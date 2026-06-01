#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${ARTFETCH_PROJECT_DIR:-${PROJECT_DIR:-/opt/artfetch}}"
WIPE_DATA="${ARTFETCH_WIPE_DATA:-0}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

for cmd in docker; do
  require_command "$cmd"
done

if [ -d "$PROJECT_DIR" ]; then
  cd "$PROJECT_DIR"
  if [ -f docker-compose.prod.yml ] && [ -f .env ] && [ -f .env.release ]; then
    docker compose --env-file .env --env-file .env.release -f docker-compose.prod.yml down --remove-orphans || true
  fi
  rm -f .env.release.candidate docker-compose.prod.yml.candidate
  find releases -maxdepth 1 -type d -name '.candidate-*' -exec rm -rf {} + 2>/dev/null || true
fi

docker rm -f artfetch-backend artfetch-frontend artfetch-jupyter 2>/dev/null || true

if [ "$WIPE_DATA" = "1" ]; then
  docker rm -f artfetch-postgres 2>/dev/null || true
  if [ -d "$PROJECT_DIR" ]; then
    cd "$PROJECT_DIR"
    docker volume rm artfetch_postgres_data 2>/dev/null || true
    rm -rf storage/original-images backend/logs backups releases .env .env.release docker-compose.prod.yml release-manifest.json active-compose-file
  fi
fi
