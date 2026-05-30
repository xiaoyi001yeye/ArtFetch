#!/usr/bin/env bash
set -euo pipefail

BACKUP_PREFIX="${1:?backup prefix timestamp is required, for example 2026.05.24.1-2026-05-24-223000}"
PROJECT_DIR="${PROJECT_DIR:-/opt/artfetch}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BASE_URL="${BASE_URL:-http://124.174.79.81:3000}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

for cmd in docker curl; do
  require_command "$cmd"
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required: docker compose version failed."
  exit 1
fi

cd "$PROJECT_DIR"

ENV_BACKUP="backups/env-before-${BACKUP_PREFIX}"
COMPOSE_BACKUP="backups/${COMPOSE_FILE}-before-${BACKUP_PREFIX}"
MANIFEST_BACKUP="backups/release-manifest-before-${BACKUP_PREFIX}.json"

if [ ! -f "$ENV_BACKUP" ]; then
  echo "Env backup not found: $ENV_BACKUP"
  exit 1
fi
if [ ! -f "$COMPOSE_BACKUP" ]; then
  echo "Compose backup not found: $COMPOSE_BACKUP"
  exit 1
fi

echo "Rolling back app deployment only. Database will not be restored."

cp "$ENV_BACKUP" .env
chmod 600 .env
cp "$COMPOSE_BACKUP" "$COMPOSE_FILE"
if [ -f "$MANIFEST_BACKUP" ]; then
  cp "$MANIFEST_BACKUP" release-manifest.json
fi

docker compose --env-file .env -f "$COMPOSE_FILE" pull backend frontend || true
docker compose --env-file .env -f "$COMPOSE_FILE" up -d backend frontend
docker compose --env-file .env -f "$COMPOSE_FILE" ps

BACKEND_PORT="$(docker compose --env-file .env -f "$COMPOSE_FILE" port backend 8080 | tail -n 1)"
for i in $(seq 1 60); do
  if curl -fsS "http://${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    echo "Rollback backend health check failed."
    docker compose --env-file .env -f "$COMPOSE_FILE" logs --tail=200 backend
    exit 1
  fi
done

curl -fsSI "$BASE_URL" >/dev/null

API_CODE="$(curl -sS -o /tmp/artfetch-api-check-body -w '%{http_code}' "${BASE_URL}/api/auth/me" || true)"
if [ "$API_CODE" != "401" ]; then
  echo "Rollback API auth check failed with HTTP $API_CODE; expected 401."
  cat /tmp/artfetch-api-check-body || true
  exit 1
fi

{
  printf '%s ' "$(date '+%F %T')"
  printf 'rollback_from_backup=%s ' "$BACKUP_PREFIX"
  printf 'status=auto-verified\n'
} >> backups/deploy-history.log

echo "Rollback finished. Run manual smoke test now."
