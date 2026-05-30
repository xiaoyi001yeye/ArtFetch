#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/release/artfetch-release.sh [options] <version> <image-owner>

Options:
  --dry-run       Build and package locally without pushing tags or images.
  --allow-dirty   Allow a dirty git worktree. Intended only for dry-run rehearsal.
  -h, --help      Show help.

Examples:
  scripts/release/artfetch-release.sh --dry-run --allow-dirty 2026.05.24.1 local
  scripts/release/artfetch-release.sh 2026.05.24.1 my-org

Environment:
  REGISTRY       default: ghcr.io
  PLATFORMS     default: linux/amd64
  SKIP_APP_BUILD default: 0; set 1 only when a previous build already passed

Before a real release:
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
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

VERSION="${1:?version is required, for example 2026.05.24.1}"
IMAGE_OWNER="${2:?image owner is required, for example my-org}"
REGISTRY="${REGISTRY:-ghcr.io}"
PLATFORMS="${PLATFORMS:-linux/amd64}"
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

if [ -n "$(git status --porcelain)" ] && [ "$ALLOW_DIRTY" != "1" ]; then
  echo "Git worktree is not clean. Commit or stash changes before release."
  git status --short
  exit 1
fi

if [ "$DRY_RUN" != "1" ] && [ "$ALLOW_DIRTY" = "1" ]; then
  echo "--allow-dirty is only permitted with --dry-run."
  exit 1
fi

GIT_SHA="$(git rev-parse HEAD)"
GIT_SHORT_SHA="$(git rev-parse --short HEAD)"
TAG="release/${VERSION}"
REMOTE_TAG_SHA=""
if git remote get-url origin >/dev/null 2>&1; then
  REMOTE_TAG_SHA="$(git ls-remote origin "refs/tags/${TAG}^{}" | awk '{print $1}' || true)"
  if [ -z "$REMOTE_TAG_SHA" ]; then
    REMOTE_TAG_SHA="$(git ls-remote origin "refs/tags/${TAG}" | awk '{print $1}' || true)"
  fi
fi

if [ -n "$REMOTE_TAG_SHA" ] && [ "$REMOTE_TAG_SHA" != "$GIT_SHA" ]; then
  echo "Remote tag ${TAG} already exists and points to another commit: ${REMOTE_TAG_SHA}"
  exit 1
fi

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  LOCAL_TAG_SHA="$(git rev-list -n 1 "$TAG")"
  if [ "$LOCAL_TAG_SHA" != "$GIT_SHA" ]; then
    echo "Local tag ${TAG} already exists and points to another commit: ${LOCAL_TAG_SHA}"
    exit 1
  fi
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

echo "Release version: $VERSION"
echo "Git SHA: $GIT_SHA"
echo "Registry: $REGISTRY/$IMAGE_OWNER"
echo "Platforms: $PLATFORMS"
echo "Dry run: $DRY_RUN"

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

if [ "$DRY_RUN" != "1" ]; then
  if [ -z "$REMOTE_TAG_SHA" ]; then
    if ! git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
      git tag -a "$TAG" -m "Release ${VERSION}"
    fi
    git push origin "$TAG"
  else
    echo "Remote tag ${TAG} already exists for this commit; reusing it."
  fi
fi

BACKEND_IMAGE="${REGISTRY}/${IMAGE_OWNER}/artfetch-backend"
FRONTEND_IMAGE="${REGISTRY}/${IMAGE_OWNER}/artfetch-frontend"

if [ "$DRY_RUN" = "1" ]; then
  BACKEND_IMAGE="artfetch-local/artfetch-backend"
  FRONTEND_IMAGE="artfetch-local/artfetch-frontend"

  echo "Building backend image locally..."
  docker build \
    --label "org.opencontainers.image.title=ArtFetch Backend" \
    --label "org.opencontainers.image.revision=${GIT_SHA}" \
    --label "org.opencontainers.image.version=${VERSION}" \
    -t "${BACKEND_IMAGE}:${VERSION}" \
    -t "${BACKEND_IMAGE}:${GIT_SHORT_SHA}" \
    backend

  echo "Building frontend image locally..."
  docker build \
    --label "org.opencontainers.image.title=ArtFetch Frontend" \
    --label "org.opencontainers.image.revision=${GIT_SHA}" \
    --label "org.opencontainers.image.version=${VERSION}" \
    -t "${FRONTEND_IMAGE}:${VERSION}" \
    -t "${FRONTEND_IMAGE}:${GIT_SHORT_SHA}" \
    frontend

  BACKEND_DIGEST="$(docker image inspect "${BACKEND_IMAGE}:${VERSION}" --format '{{.Id}}')"
  FRONTEND_DIGEST="$(docker image inspect "${FRONTEND_IMAGE}:${VERSION}" --format '{{.Id}}')"
  BACKEND_REF="${BACKEND_IMAGE}:${VERSION}"
  FRONTEND_REF="${FRONTEND_IMAGE}:${VERSION}"
