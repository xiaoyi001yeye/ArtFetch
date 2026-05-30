#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?version is required}"
PACKAGE="${2:?release package path is required}"
PROJECT_DIR="${PROJECT_DIR:-/opt/artfetch}"
BASE_URL="${BASE_URL:-http://124.174.79.81:3000}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
MIN_FREE_MB="${MIN_FREE_MB:-2048}"
TIMESTAMP="$(date +%F-%H%M%S)"
ROLLBACK_PREFIX="${VERSION}-${TIMESTAMP}"
SNAPSHOT_DIR="${PROJECT_DIR}/backups/deployment-before-${ROLLBACK_PREFIX}"
BACKUP_READY=0
DEPLOY_SUCCESS=0
TMP_DIR=""

on_exit() {
  local code="$?"
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
  rm -f "${PROJECT_DIR}/${COMPOSE_FILE}.candidate" "${PROJECT_DIR}/.env.release.candidate"
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ]; then
    echo "Deployment failed with exit code ${code}."
  fi
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ] && [ "$BACKUP_READY" = "1" ]; then
    echo "Rollback snapshot: ${ROLLBACK_PREFIX}"
    echo "Rollback command:"
    echo "  bash ${PROJECT_DIR}/scripts/artfetch-rollback-release.sh ${ROLLBACK_PREFIX}"
    {
      printf '%s ' "$(date '+%F %T')"
      printf 'version=%s ' "$VERSION"
      printf 'rollback_prefix=%s ' "$ROLLBACK_PREFIX"
      printf 'status=failed\n'
    } >> "${PROJECT_DIR}/backups/deploy-history.log" 2>/dev/null || true
  fi
  exit "$code"
}

trap on_exit EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

check_free_space() {
  local path="$1"
  local available
  available="$(df -Pm "$path" | awk 'NR == 2 {print $4}')"
  if [ -z "$available" ]; then
    echo "Cannot determine free disk space for $path"
    exit 1
  fi
  if [ "$available" -lt "$MIN_FREE_MB" ]; then
    echo "Not enough free space on $path: ${available}MB available, ${MIN_FREE_MB}MB required."
    exit 1
  fi
}

compose_current() {
  if [ -f .env.release ] && [ "$CURRENT_COMPOSE_FILE" = "$COMPOSE_FILE" ]; then
    docker compose --env-file .env --env-file .env.release -f "$CURRENT_COMPOSE_FILE" "$@"
  else
    docker compose --env-file .env -f "$CURRENT_COMPOSE_FILE" "$@"
  fi
}

compose_release() {
  docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" "$@"
}

query_postgres() {
  compose_current exec -T postgres sh -lc \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<< "$1"
}

for cmd in docker sha256sum tar python3 curl awk grep sed; do
  require_command "$cmd"
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required: docker compose version failed."
  exit 1
fi

cd "$PROJECT_DIR"
mkdir -p releases backups scripts backend/logs storage/original-images
chmod 700 backups
check_free_space "$PROJECT_DIR"

if [ ! -f "$PACKAGE" ]; then
  echo "Package not found: $PACKAGE"
  exit 1
fi
if [ ! -f "${PACKAGE}.sha256" ]; then
  echo "Checksum file not found: ${PACKAGE}.sha256"
  exit 1
fi
if [ ! -f .env ]; then
  echo ".env is missing. Create ${PROJECT_DIR}/.env and fill production secrets first."
  exit 1
fi
chmod 600 .env

echo "Checking required .env variables without printing values..."
for key in POSTGRES_PASSWORD ARTFETCH_ADMIN_PASSWORD ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY; do
  if ! grep -Eq "^${key}=.+" .env; then
    echo "Missing required .env key: $key"
    exit 1
  fi
  value="$(grep -E "^${key}=" .env | tail -n 1 | cut -d= -f2-)"
  if printf '%s' "$value" | grep -qi 'change-me'; then
    echo "Required .env key still looks placeholder-like: $key"
    exit 1
  fi
done

echo "Verifying package checksum..."
(cd "$(dirname "$PACKAGE")" && sha256sum -c "$(basename "${PACKAGE}.sha256")")

TMP_DIR="$(mktemp -d)"
tar -xzf "$PACKAGE" -C "$TMP_DIR"
RELEASE_DIR="$TMP_DIR/artfetch-deploy-${VERSION}"
MANIFEST="$RELEASE_DIR/release-manifest.json"
NEW_COMPOSE="$RELEASE_DIR/docker-compose.prod.yml"

if [ ! -f "$MANIFEST" ] || [ ! -f "$NEW_COMPOSE" ]; then
  echo "Release package must contain release-manifest.json and docker-compose.prod.yml"
  exit 1
fi

read_manifest() {
  python3 - "$MANIFEST" "$1" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    node = json.load(f)
for part in sys.argv[2].split("."):
    node = node[part]
print(node)
PY
}

