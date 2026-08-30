#!/usr/bin/env bash
set -Eeuo pipefail

readonly app_dir=/opt/kamoved
readonly env_file="$app_dir/.env.production"
readonly deploy_dir=/opt/kamoved-deploy
readonly preflight_root="$deploy_dir/worktrees"
readonly preflight_compose_file="$deploy_dir/compose.preflight.yaml"
readonly lock_file=/tmp/kamoved-deploy.lock

run_dir=
checkout_dir=
project_name=
backend_test_image=
frontend_test_image=
backend_image=
frontend_image=

cleanup() {
  local exit_code=$?
  set +e

  if [[ -n "$project_name" && -n "$checkout_dir" ]]; then
    docker compose \
      --project-name "$project_name" \
      --env-file "$env_file" \
      --file "$preflight_compose_file" \
      down --volumes --remove-orphans --rmi local
  fi

  if [[ -n "$backend_test_image" ]]; then
    docker image rm --force "$backend_test_image" >/dev/null 2>&1
  fi
  if [[ -n "$frontend_test_image" ]]; then
    docker image rm --force "$frontend_test_image" >/dev/null 2>&1
  fi
  if [[ -n "$backend_image" ]]; then
    docker image rm --force "$backend_image" >/dev/null 2>&1
  fi
  if [[ -n "$frontend_image" ]]; then
    docker image rm --force "$frontend_image" >/dev/null 2>&1
  fi

  if [[ -n "$checkout_dir" && -d "$checkout_dir" ]]; then
    git -C "$app_dir" worktree remove --force "$checkout_dir"
  fi

  case "$run_dir" in
    "$preflight_root"/run.*)
      rm -rf -- "$run_dir"
      ;;
  esac

  exit "$exit_code"
}

trap cleanup EXIT

readonly original_command="${SSH_ORIGINAL_COMMAND:-}"
if [[ ! "$original_command" =~ ^preflight\ ([1-9][0-9]*)\ ([0-9a-f]{40})\ ([0-9a-f]{40})$ ]]; then
  echo 'Only the preflight command is allowed for this SSH key.' >&2
  exit 2
fi

readonly pr_number="${BASH_REMATCH[1]}"
readonly expected_base_sha="${BASH_REMATCH[2]}"
readonly expected_head_sha="${BASH_REMATCH[3]}"

test -d "$app_dir/.git"
test -f "$env_file"
test -f "$preflight_compose_file"
install -d -m 0750 "$preflight_root"

exec 9>"$lock_file"
if ! flock --wait 1200 9; then
  echo 'Timed out waiting for another Kamoved server operation.' >&2
  exit 1
fi

readonly candidate_ref="refs/pull/$pr_number/merge"

echo "Fetching merge candidate for pull request #$pr_number"
git -C "$app_dir" fetch --no-tags --force --refmap= origin "$candidate_ref"

candidate_sha=$(git -C "$app_dir" rev-parse 'FETCH_HEAD^{commit}')
read -r actual_base_sha actual_head_sha unexpected_parent \
  <<< "$(git -C "$app_dir" show --no-patch --format='%P' "$candidate_sha")"

if [[ "$actual_base_sha" != "$expected_base_sha" \
  || "$actual_head_sha" != "$expected_head_sha" \
  || -n "${unexpected_parent:-}" ]]; then
  echo 'The fetched merge candidate does not match the pull request event.' >&2
  exit 1
fi

run_dir=$(mktemp -d "$preflight_root/run.XXXXXXXX")
checkout_dir="$run_dir/repository"
run_token=$(basename "$run_dir" | tr '[:upper:].' '[:lower:]-')
project_name="kamoved-$run_token"
backend_test_image="kamoved-preflight/backend-test:$run_token"
frontend_test_image="kamoved-preflight/frontend-test:$run_token"
backend_image="kamoved-preflight/backend:$run_token"
frontend_image="kamoved-preflight/frontend:$run_token"

export KAMOVED_PREFLIGHT_ENV_FILE="$env_file"
export KAMOVED_PREFLIGHT_BACKEND_IMAGE="$backend_image"
export KAMOVED_PREFLIGHT_FRONTEND_IMAGE="$frontend_image"

git -C "$app_dir" worktree add --detach "$checkout_dir" "$candidate_sha"
ln -s "$env_file" "$checkout_dir/.env.production"

compose=(
  docker compose
  --project-name "$project_name"
  --env-file "$env_file"
  --file "$preflight_compose_file"
)
production_build_compose=(
  docker compose
  --project-name "${project_name}-build"
  --env-file "$env_file"
  --file "$checkout_dir/compose.production.yaml"
  --file "$preflight_compose_file"
)

echo 'Validating production Compose configuration'
docker compose \
  --env-file "$env_file" \
  --file "$checkout_dir/compose.production.yaml" \
  config --quiet
"${compose[@]}" config --quiet
"${production_build_compose[@]}" config --quiet

echo 'Running backend tests'
docker build \
  --target test \
  --tag "$backend_test_image" \
  "$checkout_dir/backend"

echo 'Running frontend tests'
docker build \
  --target test \
  --tag "$frontend_test_image" \
  "$checkout_dir/frontend"

echo 'Building production backend image'
"${production_build_compose[@]}" build backend

echo 'Building production frontend image'
"${production_build_compose[@]}" build frontend

echo 'Starting isolated production candidate'
if ! "${compose[@]}" up --detach --no-build; then
  echo 'Failed to start the isolated production candidate.' >&2
  "${compose[@]}" ps >&2
  "${compose[@]}" logs --tail=100 backend frontend >&2
  exit 1
fi

echo 'Waiting for frontend and backend smoke checks'
for attempt in $(seq 1 90); do
  if "${compose[@]}" exec --no-TTY frontend \
      wget -q -O /dev/null http://127.0.0.1/ \
    && "${compose[@]}" exec --no-TTY frontend \
      wget -q -O /dev/null http://127.0.0.1/api/auth/csrf; then
    "${compose[@]}" ps
    echo "Production candidate passed: ${candidate_sha:0:12}"
    exit 0
  fi

  if [[ "$attempt" -lt 90 ]]; then
    sleep 2
  fi
done

echo 'Production candidate did not become ready.' >&2
"${compose[@]}" ps >&2
"${compose[@]}" logs --tail=100 backend frontend >&2
exit 1
