#!/usr/bin/env bash
# Shared by native development and the official MySQL container. Empty databases only.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
: "${MYSQL_DATABASE:=server}"
: "${MYSQL_USER:=root}"
[[ "$MYSQL_DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'Invalid database name' >&2; exit 1; }
args=(--default-character-set=utf8mb4 -u "$MYSQL_USER")
if [[ -n "${MYSQL_HOST:-}" ]]; then args+=(--protocol=TCP -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}"); fi
if [[ -n "${MYSQL_PASSWORD:-}" ]]; then export MYSQL_PWD="$MYSQL_PASSWORD"; fi
count=$(mysql "${args[@]}" "$MYSQL_DATABASE" -Nse 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()')
[[ "$count" == 0 ]] || { echo 'Database is not empty. Refusing to overwrite tables; use a new development database or a reviewed upgrade.' >&2; exit 1; }
for script in ry_20250417.sql quartz.sql business_schema.sql sys_file.sql zhongji_contract_schema.sql zhongji_contract_menus.sql work_projects_schema.sql work_projects_menus.sql wbcard_workspace.sql project_finance_schema.sql project_finance_menus.sql finance_ledger.sql finance_ledger_menus.sql report_credentials.sql inspection_reports.sql employees.sql employee_menus.sql; do
    echo "Initializing $script"
    mysql "${args[@]}" "$MYSQL_DATABASE" < "$ROOT/sql/$script"
done
mysql "${args[@]}" "$MYSQL_DATABASE" <<'SQL'
UPDATE sys_job SET status='1';
CREATE TABLE zjerp_schema_state(version VARCHAR(64) PRIMARY KEY, initialized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO zjerp_schema_state(version) VALUES('20261029-current');
SQL
echo 'Current schema and menus initialized.'
