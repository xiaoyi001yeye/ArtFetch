#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?version is required}"
PACKAGE="${2:?release package path is required}"
PROJECT_DIR="${PROJECT_DIR:-/opt/artfetch}"
BASE_URL="${BASE_URL:-http://124.174.79.81:3000}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ALLOW_RUNNING_TASKS="${ALLOW_RUNNING_TASKS:-0}"
MIN_FREE_MB="${MIN_FREE_MB:-2048}"
TIMESTAMP="$(date +%F-%H%M%S)"
ROLLBACK_PREFIX="${VERSION}-${TIMESTAMP}"
BACKUP_READY=0
DEPLOY_SUCCESS=0
TMP_DIR=""

on_exit() {
  local code="$?"
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ]; then
    echo "Deployment failed with exit code ${code}."
    if [ -d "$PROJECT_DIR" ]; then
      rm -f "${PROJECT_DIR}/${COMPOSE_FILE}.candidate" "${PROJECT_DIR}/.env.candidate"
    fi
  fi
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ] && [ "$BACKUP_READY" = "1" ]; then
    echo "Rollback backup prefix: ${ROLLBACK_PREFIX}"
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

for cmd in docker sha256sum tar python3 curl awk; do
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

echo "Verifying package checksum..."
(cd "$(dirname "$PACKAGE")" && sha256sum -c "$(basename "${PACKAGE}.sha256")")

TMP_DIR="$(mktemp -d)"
tar -xzf "$PACKAGE" -C "$TMP_DIR"
RELEASE_DIR="$TMP_DIR/artfetch-deploy-${VERSION}"

if [ ! -d "$RELEASE_DIR" ]; then
  echo "Release directory not found in package: artfetch-deploy-${VERSION}"
  exit 1
fi

MANIFEST="$RELEASE_DIR/release-manifest.json"
NEW_COMPOSE="$RELEASE_DIR/docker-compose.prod.yml"

if [ ! -f "$MANIFEST" ] || [ ! -f "$NEW_COMPOSE" ]; then
  echo "Release package must contain release-manifest.json and docker-compose.prod.yml"
  exit 1
fi

echo "Verifying compose checksum from manifest..."
EXPECTED_COMPOSE_SHA="$(python3 - "$MANIFEST" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    print(json.load(f)["compose"]["sha256"])
PY
)"
ACTUAL_COMPOSE_SHA="$(sha256sum "$NEW_COMPOSE" | awk '{print $1}')"
if [ "$EXPECTED_COMPOSE_SHA" != "$ACTUAL_COMPOSE_SHA" ]; then
  echo "Compose checksum mismatch."
  echo "Expected: $EXPECTED_COMPOSE_SHA"
  echo "Actual:   $ACTUAL_COMPOSE_SHA"
  exit 1
fi

read_manifest() {
  python3 - "$MANIFEST" "$1" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    data = json.load(f)
node = data
for part in sys.argv[2].split("."):
    node = node[part]
print(node)
PY
}

BACKEND_IMAGE="$(read_manifest images.backend.ref)"
FRONTEND_IMAGE="$(read_manifest images.frontend.ref)"
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

echo "Target version: $VERSION"
echo "Target git sha: $GIT_SHA"
echo "Backend image: $BACKEND_IMAGE"
echo "Frontend image: $FRONTEND_IMAGE"

if [ ! -f .env ]; then
  echo ".env is missing. Create /opt/artfetch/.env from .env.example and fill production secrets first."
  exit 1
fi

chmod 600 .env

echo "Checking required .env variables without printing values..."
for key in POSTGRES_PASSWORD ARTFETCH_ADMIN_PASSWORD ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY; do
  if ! grep -Eq "^${key}=" .env; then
    echo "Missing required .env key: $key"
    exit 1
  fi
  value="$(grep -E "^${key}=" .env | tail -n 1 | cut -d= -f2-)"
  if [ -z "$value" ] || printf '%s' "$value" | grep -qi 'change-me'; then
    echo "Required .env key still looks empty or placeholder-like: $key"
    exit 1
  fi
done

echo "Installing candidate compose for validation..."
cp "$NEW_COMPOSE" "${COMPOSE_FILE}.candidate"

echo "Updating candidate image refs in a temporary env file..."
cp .env ".env.candidate"
chmod 600 ".env.candidate"
python3 - ".env.candidate" "$BACKEND_IMAGE" "$FRONTEND_IMAGE" <<'PY'
import sys
from pathlib import Path

env_path = Path(sys.argv[1])
updates = {
    "ARTFETCH_BACKEND_IMAGE": sys.argv[2],
    "ARTFETCH_FRONTEND_IMAGE": sys.argv[3],
}

lines = env_path.read_text(encoding="utf-8").splitlines()
seen = set()
out = []
for line in lines:
    if not line or line.lstrip().startswith("#") or "=" not in line:
        out.append(line)
        continue
    key = line.split("=", 1)[0]
    if key in updates:
        out.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        out.append(line)

for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")

env_path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY

echo "Validating compose config..."
docker compose --env-file .env.candidate -f "${COMPOSE_FILE}.candidate" config >/dev/null

if [ -f "$COMPOSE_FILE" ]; then
  CURRENT_COMPOSE="$COMPOSE_FILE"
else
  CURRENT_COMPOSE="${COMPOSE_FILE}.candidate"
fi

echo "Checking running tasks before restart..."
RUNNING_TASK_COUNTS="$(docker compose --env-file .env -f "$CURRENT_COMPOSE" exec -T postgres sh -lc '
psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At <<SQL
select count(*) from search_tasks where status = '\''RUNNING'\'';
select case
  when to_regclass('\''public.hd_image_migration_tasks'\'') is null then 0
  else (select count(*) from hd_image_migration_tasks where status = '\''RUNNING'\'')