else
  if ! docker buildx inspect artfetch-release-builder >/dev/null 2>&1; then
    docker buildx create --name artfetch-release-builder --use
  else
    docker buildx use artfetch-release-builder
  fi

  echo "Building and pushing backend image..."
  docker buildx build \
    --platform "$PLATFORMS" \
    --label "org.opencontainers.image.title=ArtFetch Backend" \
    --label "org.opencontainers.image.revision=${GIT_SHA}" \
    --label "org.opencontainers.image.version=${VERSION}" \
    -t "${BACKEND_IMAGE}:${VERSION}" \
    -t "${BACKEND_IMAGE}:${GIT_SHORT_SHA}" \
    --push \
    backend

  echo "Building and pushing frontend image..."
  docker buildx build \
    --platform "$PLATFORMS" \
    --label "org.opencontainers.image.title=ArtFetch Frontend" \
    --label "org.opencontainers.image.revision=${GIT_SHA}" \
    --label "org.opencontainers.image.version=${VERSION}" \
    -t "${FRONTEND_IMAGE}:${VERSION}" \
    -t "${FRONTEND_IMAGE}:${GIT_SHORT_SHA}" \
    --push \
    frontend

  BACKEND_DIGEST="$(docker buildx imagetools inspect "${BACKEND_IMAGE}:${VERSION}" --format '{{.Manifest.Digest}}')"
  FRONTEND_DIGEST="$(docker buildx imagetools inspect "${FRONTEND_IMAGE}:${VERSION}" --format '{{.Manifest.Digest}}')"
  BACKEND_REF="${BACKEND_IMAGE}@${BACKEND_DIGEST}"
  FRONTEND_REF="${FRONTEND_IMAGE}@${FRONTEND_DIGEST}"
fi

if [ -z "$BACKEND_DIGEST" ] || [ -z "$FRONTEND_DIGEST" ]; then
  echo "Failed to resolve image digests."
  exit 1
fi

DIST_ROOT="dist"
RELEASE_DIR="${DIST_ROOT}/artfetch-deploy-${VERSION}"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/scripts"

cp docker-compose.prod.yml "$RELEASE_DIR/docker-compose.prod.yml"
cp .env.example "$RELEASE_DIR/.env.example"

if [ -d scripts/deploy ]; then
  cp scripts/deploy/*.sh "$RELEASE_DIR/scripts/" 2>/dev/null || true
fi

COMPOSE_SHA256="$(sha256_value "$RELEASE_DIR/docker-compose.prod.yml")"
CREATED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"

python3 - <<PY > "$RELEASE_DIR/release-manifest.json"
import json

manifest = {
    "app": "artfetch",
    "version": "$VERSION",
    "dryRun": bool($DRY_RUN),
    "gitSha": "$GIT_SHA",
    "gitShortSha": "$GIT_SHORT_SHA",
    "createdAt": "$CREATED_AT",
    "images": {
        "backend": {
            "tag": "${BACKEND_IMAGE}:${VERSION}",
            "digest": "$BACKEND_DIGEST",
            "ref": "$BACKEND_REF"
        },
        "frontend": {
            "tag": "${FRONTEND_IMAGE}:${VERSION}",
            "digest": "$FRONTEND_DIGEST",
            "ref": "$FRONTEND_REF"
        }
    },
    "compose": {
        "file": "docker-compose.prod.yml",
        "sha256": "$COMPOSE_SHA256"
    },
    "migrations": [],
    "build": {
        "frontendBuild": "skipped" if "$SKIP_APP_BUILD" == "1" else "passed",
        "backendPackage": "skipped" if "$SKIP_APP_BUILD" == "1" else "passed",
        "imageBuild": "passed"
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
echo "Backend image:"
echo "  $BACKEND_REF"
echo "Frontend image:"
echo "  $FRONTEND_REF"
echo
echo "Next steps:"
echo "  scp ${PACKAGE} ${PACKAGE}.sha256 artfetch-prod:/tmp/"
echo "  ssh artfetch-prod 'bash /opt/artfetch/scripts/artfetch-deploy-release.sh ${VERSION} /tmp/artfetch-deploy-${VERSION}.tgz'"
