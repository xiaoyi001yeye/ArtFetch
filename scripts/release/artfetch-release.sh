#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/release/artfetch-release.sh [options] <version> [legacy-image-owner]

Options:
  --dry-run       Accepted for backward compatibility; this script never pushes.
  --allow-dirty   Allow a dirty git worktree for local rehearsal.
  -h, --help      Show help.

Examples:
  scripts/release/artfetch-release.sh --allow-dirty 2026.06.01.1

Environment:
  SKIP_APP_BUILD default: 0; set 1 only when a previous build already passed

This script builds local Docker images, saves them as tar.gz files, and packages
those tarballs into dist/artfetch-deploy-<version>.tgz. It does not push to GHCR
or any other registry.
USAGE
}

DRY_RUN=0
ALLOW_DIRTY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --allow-dirty)
      ALLOW_DIRTY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
    *)
      break
      ;;
  esac
done

VERSION="${1:?version is required, for example 2026.06.01.1}"
LEGACY_IMAGE_OWNER="${2:-}"
SKIP_APP_BUILD="${SKIP_APP_BUILD:-0}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

sha256_value() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  else
    shasum -a 256 "$1"
  fi
}

run_backend_package() {
  if [ -x backend/mvnw ]; then
    (cd backend && ./mvnw package -DskipTests)
  elif command -v mvn >/dev/null 2>&1; then
    (cd backend && mvn package -DskipTests)
  else
    echo "System Maven not found; using Maven Docker image for backend package."
    docker run --rm \
      -u "$(id -u):$(id -g)" \
      -e HOME=/tmp \
      -e MAVEN_CONFIG=/tmp/.m2 \
      -v "$ROOT_DIR/backend:/workspace" \
      -w /workspace \
      maven:3.9-eclipse-temurin-17 \
      mvn package -DskipTests
  fi
}

case "$VERSION" in
  [0-9][0-9][0-9][0-9].[0-9][0-9].[0-9][0-9].[0-9]*) ;;
  *)
    echo "Invalid version: $VERSION"
    echo "Expected format: YYYY.MM.DD.N"
    exit 1
    ;;
esac

if [ -n "$LEGACY_IMAGE_OWNER" ]; then
  echo "Ignoring legacy image owner argument: $LEGACY_IMAGE_OWNER"
fi
if [ "$DRY_RUN" = "1" ]; then
  echo "--dry-run is accepted for compatibility; this script never pushes."
fi

if [ -n "$(git status --porcelain)" ] && [ "$ALLOW_DIRTY" != "1" ]; then
  echo "Git worktree is not clean. Commit or stash changes before release."
  git status --short
  exit 1
fi

if git ls-files --others --exclude-standard | grep -E '(^|/)(\.env|.*\.dump|.*\.sql|id_rsa|id_ed25519)$' >/dev/null; then
  echo "Potential secret or dump file is untracked. Review before release:"
  git ls-files --others --exclude-standard | grep -E '(^|/)(\.env|.*\.dump|.*\.sql|id_rsa|id_ed25519)$'
  exit 1
fi

if [ ! -f docker-compose.prod.yml ]; then
  echo "docker-compose.prod.yml is required for artifact deployment."
  exit 1
fi

GIT_SHA="$(git rev-parse HEAD)"
GIT_SHORT_SHA="$(git rev-parse --short HEAD)"
BACKEND_IMAGE="artfetch-backend:sha-${GIT_SHA}"
FRONTEND_IMAGE="artfetch-frontend:sha-${GIT_SHA}"
JUPYTER_IMAGE="artfetch-jupyter:sha-${GIT_SHA}"

echo "Release package version: $VERSION"
echo "Git SHA: $GIT_SHA"
echo "Backend image tag: $BACKEND_IMAGE"
echo "Frontend image tag: $FRONTEND_IMAGE"
echo "Jupyter image tag: $JUPYTER_IMAGE"

if [ "$SKIP_APP_BUILD" != "1" ]; then
  echo "Building frontend..."
  (
    cd frontend
    npm ci
    npm run build
  )

  echo "Building backend..."
  run_backend_package
else
  echo "Skipping app builds because SKIP_APP_BUILD=1."
fi

echo "Building backend image locally..."
docker build \
  --label "org.opencontainers.image.title=ArtFetch Backend" \
  --label "org.opencontainers.image.revision=${GIT_SHA}" \
  --label "org.opencontainers.image.version=${VERSION}" \
  -t "$BACKEND_IMAGE" \
  backend

echo "Building frontend image locally..."
docker build \
  --label "org.opencontainers.image.title=ArtFetch Frontend" \
  --label "org.opencontainers.image.revision=${GIT_SHA}" \
  --label "org.opencontainers.image.version=${VERSION}" \
  -t "$FRONTEND_IMAGE" \
  frontend

echo "Building Jupyter image locally..."
docker build \
  --label "org.opencontainers.image.title=ArtFetch Jupyter" \
  --label "org.opencontainers.image.revision=${GIT_SHA}" \
  --label "org.opencontainers.image.version=${VERSION}" \
  -t "$JUPYTER_IMAGE" \
  ml

DIST_ROOT="dist"
RELEASE_DIR="${DIST_ROOT}/artfetch-deploy-${VERSION}"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/scripts" "$RELEASE_DIR/images"

