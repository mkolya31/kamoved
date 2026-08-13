#!/usr/bin/env bash
set -Eeuo pipefail

readonly app_dir=/opt/kamoved
readonly source_dir="$app_dir/deploy/backup"
readonly config_dir=/etc/kamoved

if [[ "$EUID" -ne 0 ]]; then
  echo "Run this installer as root" >&2
  exit 1
fi
if [[ ! -d "$source_dir" ]]; then
  echo "Expected Kamoved at $app_dir" >&2
  exit 1
fi

install -d -m 700 "$config_dir"
install -d -m 700 /var/lib/kamoved-backup /var/cache/kamoved-restic

if [[ ! -f "$config_dir/backup.env" ]]; then
  install -m 600 "$source_dir/backup.env.example" "$config_dir/backup.env"
  echo "Created $config_dir/backup.env; fill in the Timeweb S3 credentials and bucket name."
else
  echo "Keeping the existing $config_dir/backup.env."
fi

if [[ ! -f "$config_dir/restic-password" ]]; then
  umask 077
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 >"$config_dir/restic-password"
  else
    head -c 48 /dev/urandom | base64 >"$config_dir/restic-password"
  fi
  echo "Created $config_dir/restic-password. Save an offline copy; backups cannot be restored without it."
else
  echo "Keeping the existing $config_dir/restic-password."
fi

install -m 644 "$source_dir/systemd/kamoved-backup.service" /etc/systemd/system/kamoved-backup.service
install -m 644 "$source_dir/systemd/kamoved-backup.timer" /etc/systemd/system/kamoved-backup.timer
install -m 644 "$source_dir/systemd/kamoved-backup-verify.service" /etc/systemd/system/kamoved-backup-verify.service
install -m 644 "$source_dir/systemd/kamoved-backup-verify.timer" /etc/systemd/system/kamoved-backup-verify.timer
systemctl daemon-reload

cat <<'EOF'

Systemd units are installed but not enabled yet.

Next steps:
  1. Edit /etc/kamoved/backup.env.
  2. Save /etc/kamoved/restic-password in a secure place outside this VPS.
  3. Initialize: bash /opt/kamoved/deploy/backup/kamoved-backup.sh init
  4. Create and verify the first backup:
       systemctl start kamoved-backup.service
       systemctl start kamoved-backup-verify.service
  5. Enable schedules:
       systemctl enable --now kamoved-backup.timer kamoved-backup-verify.timer
EOF
