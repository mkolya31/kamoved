#!/usr/bin/env bash
set -Eeuo pipefail

readonly app_dir=/opt/kamoved
readonly compose_file=compose.production.yaml
readonly env_file=.env.production
readonly production_url=https://kamoved.ru/
readonly backend_check_url=https://kamoved.ru/api/auth/csrf

exec 9>/tmp/kamoved-deploy.lock
if ! flock -n 9; then
  echo "Another Kamoved deployment is already running" >&2
  exit 1
fi

cd "$app_dir"

test -f "$env_file"
test -f "$compose_file"

echo "Fetching production branch"
# Keep the deploy independent from stale or broken remote-tracking refs.
git fetch --no-tags --refmap= origin refs/heads/production
git checkout -B production FETCH_HEAD

echo "Validating production Compose configuration"
docker compose --env-file "$env_file" -f "$compose_file" config --quiet

echo "Building backend"
docker compose --env-file "$env_file" -f "$compose_file" build backend

echo "Building frontend"
docker compose --env-file "$env_file" -f "$compose_file" build frontend

echo "Starting Kamoved"
docker compose --env-file "$env_file" -f "$compose_file" up -d --no-build --remove-orphans

echo "Waiting for the public endpoint"
for attempt in $(seq 1 30); do
  if curl --noproxy '*' --fail --silent --show-error --output /dev/null "$production_url" \
    && curl --noproxy '*' --fail --silent --show-error --output /dev/null "$backend_check_url"; then
    docker compose --env-file "$env_file" -f "$compose_file" ps
    echo "Kamoved deployment completed: $(git rev-parse --short HEAD)"
    exit 0
  fi

  if [ "$attempt" -eq 30 ]; then
    break
  fi
  sleep 2
done

echo "Kamoved did not become available after deployment" >&2
docker compose --env-file "$env_file" -f "$compose_file" ps >&2
docker compose --env-file "$env_file" -f "$compose_file" logs --tail=100 backend frontend >&2
exit 1
