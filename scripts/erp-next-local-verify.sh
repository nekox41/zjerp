#!/usr/bin/env bash
# Run the same ERP smoke journey locally without using Actions or touching the real ERP volumes.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mode="${1:---smoke}"
if [[ "$mode" != "--smoke" && "$mode" != "--build-only" && "$mode" != "--full" ]]; then
  echo "Usage: bash scripts/erp-next-local-verify.sh [--smoke|--build-only|--full]" >&2
  exit 2
fi
if [[ "$mode" != "--smoke" ]]; then
  command -v mvn >/dev/null
  command -v npm >/dev/null
  mvn -B -f backend/erp-next/pom.xml verify
  if [[ "$mode" == "--full" ]]; then
    mvn -B -f backend/pom.xml clean verify
  fi
  (cd frontend && npm ci && npm run build:prod)
  if [[ "$mode" == "--build-only" ]]; then exit 0; fi
fi
command -v docker >/dev/null
command -v python >/dev/null
command -v curl >/dev/null
command -v flock >/dev/null
docker compose version >/dev/null
exec 9>/tmp/erp-next-local-smoke.lock
flock -n 9 || { echo "Another local ERP smoke is running"; exit 1; }
python - <<'PY'
import socket
for port in (8089,8090):
    s=socket.socket()
    try:
        s.bind(("127.0.0.1",port))
    except OSError:
        raise SystemExit(f"Port {port} is occupied. Do not run against a live ERP.")
    finally:
        s.close()
PY
project="erp-next-smoke-$(python -c 'import uuid; print(uuid.uuid4().hex[:12])')"
compose_file="$(mktemp "$ROOT/erp-next/.local-smoke-XXXXXX.yaml")"
env_file="$(mktemp "$ROOT/erp-next/.local-smoke-XXXXXX.env")"
cleanup(){
  rc=$?
  trap - EXIT
  if [[ "$rc" != 0 ]]; then docker compose -p "$project" --env-file "$env_file" -f "$compose_file" logs --no-color --tail=100 || true; fi
  docker compose -p "$project" --env-file "$env_file" -f "$compose_file" down -v --remove-orphans || true
  rm -f "$compose_file" "$env_file"
  exit "$rc"
}
trap 'rm -f "$compose_file" "$env_file"' EXIT
python - "$compose_file" "$project" <<'PY'
import sys
from pathlib import Path
s=Path("erp-next/compose.yaml").read_text()
for original in ("erp_next_mysql_data","erp_next_private_files"):
    old="name: "+original
    assert s.count(old)==1, "Unexpected volume definition; stop for review"
    s=s.replace(old,"name: "+sys.argv[2]+"_"+original)
s=s.replace('"${ERP_NEXT_WEB_PORT:-8089}:80"','"127.0.0.1:${ERP_NEXT_WEB_PORT:-8089}:80"')
Path(sys.argv[1]).write_text(s)
PY
export ERP_NEXT_DB_ROOT_PASSWORD="$(python -c 'import secrets; print(secrets.token_hex(24))')"
export ERP_NEXT_DB_PASSWORD="$(python -c 'import secrets; print(secrets.token_hex(24))')"
export ERP_BOOTSTRAP_TOKEN="$(python -c 'import secrets; print(secrets.token_hex(24))')"
export ERP_NEXT_WEB_PORT=8089 ERP_NEXT_API_PORT=8090 ERP_LEGACY_SCAN_ENABLED=false
export ERP_LEGACY_DB_URL="" ERP_LEGACY_DB_USER="" ERP_LEGACY_DB_PASSWORD="" ERP_LEGACY_DB_SCHEMA=server_migration
printf 'ERP_NEXT_WEB_PORT=8089\nERP_NEXT_API_PORT=8090\nERP_LEGACY_SCAN_ENABLED=false\n' > "$env_file"
dc(){ docker compose -p "$project" --env-file "$env_file" -f "$compose_file" "$@"; }
dc config --quiet
trap cleanup EXIT
dc up -d erp-next-mysql
dc run --rm erp-next-schema
dc run --rm erp-next-schema
dc exec -T -e MYSQL_PWD="$ERP_NEXT_DB_PASSWORD" erp-next-mysql mysql -u erp_next -D erp_next -N -e 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL' | grep -qx 21
dc up -d --build
bash scripts/erp-next-bootstrap-smoke.sh
echo "Isolated local schema and API smoke passed; test-only containers and volumes will be removed."
