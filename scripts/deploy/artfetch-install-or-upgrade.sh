#!/usr/bin/env bash
set -Eeuo pipefail

VERSION="${1:?version is required}"
PACKAGE="${2:?release package path is required}"
PROJECT_DIR="${ARTFETCH_PROJECT_DIR:-${PROJECT_DIR:-/opt/artfetch}}"
BASE_URL="${BASE_URL:-http://127.0.0.1:3000}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
MIN_FREE_MB="${MIN_FREE_MB:-2048}"
TMP_DIR=""
DEPLOY_SUCCESS=0

log() {
  printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 36 | tr -d '\n'
  else
    python3 - <<'PY'
import secrets
import string
alphabet = string.ascii_letters + string.digits + "_@%+=:,.-"
print("".join(secrets.choice(alphabet) for _ in range(48)), end="")
PY
  fi
}

replace_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  if grep -Eq "^${key}=" "$file"; then
    sed -i "s#^${key}=.*#${key}=${value}#" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

cleanup_on_failure() {
  local code="$?"
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ]; then
    log "Install or upgrade failed; cleaning candidate files and ArtFetch containers."
    if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
      rm -rf "$TMP_DIR"
    fi
    rm -f "${PROJECT_DIR}/${COMPOSE_FILE}.candidate" "${PROJECT_DIR}/.env.release.candidate"
    ARTFETCH_PROJECT_DIR="$PROJECT_DIR" bash "${PROJECT_DIR}/scripts/artfetch-clean-failed-install.sh" || true
  fi
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
  exit "$code"
}

trap cleanup_on_failure EXIT

for cmd in docker sha256sum tar python3 curl awk grep sed; do
  require_command "$cmd"
done
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required: docker compose version failed."
  exit 1
fi

mkdir -p "$PROJECT_DIR"
cd "$PROJECT_DIR"
mkdir -p releases backups scripts backend/logs storage/original-images
chmod 700 backups

available="$(df -Pm "$PROJECT_DIR" | awk 'NR == 2 {print $4}')"
if [ -z "$available" ] || [ "$available" -lt "$MIN_FREE_MB" ]; then
  echo "Not enough free space on $PROJECT_DIR: ${available:-unknown}MB available, ${MIN_FREE_MB}MB required."
  exit 1
fi

if [ ! -f "$PACKAGE" ] || [ ! -f "${PACKAGE}.sha256" ]; then
  echo "Package and checksum file are required: $PACKAGE"
  exit 1
fi

log "Verifying package checksum..."
(cd "$(dirname "$PACKAGE")" && sha256sum -c "$(basename "${PACKAGE}.sha256")")

TMP_DIR="$(mktemp -d)"
tar -xzf "$PACKAGE" -C "$TMP_DIR"
RELEASE_DIR="$TMP_DIR/artfetch-deploy-${VERSION}"
MANIFEST="$RELEASE_DIR/release-manifest.json"
NEW_COMPOSE="$RELEASE_DIR/docker-compose.prod.yml"
ENV_EXAMPLE="$RELEASE_DIR/.env.example"

if [ ! -f "$MANIFEST" ] || [ ! -f "$NEW_COMPOSE" ] || [ ! -f "$ENV_EXAMPLE" ]; then
  echo "Release package must contain release-manifest.json, docker-compose.prod.yml, and .env.example"
  exit 1
fi

python3 - "$MANIFEST" "$VERSION" "$NEW_COMPOSE" "$RELEASE_DIR" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1])
version = sys.argv[2]
compose_path = Path(sys.argv[3])
release_dir = Path(sys.argv[4])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("app") != "artfetch":
    raise SystemExit("Manifest app must be artfetch")
if manifest.get("version") != version:
    raise SystemExit(f"Manifest version mismatch: {manifest.get('version')} != {version}")
if manifest.get("flywayVersion") != version:
    raise SystemExit("Manifest flywayVersion must equal release version")
compose_sha = hashlib.sha256(compose_path.read_bytes()).hexdigest()
if compose_sha != manifest["compose"]["sha256"]:
    raise SystemExit("Compose checksum mismatch")
