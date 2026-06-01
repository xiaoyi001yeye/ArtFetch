#!/usr/bin/env bash
set -Eeuo pipefail

GITHUB_REPOSITORY_NAME="${ARTFETCH_GITHUB_REPOSITORY:-__GITHUB_REPOSITORY__}"
PROJECT_DIR="${ARTFETCH_PROJECT_DIR:-${PROJECT_DIR:-/opt/artfetch}}"
GITHUB_API_URL="${GITHUB_API_URL:-https://api.github.com}"
TMP_DIR=""

log() {
  printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

cleanup() {
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}

trap cleanup EXIT

for cmd in curl tar sha256sum python3 awk grep sed docker; do
  require_command "$cmd"
done
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required: docker compose version failed."
  exit 1
fi
if [ -z "$GITHUB_REPOSITORY_NAME" ] || [ "$GITHUB_REPOSITORY_NAME" = "__GITHUB_REPOSITORY__" ]; then
  echo "ARTFETCH_GITHUB_REPOSITORY is required when this script was not stamped by the Release workflow."
  echo "Example: ARTFETCH_GITHUB_REPOSITORY=owner/repo bash install-or-upgrade-latest.sh"
  exit 1
fi

TMP_DIR="$(mktemp -d)"
LATEST_JSON="$TMP_DIR/latest-release.json"

auth_header=()
if [ -n "${GITHUB_TOKEN:-${GH_TOKEN:-}}" ]; then
  auth_header=(-H "Authorization: Bearer ${GITHUB_TOKEN:-${GH_TOKEN:-}}")
fi

log "Resolving latest ArtFetch release from ${GITHUB_REPOSITORY_NAME}..."
curl -fsSL \
  -H "Accept: application/vnd.github+json" \
  "${auth_header[@]}" \
  "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY_NAME}/releases/latest" \
  -o "$LATEST_JSON"

eval "$(
  python3 - "$LATEST_JSON" <<'PY'
import json
import shlex
import sys

release = json.load(open(sys.argv[1], encoding="utf-8"))
tag = release["tag_name"]
version = tag.removeprefix("release/")
assets = {asset["name"]: asset["url"] for asset in release.get("assets", [])}
required = [
    "release-manifest.json",
    f"artfetch-deploy-{version}.tgz",
    f"artfetch-deploy-{version}.tgz.sha256",
]
missing = [name for name in required if name not in assets]
if missing:
    raise SystemExit("Missing release assets: " + ", ".join(missing))
for key, value in {
    "VERSION": version,
    "MANIFEST_URL": assets["release-manifest.json"],
    "PACKAGE_URL": assets[f"artfetch-deploy-{version}.tgz"],
    "PACKAGE_SHA_URL": assets[f"artfetch-deploy-{version}.tgz.sha256"],
}.items():
    print(f"{key}={shlex.quote(value)}")
PY
)"

log "Latest release version: ${VERSION}"
download_asset() {
  local url="$1"
  local output="$2"
  curl -fsSL \
    -H "Accept: application/octet-stream" \
    "${auth_header[@]}" \
    "$url" \
    -o "$output"
}

download_asset "$MANIFEST_URL" "$TMP_DIR/release-manifest.json"
download_asset "$PACKAGE_URL" "$TMP_DIR/artfetch-deploy-${VERSION}.tgz"
download_asset "$PACKAGE_SHA_URL" "$TMP_DIR/artfetch-deploy-${VERSION}.tgz.sha256"

log "Verifying downloaded package checksum..."
(cd "$TMP_DIR" && sha256sum -c "artfetch-deploy-${VERSION}.tgz.sha256")

tar -xzf "$TMP_DIR/artfetch-deploy-${VERSION}.tgz" -C "$TMP_DIR"
INNER_SCRIPT="$TMP_DIR/artfetch-deploy-${VERSION}/scripts/artfetch-install-or-upgrade.sh"
if [ ! -f "$INNER_SCRIPT" ]; then
  echo "Release package is missing scripts/artfetch-install-or-upgrade.sh"
  exit 1
fi

chmod +x "$INNER_SCRIPT"
ARTFETCH_PROJECT_DIR="$PROJECT_DIR" bash "$INNER_SCRIPT" "$VERSION" "$TMP_DIR/artfetch-deploy-${VERSION}.tgz"
