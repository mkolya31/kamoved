#!/usr/bin/env bash
set -Eeuo pipefail

readonly app_dir=/opt/kamoved
readonly compose_file=compose.production.yaml
readonly env_file=.env.production

usage() {
  echo "Usage: bash deploy/revoke-sessions.sh --all | USERNAME" >&2
}

if [ "$#" -ne 1 ]; then
  usage
  exit 2
fi

cd "$app_dir"

if [ "$1" = "--all" ]; then
  readonly sql='DELETE FROM spring_session;'
  echo "Revoking all active Kamoved sessions"
  printf '%s\n' "$sql" \
    | docker compose --env-file "$env_file" -f "$compose_file" exec -T database \
      sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
  exit 0
fi

readonly username=$1
readonly sql="DELETE FROM spring_session WHERE LOWER(principal_name) = LOWER(:'username');"

echo "Revoking active Kamoved sessions for $username"
printf '%s\n' "$sql" \
  | docker compose --env-file "$env_file" -f "$compose_file" exec -T database \
    sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v username="$1"' \
    sh "$username"
