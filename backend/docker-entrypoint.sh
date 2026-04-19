#!/usr/bin/env bash

set -euo pipefail

DB_URL="${SPRING_DATASOURCE_URL:-}"
DB_HOST="postgres"
DB_PORT="5432"

if [[ -n "${DB_URL}" && "${DB_URL}" =~ ^jdbc:postgresql://([^/:]+)(:([0-9]+))?/ ]]; then
  DB_HOST="${BASH_REMATCH[1]}"
  if [[ -n "${BASH_REMATCH[3]:-}" ]]; then
    DB_PORT="${BASH_REMATCH[3]}"
  fi
fi

echo "Waiting for database at ${DB_HOST}:${DB_PORT}..."

for attempt in {1..60}; do
  if getent hosts "${DB_HOST}" >/dev/null 2>&1 && timeout 1 bash -c "</dev/tcp/${DB_HOST}/${DB_PORT}" >/dev/null 2>&1; then
    echo "Database is reachable."
    exec java -jar app.jar
  fi

  echo "Database not ready yet (${attempt}/60), retrying in 2s..."
  sleep 2
done

echo "Database did not become reachable in time." >&2
exit 1
