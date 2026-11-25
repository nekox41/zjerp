#!/usr/bin/env bash
# ERP Next: isolated, repeatable Codespaces/local Docker quickstart.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/erp-next/.env.local"
COMPOSE_FILE="$ROOT/erp-next/compose.yaml"
cd "$ROOT"

usage() {
  cat <<'EOF'
Usage: bash start.sh [start|status|logs|stop|help]
  start   (default) generate local secrets once, build and run ERP Next
  status  show container status
  logs    follow application logs
  stop    stop containers, preserving database and private files
All commands operate ONLY on erp-next/compose.yaml.
For WSL2 legacy-data acceptance and migration, use bash deploy.sh instead.
EOF
}
command_name="${1:-start}"
if [[ "$command_name" == help || "$command_name" == -h || "$command_name" == --help ]]; then usage; exit 0; fi
if [[ ! "$command_name" =~ ^(start|status|logs|stop)$ ]]; then usage >&2; exit 2; fi
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker with Compose v2 is required. In Codespaces, use an image with Docker-in-Docker support." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Cannot contact Docker daemon. Check Docker-in-Docker/container permissions." >&2
  exit 1
fi

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import secrets; print(secrets.token_hex(32))'
  else
    echo "openssl or python3 is required for safe secret generation" >&2
    exit 1
  fi
}
if [[ "$command_name" == start && ! -f "$ENV_FILE" ]]; then
  umask 077
  [[ ! -e "$ENV_FILE" ]] || { echo "Refusing to overwrite existing environment file" >&2; exit 1; }
  temp_file="$(mktemp "$ROOT/erp-next/.env.local.tmp.XXXXXXXX")"
  trap 'rm -f -- "${temp_file:-}"' EXIT
  {
    echo "# Generated locally by start.sh. NEVER commit or publish this file."
    printf 'ERP_NEXT_DB_ROOT_PASSWORD=%s\n' "$(generate_secret)"
    printf 'ERP_NEXT_DB_PASSWORD=%s\n' "$(generate_secret)"
    printf 'ERP_BOOTSTRAP_TOKEN=%s\n' "$(generate_secret)"
    echo 'ERP_NEXT_API_PORT=8090'
    echo 'ERP_NEXT_WEB_PORT=8089'
  } > "$temp_file"
  chmod 600 "$temp_file"
  if [[ -e "$ENV_FILE" ]]; then
    echo "Concurrent startup created the environment file; keeping existing credentials." >&2
  else
    mv -n -- "$temp_file" "$ENV_FILE"
    echo "Created private secrets in erp-next/.env.local (saved for future starts)."
  fi
  rm -f -- "$temp_file"
  trap - EXIT
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing erp-next/.env.local; run 'bash start.sh' once to initialize." >&2
  exit 1
fi
# Secrets are never echoed in full; Compose reads only the ERP Next environment file.
compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
case "$command_name" in
  status) compose ps -a; exit ;;
  logs) compose logs --tail=150 -f erp-next-api erp-next-web erp-next-schema; exit ;;
  stop) compose down --remove-orphans; echo "Containers stopped; database and attachment volumes are preserved."; exit ;;
esac
compose config --quiet
echo "[1/4] Building ERP Next API and Web images..."
compose build erp-next-api erp-next-web
echo "[2/4] Starting MySQL and waiting for health..."
if ! compose up -d --wait --wait-timeout 180 erp-next-mysql; then
  compose ps -a >&2
  compose logs --tail=100 erp-next-mysql >&2
  exit 1
fi
# Do not migrate while the application can still write to its database.
compose stop erp-next-web erp-next-api
echo "[3/4] Running Flyway migration (live output; do not interrupt active SQL)..."
if ! compose up --no-deps --force-recreate --exit-code-from erp-next-schema erp-next-schema; then
  echo "Flyway failed. Existing database and private files are preserved. Do not delete volumes or repair migration history blindly." >&2
  compose ps -a >&2
  compose logs --tail=100 erp-next-schema >&2
  exit 1
fi
echo "[4/4] Starting API and Web after successful migration..."
# Avoid running Flyway a second time; the preceding command must succeed first.
if ! compose up -d --no-deps --wait --wait-timeout 180 erp-next-api ||
   ! compose up -d --no-deps --wait --wait-timeout 60 erp-next-web; then
  compose ps -a >&2
  compose logs --tail=100 erp-next-api erp-next-web >&2
  exit 1
fi
echo "Waiting for frontend and backend health (up to 180 seconds)..."
healthy=0
for ((i=0;i<90;i++)); do
  if compose exec -T erp-next-api curl -fsS http://127.0.0.1:8090/actuator/health >/dev/null 2>&1 &&
     compose exec -T erp-next-web sh -c "wget -q -O - http://127.0.0.1/next.html | grep -q next-app" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 2
done
if (( healthy != 1 )); then
  echo "ERP Next health check failed; diagnostics:" >&2
  compose ps -a >&2
  compose logs --tail=100 erp-next-schema erp-next-api erp-next-web >&2
  exit 1
fi
compose ps
WEB_PORT="$(sed -n 's/^ERP_NEXT_WEB_PORT=//p' "$ENV_FILE" | tail -n 1)"
WEB_PORT="${WEB_PORT:-8089}"
echo
echo "ERP Next is ready!"
echo "Local URL: http://localhost:${WEB_PORT}/next.html"
if [[ -n "${CODESPACE_NAME:-}" && -n "${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-}" ]]; then
  echo "Codespaces URL: https://${CODESPACE_NAME}-${WEB_PORT}.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}/next.html"
  echo "Keep forwarded port ${WEB_PORT} PRIVATE; do not expose the bootstrap/login page publicly."
fi
echo "Commands: bash start.sh status | logs | stop"