BACKEND_IMAGE="$(read_manifest images.backend.ref)"
FRONTEND_IMAGE="$(read_manifest images.frontend.ref)"
EXPECTED_COMPOSE_SHA="$(read_manifest compose.sha256)"
GIT_SHA="$(read_manifest gitSha)"
MANIFEST_VERSION="$(read_manifest version)"
MANIFEST_APP="$(read_manifest app)"

if [ "$MANIFEST_APP" != "artfetch" ]; then
  echo "Manifest app mismatch: $MANIFEST_APP"
  exit 1
fi
if [ "$MANIFEST_VERSION" != "$VERSION" ]; then
  echo "Manifest version mismatch. CLI=$VERSION manifest=$MANIFEST_VERSION"
  exit 1
fi
if ! printf '%s\n%s\n' "$BACKEND_IMAGE" "$FRONTEND_IMAGE" | grep -Eqv '^.+@sha256:[a-f0-9]{64}$'; then
  :
else
  echo "Release images must use immutable sha256 digest references."
  exit 1
fi
ACTUAL_COMPOSE_SHA="$(sha256sum "$NEW_COMPOSE" | awk '{print $1}')"
if [ "$ACTUAL_COMPOSE_SHA" != "$EXPECTED_COMPOSE_SHA" ]; then
  echo "Compose checksum mismatch."
  exit 1
fi

echo "Target version: $VERSION"
echo "Target git sha: $GIT_SHA"
echo "Backend image: $BACKEND_IMAGE"
echo "Frontend image: $FRONTEND_IMAGE"

cp "$NEW_COMPOSE" "${COMPOSE_FILE}.candidate"
cat > .env.release.candidate <<EOF
ARTFETCH_BACKEND_IMAGE=${BACKEND_IMAGE}
ARTFETCH_FRONTEND_IMAGE=${FRONTEND_IMAGE}
EOF
chmod 600 .env.release.candidate

echo "Validating release compose config..."
docker compose --env-file .env --env-file .env.release.candidate -f "${COMPOSE_FILE}.candidate" config >/dev/null

if [ -f active-compose-file ] && [ -f "$(cat active-compose-file)" ]; then
  CURRENT_COMPOSE_FILE="$(cat active-compose-file)"
elif [ -f "$COMPOSE_FILE" ]; then
  CURRENT_COMPOSE_FILE="$COMPOSE_FILE"
elif [ -f docker-compose.yml ]; then
  CURRENT_COMPOSE_FILE="docker-compose.yml"
else
  echo "No current compose file found."
  exit 1
fi

echo "Ensuring PostgreSQL is running for pre-deploy checks..."
compose_current up -d postgres

echo "Checking running tasks before restart..."
RUNNING_SEARCH_TASKS="$(query_postgres "
select id || '|' || coalesce(name, '') || '|' || coalesce(task_type, 'SEARCH')
from search_tasks
where status = 'RUNNING'
order by id;
")"

RUNNING_HD_MIGRATIONS=""
if [ "$(query_postgres "select to_regclass('public.hd_image_migration_tasks') is not null;")" = "t" ]; then
  RUNNING_HD_MIGRATIONS="$(query_postgres "
select id || '|' || coalesce(name, '') || '|HD_IMAGE_MIGRATION'
from hd_image_migration_tasks
where status = 'RUNNING'
order by id;
")"
fi

UPLOADING_HD_ITEMS=""
if [ "$(query_postgres "select to_regclass('public.hd_image_migration_items') is not null;")" = "t" ]; then
  UPLOADING_HD_ITEMS="$(query_postgres "
select id || '|migration_task_id=' || migration_task_id || '|HD_IMAGE_UPLOAD'
from hd_image_migration_items
where status = 'UPLOADING'
order by id;
")"
fi

if [ -n "$RUNNING_SEARCH_TASKS" ] || [ -n "$RUNNING_HD_MIGRATIONS" ] || [ -n "$UPLOADING_HD_ITEMS" ]; then
  echo "There are running or uploading tasks. Abort deployment."
  [ -z "$RUNNING_SEARCH_TASKS" ] || printf '  search_task: %s\n' "$RUNNING_SEARCH_TASKS"
  [ -z "$RUNNING_HD_MIGRATIONS" ] || printf '  hd_image_migration: %s\n' "$RUNNING_HD_MIGRATIONS"
  [ -z "$UPLOADING_HD_ITEMS" ] || printf '  hd_image_upload: %s\n' "$UPLOADING_HD_ITEMS"
  exit 1
fi

echo "Pulling release images before touching current app containers..."
docker pull "$BACKEND_IMAGE"
docker pull "$FRONTEND_IMAGE"

echo "Stopping frontend and backend for the maintenance window..."
compose_current stop frontend backend || true

echo "Capturing deployment snapshot..."
mkdir -p "$SNAPSHOT_DIR"
chmod 700 "$SNAPSHOT_DIR"
printf '%s\n' "$CURRENT_COMPOSE_FILE" > "$SNAPSHOT_DIR/active-compose-file"
cp .env "$SNAPSHOT_DIR/.env"
chmod 600 "$SNAPSHOT_DIR/.env"
cp "$CURRENT_COMPOSE_FILE" "$SNAPSHOT_DIR/compose.yml"
[ ! -f .env.release ] || cp .env.release "$SNAPSHOT_DIR/.env.release"
[ ! -f release-manifest.json ] || cp release-manifest.json "$SNAPSHOT_DIR/release-manifest.json"
compose_current ps > "$SNAPSHOT_DIR/compose-ps.txt"
docker inspect artfetch-backend artfetch-frontend > "$SNAPSHOT_DIR/app-container-inspect.json" 2>/dev/null || true