BACKEND_TAR="images/artfetch-backend-${GIT_SHA}.tar.gz"
FRONTEND_TAR="images/artfetch-frontend-${GIT_SHA}.tar.gz"
JUPYTER_TAR="images/artfetch-jupyter-${GIT_SHA}.tar.gz"

echo "Saving image tarballs..."
docker save "$BACKEND_IMAGE" | gzip -c > "$RELEASE_DIR/$BACKEND_TAR"
docker save "$FRONTEND_IMAGE" | gzip -c > "$RELEASE_DIR/$FRONTEND_TAR"
docker save "$JUPYTER_IMAGE" | gzip -c > "$RELEASE_DIR/$JUPYTER_TAR"
(
  cd "$RELEASE_DIR/images"
  sha256_file "artfetch-backend-${GIT_SHA}.tar.gz" > "artfetch-backend-${GIT_SHA}.tar.gz.sha256"
  sha256_file "artfetch-frontend-${GIT_SHA}.tar.gz" > "artfetch-frontend-${GIT_SHA}.tar.gz.sha256"
  sha256_file "artfetch-jupyter-${GIT_SHA}.tar.gz" > "artfetch-jupyter-${GIT_SHA}.tar.gz.sha256"
)

BACKEND_IMAGE_ID="$(docker image inspect "$BACKEND_IMAGE" --format '{{.Id}}')"
FRONTEND_IMAGE_ID="$(docker image inspect "$FRONTEND_IMAGE" --format '{{.Id}}')"
JUPYTER_IMAGE_ID="$(docker image inspect "$JUPYTER_IMAGE" --format '{{.Id}}')"
BACKEND_TAR_SHA256="$(sha256_value "$RELEASE_DIR/$BACKEND_TAR")"
FRONTEND_TAR_SHA256="$(sha256_value "$RELEASE_DIR/$FRONTEND_TAR")"
JUPYTER_TAR_SHA256="$(sha256_value "$RELEASE_DIR/$JUPYTER_TAR")"

cp docker-compose.prod.yml "$RELEASE_DIR/docker-compose.prod.yml"
cp .env.example "$RELEASE_DIR/.env.example"
if [ -d scripts/deploy ]; then
  cp scripts/deploy/*.sh "$RELEASE_DIR/scripts/" 2>/dev/null || true
fi

COMPOSE_SHA256="$(sha256_value "$RELEASE_DIR/docker-compose.prod.yml")"
BUILT_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

python3 - <<PY > "$RELEASE_DIR/release-manifest.json"
import json

manifest = {
    "app": "artfetch",
    "version": "$VERSION",
    "gitSha": "$GIT_SHA",
    "gitShortSha": "$GIT_SHORT_SHA",
    "builtAt": "$BUILT_AT",
    "images": {
        "backend": {
            "tag": "$BACKEND_IMAGE",
            "ref": "$BACKEND_IMAGE",
            "imageId": "$BACKEND_IMAGE_ID",
            "tar": "$BACKEND_TAR",
            "tarSha256": "$BACKEND_TAR_SHA256"
        },
        "frontend": {
            "tag": "$FRONTEND_IMAGE",
            "ref": "$FRONTEND_IMAGE",
            "imageId": "$FRONTEND_IMAGE_ID",
            "tar": "$FRONTEND_TAR",
            "tarSha256": "$FRONTEND_TAR_SHA256"
        },
        "jupyter": {
            "tag": "$JUPYTER_IMAGE",
            "ref": "$JUPYTER_IMAGE",
            "imageId": "$JUPYTER_IMAGE_ID",
            "tar": "$JUPYTER_TAR",
            "tarSha256": "$JUPYTER_TAR_SHA256"
        }
    },
    "externalImages": {
        "postgres": {
            "service": "postgres",
            "ref": "postgres:16-alpine"
        }
    },
    "compose": {
        "file": "docker-compose.prod.yml",
        "sha256": "$COMPOSE_SHA256"
    },
    "build": {
        "frontendBuild": "skipped" if "$SKIP_APP_BUILD" == "1" else "passed",
        "backendPackage": "skipped" if "$SKIP_APP_BUILD" == "1" else "passed",
        "imageBuild": "passed",
        "imageExport": "passed"
    }
}

print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY

PACKAGE="${DIST_ROOT}/artfetch-deploy-${VERSION}.tgz"
tar -C "$DIST_ROOT" -czf "$PACKAGE" "artfetch-deploy-${VERSION}"
(
  cd "$DIST_ROOT"
  sha256_file "$(basename "$PACKAGE")" > "$(basename "${PACKAGE}.sha256")"
)

echo "Release package created:"
echo "  $PACKAGE"
echo "  ${PACKAGE}.sha256"
echo
echo "Image tarballs included:"
echo "  $BACKEND_TAR"
echo "  $FRONTEND_TAR"
echo "  $JUPYTER_TAR"
echo
echo "Next steps:"
echo "  Upload ${PACKAGE} and ${PACKAGE}.sha256 to a GitHub Release, or use the Release workflow."
echo "  ssh artfetch-prod 'bash /opt/artfetch/scripts/artfetch-deploy-release.sh ${VERSION} /tmp/artfetch-deploy-${VERSION}.tgz'"
