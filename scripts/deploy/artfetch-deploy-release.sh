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

log() {
  printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*"
}

begin_group() {
  printf '::group::%s\n' "$*"
}

end_group() {
  printf '::endgroup::\n'
}

print_env_presence() {
  local file="$1"
  shift
  if [ ! -f "$file" ]; then
    echo "$file: missing"
    return
  fi
  local mode
  mode="$(stat -c '%a' "$file" 2>/dev/null || echo unknown)"
  echo "$file: present mode=$mode"
  local key
  for key in "$@"; do
    if grep -Eq "^${key}=.+" "$file"; then
      echo "env $key: set"
    else
      echo "env $key: missing_or_empty"
    fi
  done
}

compose_for_file() {
  local file="$1"
  shift
  if [ -f .env.release ] && [ "$file" = "$COMPOSE_FILE" ]; then
    docker compose --env-file .env --env-file .env.release -f "$file" "$@"
  else
    docker compose --env-file .env -f "$file" "$@"
  fi
}

diagnose_failure() {
  local code="$1"
  set +e
  begin_group "ArtFetch deployment failure diagnostics"
  log "Deployment failed with exit code ${code}"
  log "Version=${VERSION}"
  log "Project dir=${PROJECT_DIR}"
  log "Base URL=${BASE_URL}"

  if [ -d "$PROJECT_DIR" ]; then
    cd "$PROJECT_DIR" || true
    echo "pwd=$(pwd)"
    echo "directory listing:"
    ls -la | sed -n '1,120p'
    echo
    echo "disk:"
    df -h "$PROJECT_DIR" || true
  else
    echo "Project directory missing: $PROJECT_DIR"
  fi

  echo
  echo "required environment variable presence:"
  print_env_presence .env \
    POSTGRES_DB \
    POSTGRES_USER \
    POSTGRES_PASSWORD \
    ARTFETCH_ADMIN_PASSWORD \
    ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY \
    FRONTEND_PORT \
    BACKEND_PORT \
    BACKEND_BIND_HOST \
    POSTGRES_BIND_HOST
  print_env_presence .env.release ARTFETCH_BACKEND_IMAGE ARTFETCH_FRONTEND_IMAGE ARTFETCH_JUPYTER_IMAGE

  echo
  echo "release metadata:"
  if [ -f release-manifest.json ]; then
    python3 - <<'PY' || true
import json
from pathlib import Path

manifest = json.loads(Path("release-manifest.json").read_text(encoding="utf-8"))
print("version=" + str(manifest.get("version")))
print("gitSha=" + str(manifest.get("gitSha")))
for service in ("backend", "frontend", "jupyter"):
    print(service + "=" + manifest["images"][service]["ref"])
PY
  else
    echo "release-manifest.json: missing"
  fi
  [ ! -f active-compose-file ] || echo "active-compose-file=$(cat active-compose-file)"

  echo
  echo "docker versions:"
  docker --version || true
  docker compose version || true

  echo
  echo "compose ps:"
  if [ -n "${CURRENT_COMPOSE_FILE:-}" ] && [ -f "${CURRENT_COMPOSE_FILE:-}" ]; then
    echo "current compose file: $CURRENT_COMPOSE_FILE"
    compose_for_file "$CURRENT_COMPOSE_FILE" ps || true
  fi
  if [ -f "$COMPOSE_FILE" ]; then
    echo "release compose file: $COMPOSE_FILE"
    compose_for_file "$COMPOSE_FILE" ps || true
  fi
  if [ -f docker-compose.yml ] && [ "${CURRENT_COMPOSE_FILE:-}" != "docker-compose.yml" ]; then
    echo "default compose file: docker-compose.yml"
    compose_for_file docker-compose.yml ps || true
  fi

  echo
  echo "artfetch containers:"
  docker ps -a --filter 'name=artfetch' \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}\t{{.Ports}}' || true
  for container in artfetch-postgres artfetch-backend artfetch-frontend artfetch-jupyter; do
    echo
    echo "inspect ${container}:"
    docker inspect "$container" \
      --format 'name={{.Name}} status={{.State.Status}} restarting={{.State.Restarting}} exitCode={{.State.ExitCode}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} image={{.Config.Image}} started={{.State.StartedAt}} finished={{.State.FinishedAt}}' \
      2>/dev/null || echo "${container}: not found"
  done

  echo
  echo "local HTTP probes:"
  curl -vfsS --connect-timeout 5 --max-time 20 http://127.0.0.1:3000/ -o /tmp/artfetch-diagnostic-frontend-body 2>&1 | sed -n '1,120p' || true
  curl -vfsS --connect-timeout 5 --max-time 20 http://127.0.0.1:8080/actuator/health -o /tmp/artfetch-diagnostic-health-body 2>&1 | sed -n '1,120p' || true
  curl -sS --connect-timeout 5 --max-time 20 -o /tmp/artfetch-diagnostic-auth-body -w 'api_auth_http_code=%{http_code}\n' http://127.0.0.1:3000/api/auth/me || true
  sed -n '1,40p' /tmp/artfetch-diagnostic-auth-body 2>/dev/null || true

  echo
  echo "recent compose logs:"
  local log_compose_file=""
  if [ -f "$COMPOSE_FILE" ]; then
    log_compose_file="$COMPOSE_FILE"
  elif [ -n "${CURRENT_COMPOSE_FILE:-}" ] && [ -f "${CURRENT_COMPOSE_FILE:-}" ]; then
    log_compose_file="$CURRENT_COMPOSE_FILE"
  elif [ -f docker-compose.yml ]; then
    log_compose_file="docker-compose.yml"
  fi
  if [ -n "$log_compose_file" ]; then
    for service in postgres backend frontend jupyter; do
      echo
      echo "logs ${service}:"
      compose_for_file "$log_compose_file" logs --tail=200 "$service" || true
    done
  else
    echo "No compose file available for logs."
  fi

  echo
  echo "recent deploy history:"
  tail -n 40 backups/deploy-history.log 2>/dev/null || true
  end_group
  set -e
}

