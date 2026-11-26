#!/usr/bin/env bash
# Only called by the official PostgreSQL image when its data directory is empty.
set -Eeuo pipefail
: "${ERP_NEXT_DB_PASSWORD:?Missing ERP application password}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=app_password="$ERP_NEXT_DB_PASSWORD" <<'SQL'
CREATE ROLE erp_next LOGIN PASSWORD :'app_password';
ALTER DATABASE erp_next OWNER TO erp_next;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO erp_next;
SQL
