#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

: "${MYSQL_URL:?Missing MYSQL_URL}"
: "${MYSQL_USERNAME:?Missing MYSQL_USERNAME}"
: "${MYSQL_PASSWORD:?Missing MYSQL_PASSWORD}"

export DM_URL="${DM_URL:-jdbc:dm://localhost:5236}"
export DM_USERNAME="${DM_USERNAME:-SYSDBA}"
export DM_PASSWORD="${DM_PASSWORD:-SysdbA123}"

cd "${ROOT_DIR}"
./mvnw -q -Pdm8-migration -DskipTests compile exec:java \
  -Dexec.mainClass=com.qy.citytechupgrade.tool.MysqlToDm8MigrationTool