echo "Backing up database..."
compose_current exec -T postgres sh -lc \
  'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$SNAPSHOT_DIR/artfetch.dump"
test -s "$SNAPSHOT_DIR/artfetch.dump"
BACKUP_READY=1

echo "Installing release files..."
mkdir -p "releases/artfetch-deploy-${VERSION}"
cp -R "$RELEASE_DIR"/. "releases/artfetch-deploy-${VERSION}/"
cp "$NEW_COMPOSE" "$COMPOSE_FILE"
cp "$MANIFEST" release-manifest.json
mv .env.release.candidate .env.release
printf '%s\n' "$COMPOSE_FILE" > active-compose-file
chmod 600 .env.release
rm -f "${COMPOSE_FILE}.candidate"

echo "Starting release backend and frontend..."
compose_release up -d backend frontend

echo "Waiting for stable containers..."
for _ in $(seq 1 60); do
  POSTGRES_STATUS="$(docker inspect artfetch-postgres --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>/dev/null || true)"
  BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  if [ "$POSTGRES_STATUS" = "running healthy" ] && [ "$BACKEND_STATUS" = "running false" ] && [ "$FRONTEND_STATUS" = "running false" ]; then
    break
  fi
  sleep 2
done

POSTGRES_STATUS="$(docker inspect artfetch-postgres --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>/dev/null || true)"
BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
if [ "$POSTGRES_STATUS" != "running healthy" ] || [ "$BACKEND_STATUS" != "running false" ] || [ "$FRONTEND_STATUS" != "running false" ]; then
  echo "Containers did not become stable."
  compose_release ps
  exit 1
fi

ACTUAL_BACKEND_IMAGE="$(docker inspect artfetch-backend --format '{{.Config.Image}}')"
ACTUAL_FRONTEND_IMAGE="$(docker inspect artfetch-frontend --format '{{.Config.Image}}')"
if [ "$ACTUAL_BACKEND_IMAGE" != "$BACKEND_IMAGE" ] || [ "$ACTUAL_FRONTEND_IMAGE" != "$FRONTEND_IMAGE" ]; then
  echo "Running image refs do not match the release manifest."
  exit 1
fi

compose_release ps
BACKEND_PORT="$(compose_release port backend 8080 | tail -n 1)"
if [ -z "$BACKEND_PORT" ]; then
  echo "Cannot resolve backend published port."
  exit 1
fi

echo "Checking backend health..."
for i in $(seq 1 60); do
  if curl -fsS "http://${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    echo "Backend health check failed."
    compose_release logs --tail=200 backend
    exit 1
  fi
done

echo "Checking frontend..."
curl -fsSI "$BASE_URL" >/dev/null

echo "Checking API auth path..."
API_CODE="$(curl -sS -o /tmp/artfetch-api-check-body -w '%{http_code}' "${BASE_URL}/api/auth/me" || true)"
if [ "$API_CODE" != "401" ]; then
  echo "API auth check failed with HTTP $API_CODE; expected 401 for unauthenticated /api/auth/me."
  cat /tmp/artfetch-api-check-body || true
  exit 1
fi

echo "Checking DESCRIPTION task type schema support..."
TASK_TYPE_CONSTRAINT="$(compose_release exec -T postgres sh -lc \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select pg_get_constraintdef(oid) from pg_constraint where conrelid = '\''search_tasks'\''::regclass and contype = '\''c'\'';"')"
if ! printf '%s' "$TASK_TYPE_CONSTRAINT" | grep -q DESCRIPTION; then
  echo "search_tasks constraint does not include DESCRIPTION."
  exit 1
fi

echo "Checking recent backend errors..."
compose_release logs --since 3m --tail=200 backend | tee "/tmp/artfetch-backend-${VERSION}.log" >/dev/null
if grep -E "ERROR|Exception|Failed to start" "/tmp/artfetch-backend-${VERSION}.log" >/dev/null; then
  echo "Backend logs contain ERROR/Exception. Review logs before marking deployment successful."
  compose_release logs --tail=200 backend
  exit 1
fi

{
  printf '%s ' "$(date '+%F %T')"
  printf 'version=%s ' "$VERSION"
  printf 'git=%s ' "$GIT_SHA"
  printf 'backend=%s ' "$BACKEND_IMAGE"
  printf 'frontend=%s ' "$FRONTEND_IMAGE"
  printf 'backup=%s ' "$SNAPSHOT_DIR/artfetch.dump"
  printf 'status=auto-verified\n'
} >> backups/deploy-history.log

DEPLOY_SUCCESS=1
echo "Deployment auto-verification passed."
