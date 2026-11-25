#!/usr/bin/env bash
# Dedicated disposable DB + private directory. Never joins the production compose project.
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ $# != 1 ]]; then echo "Usage: bash scripts/erp-next-trial-provision.sh /absolute/new-trial-directory" >&2; exit 2; fi
command -v docker >/dev/null
command -v python >/dev/null
command -v curl >/dev/null
# Inherited production environment variables must not override generated trial credentials.
unset ERP_NEXT_DB_PASSWORD ERP_NEXT_DB_ROOT_PASSWORD ERP_BOOTSTRAP_TOKEN COMPOSE_PROJECT_NAME COMPOSE_FILE COMPOSE_PROFILES
trial="$(python - "$1" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1])
if not p.is_absolute() or p.exists() or p.is_symlink(): raise SystemExit("Use a new absolute trial directory")
p.mkdir(mode=0o700,parents=True)
print(p.resolve())
PY
)"
id="$(python -c 'import uuid;print(uuid.uuid4().hex)')"
project="erp-next-trial-$id"
password="$(python -c 'import secrets;print(secrets.token_hex(24))')"
rootpassword="$(python -c 'import secrets;print(secrets.token_hex(24))')"
mkdir "$trial/files"
bootstrap="$(python -c 'import secrets;print(secrets.token_hex(24))')"
adminpassword="$(python -c 'import secrets;print(secrets.token_hex(24))')"
printf 'ERP_NEXT_DB_PASSWORD=%s\nERP_NEXT_DB_ROOT_PASSWORD=%s\nTRIAL_ID=%s\nERP_BOOTSTRAP_TOKEN=%s\n' "$password" "$rootpassword" "$id" "$bootstrap" > "$trial/.env"
printf '%s\n' "$adminpassword" > "$trial/admin-password"
# Docker chooses an unused loopback port; no fixed production volume names.
cat > "$trial/compose.yaml" <<'EOF'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${ERP_NEXT_DB_ROOT_PASSWORD}
      MYSQL_DATABASE: erp_next
      MYSQL_USER: erp_next
      MYSQL_PASSWORD: ${ERP_NEXT_DB_PASSWORD}
    ports: ["127.0.0.1::3306"]
    volumes: ["data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD-SHELL", "MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysqladmin ping -h 127.0.0.1 -u root --silent"]
      interval: 5s
      timeout: 5s
      retries: 40
  schema:
    image: flyway/flyway:10.20.1
    environment:
      FLYWAY_URL: jdbc:mysql://mysql:3306/erp_next?useSSL=false&allowPublicKeyRetrieval=true
      FLYWAY_USER: erp_next
      FLYWAY_PASSWORD: ${ERP_NEXT_DB_PASSWORD}
      FLYWAY_CONNECT_RETRIES: "30"
      FLYWAY_CLEAN_DISABLED: "true"
    volumes: ["__MIGRATIONS__:/flyway/sql:ro"]
    command: ["migrate"]
  api:
    build:
      context: "__BACKEND__"
      dockerfile: Dockerfile
    environment:
      ERP_NEXT_PORT: "8090"
      ERP_NEXT_DB_URL: jdbc:mysql://mysql:3306/erp_next?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      ERP_NEXT_DB_USER: erp_next
      ERP_NEXT_DB_PASSWORD: ${ERP_NEXT_DB_PASSWORD}
      ERP_BOOTSTRAP_TOKEN: ${ERP_BOOTSTRAP_TOKEN}
      ERP_LEGACY_SCAN_ENABLED: "false"
      ERP_FILES_ROOT: /var/lib/erp-next/files
    ports: ["127.0.0.1::8090"]
    depends_on:
      mysql:
        condition: service_healthy
volumes:
  data: {}
EOF
python - "$trial/compose.yaml" "$ROOT/erp-next/db/migration" "$ROOT/backend/erp-next" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]);p.write_text(p.read_text().replace("__MIGRATIONS__",sys.argv[2]).replace("__BACKEND__",sys.argv[3]),encoding="utf-8")
PY
printf '%s\n' "$project" > "$trial/project-name"
dc(){ docker compose -p "$project" --env-file "$trial/.env" -f "$trial/compose.yaml" "$@"; }
dc up -d --wait mysql
dc run --rm schema
# Marker only exists in this disposable trial, not in regular Flyway migrations.
dc exec -T -e MYSQL_PWD="$password" mysql mysql -u erp_next erp_next <<SQL
CREATE TABLE erp_trial_isolation_marker (id INT PRIMARY KEY, trial_id CHAR(32) NOT NULL, server_uuid CHAR(36) NOT NULL);
INSERT INTO erp_trial_isolation_marker VALUES(1,'$id',@@server_uuid);
SQL
server="$(dc exec -T -e MYSQL_PWD="$password" mysql mysql -u erp_next erp_next -N -e 'SELECT @@server_uuid')"
python - "$trial/files/.erp-next-trial.json" "$id" "$server" <<'PY'
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({"format":"erp-next-trial-v1","trialId":sys.argv[2],"serverUuid":sys.argv[3]}),encoding="utf-8")
PY
# Bootstrap the disposable API only; it is stopped before any migration.
dc up -d --build --wait api
apiaddr="$(dc port api 8090)"
python - "$apiaddr" "$bootstrap" "$adminpassword" <<'PY'
import json,sys,urllib.request
addr,token,password=sys.argv[1:]
req=urllib.request.Request("http://"+addr+"/api/bootstrap/admin",
 data=json.dumps({"username":"admin","displayName":"Trial administrator","password":password}).encode(),
 headers={"Content-Type":"application/json","X-Bootstrap-Token":token})
with urllib.request.urlopen(req,timeout=20) as response:
    if response.status!=201:raise SystemExit("Trial bootstrap failed")
PY
dc stop api
dc exec -T -e MYSQL_PWD="$password" mysql mysql -u erp_next erp_next -N -e "SELECT id FROM erp_identity WHERE username='admin'" > "$trial/admin-id"
dc port mysql 3306 > "$trial/mysql-address"
printf 'Trial: %s\nMySQL: %s\nSecrets: %s/.env\nFiles: %s/files\n' "$project" "$(cat "$trial/mysql-address")" "$trial" "$trial"
echo "Trial administrator initialized and API stopped. Actor ID: $(cat "$trial/admin-id"). Password is in admin-password (private)."
echo "For a later UI trial use only this database and its files directory, with an independently configured API; never use production Compose volumes."
echo "Do not start a production compose project here. Preserve the directory until trial reconciliation is complete."