on_exit() {
  local code="$?"
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ]; then
    diagnose_failure "$code"
  fi
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
  rm -f "${PROJECT_DIR}/${COMPOSE_FILE}.candidate" "${PROJECT_DIR}/.env.release.candidate"
  if [ "$code" != "0" ] && [ "$DEPLOY_SUCCESS" != "1" ]; then
    log "Deployment failed with exit code ${code}."
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

log "Verifying package checksum..."
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
JUPYTER_IMAGE="$(read_manifest images.jupyter.ref)"
BACKEND_TAR="$(read_manifest images.backend.tar)"
FRONTEND_TAR="$(read_manifest images.frontend.tar)"
JUPYTER_TAR="$(read_manifest images.jupyter.tar)"
BACKEND_TAR_SHA="$(read_manifest images.backend.tarSha256)"
FRONTEND_TAR_SHA="$(read_manifest images.frontend.tarSha256)"
JUPYTER_TAR_SHA="$(read_manifest images.jupyter.tarSha256)"
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

if [ "$BACKEND_IMAGE" != "artfetch-backend:sha-${GIT_SHA}" ]; then
  echo "Backend image tag does not match manifest gitSha: $BACKEND_IMAGE"
  exit 1
fi
if [ "$FRONTEND_IMAGE" != "artfetch-frontend:sha-${GIT_SHA}" ]; then
  echo "Frontend image tag does not match manifest gitSha: $FRONTEND_IMAGE"
  exit 1
fi
if [ "$JUPYTER_IMAGE" != "artfetch-jupyter:sha-${GIT_SHA}" ]; then
  echo "Jupyter image tag does not match manifest gitSha: $JUPYTER_IMAGE"
  exit 1
