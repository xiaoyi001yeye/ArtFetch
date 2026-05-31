#!/usr/bin/env bash
set -euo pipefail

BACKUP_PREFIX="${1:?backup prefix is required, for example 2026.05.30.1-2026-05-30-223000}"
PROJECT_DIR="${PROJECT_DIR:-/opt/artfetch}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BASE_URL="${BASE_URL:-http://124.174.79.81:3000}"
SNAPSHOT_DIR="${PROJECT_DIR}/backups/deployment-before-${BACKUP_PREFIX}"

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

if [ ! -d "$SNAPSHOT_DIR" ]; then
  echo "Snapshot not found: $SNAPSHOT_DIR"
  exit 1
fi
if [ ! -f "$SNAPSHOT_DIR/.env" ] || [ ! -f "$SNAPSHOT_DIR/compose.yml" ] || [ ! -f "$SNAPSHOT_DIR/active-compose-file" ]; then
  echo "Snapshot is incomplete: $SNAPSHOT_DIR"
  exit 1
fi

PREVIOUS_COMPOSE_FILE="$(cat "$SNAPSHOT_DIR/active-compose-file")"
echo "Rolling back app deployment only. Database will not be restored."

if [ -f "$COMPOSE_FILE" ] && [ -f .env.release ]; then
  docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" stop frontend backend jupyter || true
fi

cp "$SNAPSHOT_DIR/.env" .env
chmod 600 .env
cp "$SNAPSHOT_DIR/compose.yml" "$PREVIOUS_COMPOSE_FILE"
if [ -f "$SNAPSHOT_DIR/.env.release" ]; then
  cp "$SNAPSHOT_DIR/.env.release" .env.release
  chmod 600 .env.release
else
  rm -f .env.release
fi
if [ -f "$SNAPSHOT_DIR/release-manifest.json" ]; then
  cp "$SNAPSHOT_DIR/release-manifest.json" release-manifest.json
else
  rm -f release-manifest.json
fi
printf '%s\n' "$PREVIOUS_COMPOSE_FILE" > active-compose-file

if [ -f .env.release ] && [ "$PREVIOUS_COMPOSE_FILE" = "$COMPOSE_FILE" ]; then
  COMPOSE=(docker compose --env-file .env --env-file .env.release -f "$PREVIOUS_COMPOSE_FILE")
else
  COMPOSE=(docker compose --env-file .env -f "$PREVIOUS_COMPOSE_FILE")
fi

"${COMPOSE[@]}" up -d backend frontend jupyter
"${COMPOSE[@]}" ps

BACKEND_PORT="$("${COMPOSE[@]}" port backend 8080 | tail -n 1)"
for i in $(seq 1 60); do
  if curl -fsS "http://${BACKEND_PORT}/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    echo "Rollback backend health check failed."
    "${COMPOSE[@]}" logs --tail=200 backend
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