for service, image in manifest["images"].items():
    tar = release_dir / image["tar"]
    if not tar.is_file():
        raise SystemExit(f"Missing image tar for {service}: {tar}")
    actual = hashlib.sha256(tar.read_bytes()).hexdigest()
    if actual != image["tarSha256"]:
        raise SystemExit(f"Image tar checksum mismatch for {service}")
PY

cp "$RELEASE_DIR/scripts/"*.sh scripts/
chmod +x scripts/*.sh

if [ ! -f .env ]; then
  log "Creating .env with generated local secrets."
  cp "$ENV_EXAMPLE" .env
  replace_env_value .env POSTGRES_PASSWORD "$(random_secret)"
  replace_env_value .env ARTFETCH_ADMIN_PASSWORD "$(random_secret)"
  replace_env_value .env ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY "$(random_secret)"
  chmod 600 .env
else
  chmod 600 .env
fi

log "Checking required .env variables without printing values..."
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

python3 - "$MANIFEST" > .env.release.candidate <<'PY'
import json
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
images = manifest["images"]
print(f"POSTGRES_IMAGE={images['postgres']['ref']}")
print(f"ARTFETCH_BACKEND_IMAGE={images['backend']['versionTag']}")
print(f"ARTFETCH_FRONTEND_IMAGE={images['frontend']['versionTag']}")
print(f"ARTFETCH_JUPYTER_IMAGE={images['jupyter']['versionTag']}")
PY
chmod 600 .env.release.candidate
cp "$NEW_COMPOSE" "${COMPOSE_FILE}.candidate"

log "Validating release compose config..."
docker compose --env-file .env --env-file .env.release.candidate -f "${COMPOSE_FILE}.candidate" config >/dev/null

if [ -f "$COMPOSE_FILE" ] && [ -f .env.release ]; then
  log "Checking running tasks before upgrade..."
  docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" up -d postgres
  query_postgres() {
    docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" exec -T postgres sh -lc \
      'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<< "$1"
  }
  if [ "$(query_postgres "select to_regclass('public.search_tasks') is not null;")" = "t" ]; then
    running_search_tasks="$(query_postgres "select id || '|' || coalesce(name, '') || '|' || coalesce(task_type, 'SEARCH') from search_tasks where status = 'RUNNING' order by id;")"
  else
    running_search_tasks=""
  fi
  if [ "$(query_postgres "select to_regclass('public.hd_image_migration_tasks') is not null;")" = "t" ]; then
    running_hd_migrations="$(query_postgres "select id || '|' || coalesce(name, '') || '|HD_IMAGE_MIGRATION' from hd_image_migration_tasks where status = 'RUNNING' order by id;")"
  else
    running_hd_migrations=""
  fi
  if [ "$(query_postgres "select to_regclass('public.hd_image_migration_items') is not null;")" = "t" ]; then
    uploading_hd_items="$(query_postgres "select id || '|migration_task_id=' || migration_task_id || '|HD_IMAGE_UPLOAD' from hd_image_migration_items where status = 'UPLOADING' order by id;")"
  else
    uploading_hd_items=""
  fi
  if [ -n "$running_search_tasks" ] || [ -n "$running_hd_migrations" ] || [ -n "$uploading_hd_items" ]; then
    echo "There are running or uploading tasks. Abort deployment."
    [ -z "$running_search_tasks" ] || printf '  search_task: %s\n' "$running_search_tasks"
    [ -z "$running_hd_migrations" ] || printf '  hd_image_migration: %s\n' "$running_hd_migrations"
    [ -z "$uploading_hd_items" ] || printf '  hd_image_upload: %s\n' "$uploading_hd_items"
    exit 1
  fi

  backup_dir="backups/deployment-before-${VERSION}-$(date +%F-%H%M%S)"
  mkdir -p "$backup_dir"
  chmod 700 "$backup_dir"
  cp .env "$backup_dir/.env"
  cp .env.release "$backup_dir/.env.release"
  cp "$COMPOSE_FILE" "$backup_dir/$COMPOSE_FILE"
  [ ! -f release-manifest.json ] || cp release-manifest.json "$backup_dir/release-manifest.json"
  log "Backing up database to ${backup_dir}/artfetch.dump..."
  docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" exec -T postgres sh -lc \
    'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup_dir/artfetch.dump"
  test -s "$backup_dir/artfetch.dump"
fi

log "Loading release images..."
python3 - "$MANIFEST" "$RELEASE_DIR" <<'PY' | while IFS= read -r tar_path; do
import json
import sys
from pathlib import Path
manifest = json.load(open(sys.argv[1], encoding="utf-8"))
release_dir = Path(sys.argv[2])
for image in manifest["images"].values():
    print(release_dir / image["tar"])
PY
  docker load -i "$tar_path"
done

log "Inspecting loaded images..."
python3 - "$MANIFEST" <<'PY' | while IFS= read -r image_ref; do
import json
import sys
manifest = json.load(open(sys.argv[1], encoding="utf-8"))
images = manifest["images"]
print(images["postgres"]["ref"])
for service in ("backend", "frontend", "jupyter"):
    print(images[service]["versionTag"])
    print(images[service]["shaTag"])
PY
  docker image inspect "$image_ref" >/dev/null
done

log "Installing release files..."
rm -rf "releases/artfetch-deploy-${VERSION}"
mkdir -p "releases/artfetch-deploy-${VERSION}"
cp -R "$RELEASE_DIR"/. "releases/artfetch-deploy-${VERSION}/"
cp "$NEW_COMPOSE" "$COMPOSE_FILE"
cp "$MANIFEST" release-manifest.json
mv .env.release.candidate .env.release
chmod 600 .env.release
rm -f "${COMPOSE_FILE}.candidate"
printf '%s\n' "$COMPOSE_FILE" > active-compose-file

log "Starting ArtFetch release ${VERSION}..."
docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" up -d

log "Waiting for containers to become stable..."
for _ in $(seq 1 60); do
  postgres_status="$(docker inspect artfetch-postgres --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>/dev/null || true)"
  backend_status="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  frontend_status="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  jupyter_status="$(docker inspect artfetch-jupyter --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  if [ "$postgres_status" = "running healthy" ] && [ "$backend_status" = "running false" ] && [ "$frontend_status" = "running false" ] && [ "$jupyter_status" = "running false" ]; then
    break
  fi
  sleep 2
done

docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" ps

backend_port="$(docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" port backend 8080 | tail -n 1)"
if [ -z "$backend_port" ]; then
  echo "Cannot resolve backend published port."
  exit 1
fi

log "Checking backend health at http://${backend_port}/actuator/health"
for i in $(seq 1 60); do
  if curl -fsS "http://${backend_port}/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
  if [ "$i" = "60" ]; then
    echo "Backend health check failed."
    docker compose --env-file .env --env-file .env.release -f "$COMPOSE_FILE" logs --tail=200 backend
    exit 1
  fi
done

log "Checking frontend at $BASE_URL"
curl -fsSI --connect-timeout 5 --max-time 20 "$BASE_URL" >/dev/null

log "Checking API auth path at ${BASE_URL}/api/auth/me"
api_code="$(curl -sS --connect-timeout 5 --max-time 20 -o /tmp/artfetch-api-check-body -w '%{http_code}' "${BASE_URL}/api/auth/me" || true)"
if [ "$api_code" != "401" ]; then
  echo "API auth check failed with HTTP $api_code; expected 401 for unauthenticated /api/auth/me."
  cat /tmp/artfetch-api-check-body || true
  exit 1
fi

{
  printf '%s ' "$(date '+%F %T')"
  printf 'version=%s ' "$VERSION"
  python3 - "$MANIFEST" <<'PY'
import json
import sys
manifest = json.load(open(sys.argv[1], encoding="utf-8"))
print("git=%s backend=%s frontend=%s jupyter=%s status=auto-verified" % (
    manifest["gitSha"],
    manifest["images"]["backend"]["versionTag"],
    manifest["images"]["frontend"]["versionTag"],
    manifest["images"]["jupyter"]["versionTag"],
))
PY
} >> backups/deploy-history.log

DEPLOY_SUCCESS=1
log "Deployment auto-verification passed."