fi
for image_tar in "$BACKEND_TAR" "$FRONTEND_TAR" "$JUPYTER_TAR"; do
  case "$image_tar" in
    images/*.tar.gz) ;;
    *)
      echo "Image tar path must be under images/ and end with .tar.gz: $image_tar"
      exit 1
      ;;
  esac
  if [ ! -f "$RELEASE_DIR/$image_tar" ]; then
    echo "Image tar missing from release package: $image_tar"
    exit 1
  fi
done
ACTUAL_BACKEND_TAR_SHA="$(sha256sum "$RELEASE_DIR/$BACKEND_TAR" | awk '{print $1}')"
ACTUAL_FRONTEND_TAR_SHA="$(sha256sum "$RELEASE_DIR/$FRONTEND_TAR" | awk '{print $1}')"
ACTUAL_JUPYTER_TAR_SHA="$(sha256sum "$RELEASE_DIR/$JUPYTER_TAR" | awk '{print $1}')"
if [ "$ACTUAL_BACKEND_TAR_SHA" != "$BACKEND_TAR_SHA" ]; then
  echo "Backend image tar checksum mismatch."
  exit 1
fi
if [ "$ACTUAL_FRONTEND_TAR_SHA" != "$FRONTEND_TAR_SHA" ]; then
  echo "Frontend image tar checksum mismatch."
  exit 1
fi
if [ "$ACTUAL_JUPYTER_TAR_SHA" != "$JUPYTER_TAR_SHA" ]; then
  echo "Jupyter image tar checksum mismatch."
  exit 1
fi
ACTUAL_COMPOSE_SHA="$(sha256sum "$NEW_COMPOSE" | awk '{print $1}')"
if [ "$ACTUAL_COMPOSE_SHA" != "$EXPECTED_COMPOSE_SHA" ]; then
  echo "Compose checksum mismatch."
  exit 1
fi

log "Target version: $VERSION"
log "Target git sha: $GIT_SHA"
log "Backend image: $BACKEND_IMAGE"
log "Frontend image: $FRONTEND_IMAGE"
log "Jupyter image: $JUPYTER_IMAGE"
log "Backend image tar: $BACKEND_TAR"
log "Frontend image tar: $FRONTEND_TAR"
log "Jupyter image tar: $JUPYTER_TAR"

cp "$NEW_COMPOSE" "${COMPOSE_FILE}.candidate"
cat > .env.release.candidate <<EOF
ARTFETCH_BACKEND_IMAGE=${BACKEND_IMAGE}
ARTFETCH_FRONTEND_IMAGE=${FRONTEND_IMAGE}
ARTFETCH_JUPYTER_IMAGE=${JUPYTER_IMAGE}
EOF
chmod 600 .env.release.candidate

log "Validating release compose config..."
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

log "Using current compose file: $CURRENT_COMPOSE_FILE"
log "Ensuring PostgreSQL is running for pre-deploy checks..."
compose_current up -d postgres

log "Checking running tasks before restart..."
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

log "Loading release images before touching current app containers..."
docker load -i "$RELEASE_DIR/$BACKEND_TAR"
docker load -i "$RELEASE_DIR/$FRONTEND_TAR"
docker load -i "$RELEASE_DIR/$JUPYTER_TAR"
docker image inspect "$BACKEND_IMAGE" >/dev/null
docker image inspect "$FRONTEND_IMAGE" >/dev/null
docker image inspect "$JUPYTER_IMAGE" >/dev/null

log "Stopping frontend, backend, and jupyter for the maintenance window..."
compose_current stop frontend backend jupyter || true

log "Capturing deployment snapshot: $SNAPSHOT_DIR"
mkdir -p "$SNAPSHOT_DIR"
chmod 700 "$SNAPSHOT_DIR"
printf '%s\n' "$CURRENT_COMPOSE_FILE" > "$SNAPSHOT_DIR/active-compose-file"
cp .env "$SNAPSHOT_DIR/.env"
chmod 600 "$SNAPSHOT_DIR/.env"
cp "$CURRENT_COMPOSE_FILE" "$SNAPSHOT_DIR/compose.yml"
[ ! -f .env.release ] || cp .env.release "$SNAPSHOT_DIR/.env.release"
[ ! -f release-manifest.json ] || cp release-manifest.json "$SNAPSHOT_DIR/release-manifest.json"
compose_current ps > "$SNAPSHOT_DIR/compose-ps.txt"
docker inspect artfetch-backend artfetch-frontend artfetch-jupyter > "$SNAPSHOT_DIR/app-container-inspect.json" 2>/dev/null || true

log "Backing up database..."
compose_current exec -T postgres sh -lc \
  'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$SNAPSHOT_DIR/artfetch.dump"
test -s "$SNAPSHOT_DIR/artfetch.dump"
log "Database backup created: $SNAPSHOT_DIR/artfetch.dump ($(du -h "$SNAPSHOT_DIR/artfetch.dump" | awk '{print $1}'))"
BACKUP_READY=1

log "Installing release files..."
mkdir -p "releases/artfetch-deploy-${VERSION}"
cp -R "$RELEASE_DIR"/. "releases/artfetch-deploy-${VERSION}/"
cp "$NEW_COMPOSE" "$COMPOSE_FILE"
cp "$MANIFEST" release-manifest.json
mv .env.release.candidate .env.release
printf '%s\n' "$COMPOSE_FILE" > active-compose-file
chmod 600 .env.release
rm -f "${COMPOSE_FILE}.candidate"

log "Starting release backend, frontend, and jupyter..."
compose_release up -d backend frontend jupyter

log "Waiting for stable containers..."
for _ in $(seq 1 60); do
  POSTGRES_STATUS="$(docker inspect artfetch-postgres --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>/dev/null || true)"
  BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  JUPYTER_STATUS="$(docker inspect artfetch-jupyter --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
  if [ "$POSTGRES_STATUS" = "running healthy" ] && [ "$BACKEND_STATUS" = "running false" ] && [ "$FRONTEND_STATUS" = "running false" ] && [ "$JUPYTER_STATUS" = "running false" ]; then
    break
  fi
  sleep 2
done

POSTGRES_STATUS="$(docker inspect artfetch-postgres --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>/dev/null || true)"
BACKEND_STATUS="$(docker inspect artfetch-backend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
FRONTEND_STATUS="$(docker inspect artfetch-frontend --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
JUPYTER_STATUS="$(docker inspect artfetch-jupyter --format '{{.State.Status}} {{.State.Restarting}}' 2>/dev/null || true)"
if [ "$POSTGRES_STATUS" != "running healthy" ] || [ "$BACKEND_STATUS" != "running false" ] || [ "$FRONTEND_STATUS" != "running false" ] || [ "$JUPYTER_STATUS" != "running false" ]; then
  echo "Containers did not become stable."
  compose_release ps
  exit 1
fi

ACTUAL_BACKEND_IMAGE="$(docker inspect artfetch-backend --format '{{.Config.Image}}')"
ACTUAL_FRONTEND_IMAGE="$(docker inspect artfetch-frontend --format '{{.Config.Image}}')"
ACTUAL_JUPYTER_IMAGE="$(docker inspect artfetch-jupyter --format '{{.Config.Image}}')"
if [ "$ACTUAL_BACKEND_IMAGE" != "$BACKEND_IMAGE" ] || [ "$ACTUAL_FRONTEND_IMAGE" != "$FRONTEND_IMAGE" ] || [ "$ACTUAL_JUPYTER_IMAGE" != "$JUPYTER_IMAGE" ]; then
  echo "Running image refs do not match the release manifest."
  exit 1
fi

compose_release ps
BACKEND_PORT="$(compose_release port backend 8080 | tail -n 1)"
if [ -z "$BACKEND_PORT" ]; then
  echo "Cannot resolve backend published port."
  exit 1
fi

log "Checking backend health at http://${BACKEND_PORT}/actuator/health"
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

log "Checking frontend at $BASE_URL"
curl -vfsSI --connect-timeout 5 --max-time 20 "$BASE_URL" 2>&1 | sed -n '1,120p'

log "Checking API auth path at ${BASE_URL}/api/auth/me"
API_CODE="$(curl -sS --connect-timeout 5 --max-time 20 -o /tmp/artfetch-api-check-body -w '%{http_code}' "${BASE_URL}/api/auth/me" || true)"
if [ "$API_CODE" != "401" ]; then
  echo "API auth check failed with HTTP $API_CODE; expected 401 for unauthenticated /api/auth/me."
  cat /tmp/artfetch-api-check-body || true
  exit 1
fi

log "Checking DESCRIPTION task type schema support..."
TASK_TYPE_CONSTRAINT="$(compose_release exec -T postgres sh -lc \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select pg_get_constraintdef(oid) from pg_constraint where conrelid = '\''search_tasks'\''::regclass and contype = '\''c'\'';"')"
if ! printf '%s' "$TASK_TYPE_CONSTRAINT" | grep -q DESCRIPTION; then
  echo "search_tasks constraint does not include DESCRIPTION."
  exit 1
fi

log "Checking recent backend errors..."
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
  printf 'jupyter=%s ' "$JUPYTER_IMAGE"
  printf 'backup=%s ' "$SNAPSHOT_DIR/artfetch.dump"
  printf 'status=auto-verified\n'
} >> backups/deploy-history.log

DEPLOY_SUCCESS=1
echo "Deployment auto-verification passed."
