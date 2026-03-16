#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTAINER_NAME="${DM8_CONTAINER_NAME:-city-tech-upgrade-dm8}"
DM8_USERNAME="${DM8_USERNAME:-SYSDBA}"
DM8_PASSWORD="${DM8_PASSWORD:-SysdbA123}"
DM8_HOST="${DM8_HOST:-localhost}"
DM8_PORT="${DM8_PORT:-5236}"
SCHEMA_SQL="${ROOT_DIR}/sql/dm8/schema_v1_dm8.sql"

if [[ ! -f "${SCHEMA_SQL}" ]]; then
  echo "Missing schema file: ${SCHEMA_SQL}" >&2
  exit 1
fi

docker exec -i "${CONTAINER_NAME}" bash -lc "/opt/dmdbms/bin/disql ${DM8_USERNAME}/${DM8_PASSWORD}@${DM8_HOST}:${DM8_PORT}" < "${SCHEMA_SQL}"