end;
select case
  when to_regclass('\''public.hd_image_migration_items'\'') is null then 0
  else (select count(*) from hd_image_migration_items where status in ('\''UPLOADING'\''))
end;
SQL
')"

RUNNING_SEARCH_TASKS="$(printf '%s\n' "$RUNNING_TASK_COUNTS" | sed -n '1p')"
RUNNING_HD_MIGRATIONS="$(printf '%s\n' "$RUNNING_TASK_COUNTS" | sed -n '2p')"
UPLOADING_HD_ITEMS="$(printf '%s\n' "$RUNNING_TASK_COUNTS" | sed -n '3p')"
RUNNING_TOTAL=$((RUNNING_SEARCH_TASKS + RUNNING_HD_MIGRATIONS + UPLOADING_HD_ITEMS))

if [ "$RUNNING_TOTAL" != "0" ] && [ "$ALLOW_RUNNING_TASKS" != "1" ]; then
  echo "There are running or uploading tasks. Abort deployment."
  echo "  search_tasks RUNNING: ${RUNNING_SEARCH_TASKS}"
  echo "  hd_image_migration_tasks RUNNING: ${RUNNING_HD_MIGRATIONS}"
  echo "  hd_image_migration_items UPLOADING: ${UPLOADING_HD_ITEMS}"
  echo "Set ALLOW_RUNNING_TASKS=1 only during a planned maintenance window."
  exit 1
fi

echo "Pulling candidate images before touching current deployment..."
docker pull "$BACKEND_IMAGE"
docker pull "$FRONTEND_IMAGE"

echo "Entering maintenance window: stopping frontend and backend before backup..."
docker compose --env-file .env -f "$CURRENT_COMPOSE" stop frontend backend

BACKUP_DUMP="backups/artfetch-before-${ROLLBACK_PREFIX}.dump"

echo "Backing up database..."
docker compose --env-file .env -f "$CURRENT_COMPOSE" exec -T postgres sh -lc \
  'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$BACKUP_DUMP"

test -s "$BACKUP_DUMP"

echo "Backing up current deployment files..."
cp .env "backups/env-before-${ROLLBACK_PREFIX}"
chmod 600 "backups/env-before-${ROLLBACK_PREFIX}"

if [ -f "$COMPOSE_FILE" ]; then
  cp "$COMPOSE_FILE" "backups/${COMPOSE_FILE}-before-${ROLLBACK_PREFIX}"
fi
if [ -f release-manifest.json ]; then
  cp release-manifest.json "backups/release-manifest-before-${ROLLBACK_PREFIX}.json"
fi
BACKUP_READY=1

echo "Installing release files..."
mkdir -p "releases/artfetch-deploy-${VERSION}"
cp -R "$RELEASE_DIR"/. "releases/artfetch-deploy-${VERSION}/"
cp "$NEW_COMPOSE" "$COMPOSE_FILE"
cp "$MANIFEST" release-manifest.json
mv ".env.candidate" .env
rm -f "${COMPOSE_FILE}.candidate"

echo "Starting services..."
docker compose --env-file .env -f "$COMPOSE_FILE" up -d postgres backend frontend

echo "Waiting for containers..."
for _ in $(seq 1 60); do
  BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  if [ "$BACKEND_STATUS" = "running false" ] && [ "$FRONTEND_STATUS" = "running false" ]; then
    break
  fi
  sleep 2
done

BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
if [ "$BACKEND_STATUS" != "running false" ] || [ "$FRONTEND_STATUS" != "running false" ]; then
  echo "Containers did not become stable."
  docker compose --env-file .env -f "$COMPOSE_FILE" ps
  exit 1
fi

docker compose --env-file .env -f "$COMPOSE_FILE" ps

BACKEND_PORT="$(docker compose --env-file .env -f "$COMPOSE_FILE" port backend 8080 | tail -n 1)"
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
    docker compose --env-file .env -f "$COMPOSE_FILE" logs --tail=200 backend
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
  docker compose --env-file .env -f "$COMPOSE_FILE" logs --tail=120 frontend
  exit 1
fi

echo "Checking recent backend errors..."
docker compose --env-file .env -f "$COMPOSE_FILE" logs --since 3m --tail=200 backend | tee "/tmp/artfetch-backend-${VERSION}.log" >/dev/null
if grep -E "ERROR|Exception|Failed to start" "/tmp/artfetch-backend-${VERSION}.log" >/dev/null; then
  echo "Backend logs contain ERROR/Exception. Review logs before marking deployment successful."
  docker compose --env-file .env -f "$COMPOSE_FILE" logs --tail=200 backend
  exit 1
fi

{
  printf '%s ' "$(date '+%F %T')"
  printf 'version=%s ' "$VERSION"
  printf 'git=%s ' "$GIT_SHA"
  printf 'backend=%s ' "$BACKEND_IMAGE"
  printf 'frontend=%s ' "$FRONTEND_IMAGE"
  printf 'backup=%s ' "$BACKUP_DUMP"
  printf 'status=auto-verified\n'
} >> backups/deploy-history.log

DEPLOY_SUCCESS=1
echo "Deployment auto-verification passed."
echo "Manual smoke test still required:"
echo "  1. Open ${BASE_URL}/login"
echo "  2. Login as admin"
echo "  3. Load task list"
echo "  4. Load artworks"
echo "  5. Download one Excel export"
