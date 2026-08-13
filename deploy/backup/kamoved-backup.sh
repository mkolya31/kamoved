#!/usr/bin/env bash
set -Eeuo pipefail

readonly command_name="${1:-help}"
readonly config_file="${KAMOVED_BACKUP_CONFIG:-/etc/kamoved/backup.env}"
readonly staging_dir=/var/lib/kamoved-backup
readonly staging_file="$staging_dir/kamoved-postgresql.dump"
readonly lock_file=/run/lock/kamoved-backup.lock
readonly backup_host=kamoved-production
readonly backup_tag=postgresql
active_verify_container=

log() {
  printf '%s %s\n' "$(date --iso-8601=seconds)" "$*"
}

fail() {
  log "ERROR: $*" >&2
  return 1
}

check_secret_file_permissions() {
  local file="$1"
  local mode
  local owner_id

  mode="$(stat --format=%a "$file")"
  owner_id="$(stat --format=%u "$file")"
  if [[ "$owner_id" != 0 ]] || (( (8#$mode & 8#077) != 0 )); then
    fail "Secret file must be owned by root and inaccessible to group/others: $file"
    return 1
  fi
}

load_config() {
  if [[ ! -f "$config_file" ]]; then
    fail "Backup configuration not found: $config_file"
    return 1
  fi
  check_secret_file_permissions "$config_file"

  set -a
  # This root-owned file uses systemd EnvironmentFile-compatible KEY=value syntax.
  # shellcheck source=/dev/null
  source "$config_file"
  set +a

  : "${AWS_ACCESS_KEY_ID:?Set AWS_ACCESS_KEY_ID in $config_file}"
  : "${AWS_SECRET_ACCESS_KEY:?Set AWS_SECRET_ACCESS_KEY in $config_file}"
  : "${RESTIC_REPOSITORY:?Set RESTIC_REPOSITORY in $config_file}"
  : "${RESTIC_PASSWORD_FILE:?Set RESTIC_PASSWORD_FILE in $config_file}"

  if [[ "$RESTIC_REPOSITORY" != s3:https://s3.twcstorage.ru/* ]]; then
    fail "RESTIC_REPOSITORY must use the encrypted Timeweb S3 endpoint"
    return 1
  fi

  export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ru-1}"
  export RESTIC_CACHE_DIR="${RESTIC_CACHE_DIR:-/var/cache/kamoved-restic}"

  KAMOVED_COMPOSE_DIR="${KAMOVED_COMPOSE_DIR:-/opt/kamoved}"
  KAMOVED_COMPOSE_FILE="${KAMOVED_COMPOSE_FILE:-compose.production.yaml}"
  KAMOVED_ENV_FILE="${KAMOVED_ENV_FILE:-.env.production}"
  BACKUP_KEEP_HOURLY="${BACKUP_KEEP_HOURLY:-48}"
  BACKUP_KEEP_DAILY="${BACKUP_KEEP_DAILY:-30}"
  BACKUP_KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-13}"

  if [[ ! -s "$RESTIC_PASSWORD_FILE" ]]; then
    fail "Restic password file not found or empty: $RESTIC_PASSWORD_FILE"
    return 1
  fi
  check_secret_file_permissions "$RESTIC_PASSWORD_FILE"

  if [[ ! "$BACKUP_KEEP_HOURLY" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$BACKUP_KEEP_DAILY" =~ ^[1-9][0-9]*$ ]] ||
     [[ ! "$BACKUP_KEEP_WEEKLY" =~ ^[1-9][0-9]*$ ]]; then
    fail "Backup retention values must be positive integers"
    return 1
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Required command is not installed: $1"
    return 1
  fi
}

check_runtime() {
  if [[ "$EUID" -ne 0 ]]; then
    fail "Run this command as root"
    return 1
  fi

  require_command restic
  require_command flock
  mkdir -p "$RESTIC_CACHE_DIR"
  chmod 700 "$RESTIC_CACHE_DIR"
}

check_compose_runtime() {
  require_command docker

  if [[ ! -d "$KAMOVED_COMPOSE_DIR" ]]; then
    fail "Kamoved directory not found: $KAMOVED_COMPOSE_DIR"
    return 1
  fi
  if [[ ! -f "$KAMOVED_COMPOSE_DIR/$KAMOVED_COMPOSE_FILE" ]]; then
    fail "Compose file not found: $KAMOVED_COMPOSE_DIR/$KAMOVED_COMPOSE_FILE"
    return 1
  fi
  if [[ ! -f "$KAMOVED_COMPOSE_DIR/$KAMOVED_ENV_FILE" ]]; then
    fail "Production environment file not found: $KAMOVED_COMPOSE_DIR/$KAMOVED_ENV_FILE"
    return 1
  fi
}

acquire_lock() {
  exec 9>"$lock_file"
  if ! flock -n 9; then
    fail "Another Kamoved backup operation is already running"
    return 1
  fi
}

cleanup_staging_file() {
  if [[ -f "$staging_file" ]]; then
    rm -f -- "$staging_file"
  fi
}

cleanup_verify_container() {
  if [[ -n "$active_verify_container" ]]; then
    docker rm -f "$active_verify_container" >/dev/null 2>&1 || true
    active_verify_container=
  fi
}

run_backup() {
  local dump_size
  local -a compose

  check_compose_runtime
  acquire_lock
  mkdir -p "$staging_dir"
  chmod 700 "$staging_dir"
  cleanup_staging_file

  compose=(
    docker compose
    --env-file "$KAMOVED_ENV_FILE"
    -f "$KAMOVED_COMPOSE_FILE"
  )

  cd "$KAMOVED_COMPOSE_DIR"
  "${compose[@]}" config --quiet
  restic cat config >/dev/null

  log "Checking PostgreSQL availability"
  "${compose[@]}" exec -T database sh -ec \
    'exec pg_isready --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'

  log "Creating a consistent PostgreSQL dump"
  "${compose[@]}" exec -T database sh -ec \
    'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
    >"$staging_file"

  if [[ ! -s "$staging_file" ]]; then
    fail "PostgreSQL produced an empty dump"
    return 1
  fi

  log "Validating the PostgreSQL archive"
  "${compose[@]}" exec -T database pg_restore --list <"$staging_file" >/dev/null
  dump_size="$(du -h "$staging_file" | cut -f1)"

  log "Encrypting and uploading the dump to Timeweb S3 (local size: $dump_size)"
  restic backup \
    --host "$backup_host" \
    --tag "$backup_tag" \
    "$staging_file"

  # The S3 copy is complete and encrypted; do not retain the plaintext dump locally.
  cleanup_staging_file

  log "Applying retention policy"
  restic forget \
    --host "$backup_host" \
    --tag "$backup_tag" \
    --keep-hourly "$BACKUP_KEEP_HOURLY" \
    --keep-daily "$BACKUP_KEEP_DAILY" \
    --keep-weekly "$BACKUP_KEEP_WEEKLY" \
    --prune

  log "Backup completed successfully"
}

verify_restore() {
  local verify_image="${POSTGRES_VERIFY_IMAGE:-postgres:17-alpine}"
  local public_table_count

  check_compose_runtime
  acquire_lock
  active_verify_container="kamoved-backup-verify-$$"

  log "Checking every encrypted object in the restic repository"
  restic check --read-data

  log "Starting an isolated PostgreSQL container for restore verification"
  docker run --detach --rm \
    --name "$active_verify_container" \
    --network none \
    --tmpfs /var/lib/postgresql/data:rw,nosuid,nodev,size=512m \
    --env POSTGRES_HOST_AUTH_METHOD=trust \
    --env POSTGRES_DB=kamoved_verify \
    "$verify_image" >/dev/null

  for _ in $(seq 1 60); do
    if docker exec "$active_verify_container" pg_isready --username=postgres --dbname=kamoved_verify >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  if ! docker exec "$active_verify_container" pg_isready --username=postgres --dbname=kamoved_verify >/dev/null 2>&1; then
    fail "Verification PostgreSQL container did not become ready"
    return 1
  fi

  log "Restoring the latest backup into the isolated PostgreSQL container"
  restic dump \
    --host "$backup_host" \
    --path "$staging_file" \
    latest "$staging_file" |
    docker exec -i "$active_verify_container" pg_restore \
      --exit-on-error \
      --single-transaction \
      --no-owner \
      --no-privileges \
      --username=postgres \
      --dbname=kamoved_verify

  public_table_count="$(
    docker exec "$active_verify_container" psql \
      --username=postgres \
      --dbname=kamoved_verify \
      --tuples-only \
      --no-align \
      --command="SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname = 'public';"
  )"
  if [[ ! "$public_table_count" =~ ^[1-9][0-9]*$ ]]; then
    fail "Restore completed but no application tables were found"
    return 1
  fi

  log "Restore verification completed successfully ($public_table_count public tables)"
}

show_usage() {
  cat <<'EOF'
Usage: kamoved-backup.sh COMMAND

Commands:
  init       Initialize the encrypted restic repository in Timeweb S3
  backup     Create, validate, upload, and rotate a PostgreSQL backup
  snapshots  List available PostgreSQL backup snapshots
  check      Check repository metadata
  verify     Download and fully restore the latest backup in an isolated container
EOF
}

case "$command_name" in
  help|-h|--help)
    show_usage
    ;;
  init)
    load_config
    check_runtime
    acquire_lock
    restic init
    restic check
    ;;
  backup)
    trap 'cleanup_staging_file' EXIT
    load_config
    check_runtime
    run_backup
    ;;
  snapshots)
    load_config
    check_runtime
    restic snapshots --host "$backup_host" --tag "$backup_tag"
    ;;
  check)
    load_config
    check_runtime
    acquire_lock
    restic check
    ;;
  verify)
    trap 'cleanup_verify_container' EXIT
    load_config
    check_runtime
    verify_restore
    ;;
  *)
    show_usage >&2
    exit 2
    ;;
esac
