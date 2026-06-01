#!/usr/bin/env bash
set -euo pipefail

cat <<'MSG'
Local release packaging is disabled.

ArtFetch release artifacts must be built by the GitHub Actions Release workflow:
  Actions -> Release -> Run workflow -> version Vx.y.z

The production server must consume GitHub Release attachments through:
  install-or-upgrade-latest.sh
MSG

exit 1
