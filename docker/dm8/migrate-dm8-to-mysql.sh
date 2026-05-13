#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export MYSQL_URL="${MYSQL_URL:-jdbc:mysql://172.17.0.1:3306/city_upgrade?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}"
export MYSQL_USERNAME="${MYSQL_USERNAME:-qiyuan}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-qiyuan}"
export DM_URL="${DM_URL:-jdbc:dm://127.0.0.1:5236}"
export DM_USERNAME="${DM_USERNAME:-SYSDBA}"
export DM_PASSWORD="${DM_PASSWORD:-SysdbA123}"
export DM_SCHEMA="${DM_SCHEMA:-CITY_UPGRADE}"

cd "${ROOT_DIR}"
./mvnw -q -Pdm8-migration -DskipTests compile exec:java \
  -Dexec.mainClass=com.qy.citytechupgrade.tool.Dm8ToMysqlMigrationTool
