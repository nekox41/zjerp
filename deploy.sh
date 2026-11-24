#!/usr/bin/env bash
# Persistent WSL2 acceptance environment with isolated old DB and migration target.
set -Eeuo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
action="${1:-start}"
shift || true
usage(){ cat <<'EOF'
Usage: bash deploy.sh [start|status|logs|stop|legacy-import SQL|legacy-shell|archive|plan|preview|apply PLAN_SHA256|reconcile]
默认 start：创建或继续运行持久化验收环境（不是正式生产）。
ERP_TRIAL_DIR 可指定独立状态目录；默认 ~/.local/share/zjerp-trial。
首次启动前可设置 ERP_NEXT_WEB_PORT / ERP_NEXT_API_PORT（默认 8089/8090）。
legacy-import SQL：仅导入空的离线旧库 server_migration（不接受含 USE server 的整库脚本）。
legacy-shell：手工操作隔离旧库（仅该 schema）。
archive：将旧库及状态目录 legacy-files 中的旧附件归档。
plan：生成空白人工审核映射 review.json。
preview：停止新版 Web/API 后演练，生成唯一凭据。
apply SHA256：执行明确确认的同一计划（停止新版服务）。
reconcile：核对目标映射与附件；完成后 start 恢复 Web/API。
stop：停止容器，保留全部数据和凭据。不提供删库命令。
EOF
}
case "$action" in help|-h|--help) usage; exit 0;; start|status|logs|stop|legacy-import|legacy-shell|archive|plan|preview|apply|reconcile) ;; *) usage >&2; exit 2;; esac
case "$action" in legacy-import|apply) [[ $# == 1 ]] || { usage >&2; exit 2; };; *) [[ $# == 0 ]] || { usage >&2; exit 2; };; esac
for cmd in docker python3 flock curl; do command -v "$cmd" >/dev/null || { echo "缺少 $cmd" >&2; exit 1; }; done
docker compose version >/dev/null
docker info >/dev/null
STATE="$(python3 -c 'from pathlib import Path;import sys; print(Path(sys.argv[1]).expanduser().resolve())' "${ERP_TRIAL_DIR:-$HOME/.local/share/zjerp-trial}")"
# State must not be stored in the Git checkout or a Windows-mounted filesystem.
case "$STATE/" in "$ROOT/"*|/mnt/[a-zA-Z]/*) echo "请在 WSL Linux 文件系统的仓库外设置 ERP_TRIAL_DIR" >&2; exit 1;; esac
if [[ "$action" != start && ! -f "$STATE/.env" ]]; then echo "先执行 bash deploy.sh"; exit 1; fi
mkdir -p "$STATE"
chmod 700 "$STATE"
exec 9>"$STATE/.lock"
flock -n 9 || { echo "另一个部署/迁移命令正在运行"; exit 1; }
# Generate local credentials only once. Never use inherited production credentials.
web="${ERP_NEXT_WEB_PORT:-8089}"; api="${ERP_NEXT_API_PORT:-8090}"
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN ERP_LEGACY_ROOT_PASSWORD ERP_LEGACY_DB_PASSWORD ERP_LEGACY_LOADER_PASSWORD ERP_TRIAL_FILES ERP_TRIAL_UID ERP_TRIAL_GID ERP_NEXT_WEB_PORT ERP_NEXT_API_PORT COMPOSE_PROJECT_NAME COMPOSE_FILE COMPOSE_PROFILES; do unset "$key"; done
if [[ ! -e "$STATE/.env" ]]; then
 [[ "$action" == start ]] || exit 1
 python3 - "$STATE" "$web" "$api" <<'PY'
from pathlib import Path
import os,secrets,sys,uuid
p=Path(sys.argv[1]); ports=[int(x) for x in sys.argv[2:]]
if len(set(ports))!=2 or any(not 1024<=x<=65535 for x in ports):raise SystemExit("端口必须不同且在1024–65535")
if any(x in str(p) for x in ("\n","\r","'","$","\\")):raise SystemExit("状态路径包含不支持字符")
if os.getuid()==0:raise SystemExit("不要 sudo 运行脚本；请配置当前用户 Docker 权限")
if any(x.name not in (".lock",) for x in p.iterdir()):raise SystemExit("新状态目录必须为空，避免覆盖已有数据")
for d in ("files","legacy-files"): (p/d).mkdir(mode=0o700)
trial=uuid.uuid4().hex
values={k:secrets.token_hex(32) for k in ("ERP_NEXT_DB_ROOT_PASSWORD","ERP_NEXT_DB_PASSWORD","ERP_BOOTSTRAP_TOKEN","ERP_LEGACY_ROOT_PASSWORD","ERP_LEGACY_DB_PASSWORD","ERP_LEGACY_LOADER_PASSWORD")}
values.update(TRIAL_ID=trial,ERP_TRIAL_FILES=str(p/"files"),ERP_TRIAL_UID=str(os.getuid()),ERP_TRIAL_GID=str(os.getgid()),ERP_NEXT_WEB_PORT=str(ports[0]),ERP_NEXT_API_PORT=str(ports[1]))
with (p/"admin-password").open("x") as f:f.write(secrets.token_hex(24)+"\n")
with (p/".env").open("x") as f:
    for k,v in values.items():f.write(k+"='"+v+"'\n")
PY
fi
[[ ! -L "$STATE/.env" && ! -L "$STATE/files" && ! -L "$STATE/legacy-files" ]] || { echo "拒绝符号链接状态目录"; exit 1; }
# Only source the private configuration generated above, not a user-supplied SQL or arbitrary env file.
set -a; source "$STATE/.env"; set +a
[[ "$TRIAL_ID" =~ ^[0-9a-f]{32}$ && "$ERP_TRIAL_FILES" == "$STATE/files" && "$ERP_TRIAL_UID" == "$(id -u)" && "$ERP_TRIAL_GID" == "$(id -g)" ]] || { echo "状态目录与创建者不一致"; exit 1; }
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN ERP_LEGACY_ROOT_PASSWORD ERP_LEGACY_DB_PASSWORD ERP_LEGACY_LOADER_PASSWORD; do [[ "${!key}" =~ ^[0-9a-f]{64}$ ]] || { echo "无效本地凭据 $key"; exit 1; }; done
project="erp-next-trial-$TRIAL_ID"
dc(){ docker compose -p "$project" --env-file "$STATE/.env" -f "$ROOT/erp-next/compose.trial.yaml" "$@"; }
nextsql(){ dc exec -T erp-next-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u erp_next erp_next -N' "$@"; }
legacyroot(){ dc exec -T legacy-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root server_migration -N' "$@"; }
loader(){ dc exec "$@" -e MYSQL_PWD="$ERP_LEGACY_LOADER_PASSWORD" legacy-mysql mysql -u legacy_loader server_migration; }
ensure_databases(){
 dc config --quiet
 dc up -d --wait erp-next-mysql legacy-mysql
 dc run --rm erp-next-schema
 # Only create the target marker on an empty new target. Never repair a mismatch silently.
 server="$(nextsql <<< 'SELECT @@server_uuid;')"
 count="$(nextsql <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='erp_next' AND table_name='erp_trial_isolation_marker';")"
 marker="$STATE/files/.erp-next-trial.json"
 if [[ "$count" == 0 && ! -e "$marker" ]]; then
   [[ "$(nextsql <<< 'SELECT COUNT(*) FROM erp_identity;')" == 0 ]] || { echo "目标非空且无试迁移标识，停止"; exit 1; }
   [[ -z "$(find "$STATE/files" -mindepth 1 -maxdepth 1 -print -quit)" ]] || { echo "附件目录非空且无标识"; exit 1; }
   nextsql <<SQL
CREATE TABLE erp_trial_isolation_marker(id INT PRIMARY KEY,trial_id CHAR(32) NOT NULL,server_uuid CHAR(36) NOT NULL);
INSERT INTO erp_trial_isolation_marker VALUES(1,'$TRIAL_ID',@@server_uuid);
SQL
   python3 - "$marker" "$TRIAL_ID" "$server" <<'PY'
import json,sys
from pathlib import Path
with Path(sys.argv[1]).open("x") as f:json.dump({"format":"erp-next-trial-v1","trialId":sys.argv[2],"serverUuid":sys.argv[3]},f)
PY
 fi
 verify_marker
 legacyroot <<SQL
CREATE USER IF NOT EXISTS 'legacy_readonly'@'%' IDENTIFIED BY '$ERP_LEGACY_DB_PASSWORD';
GRANT SELECT, SHOW VIEW ON server_migration.* TO 'legacy_readonly'@'%';
CREATE USER IF NOT EXISTS 'legacy_loader'@'%' IDENTIFIED BY '$ERP_LEGACY_LOADER_PASSWORD';
GRANT ALL PRIVILEGES ON server_migration.* TO 'legacy_loader'@'%';
SQL
}
verify_marker(){
 local server row
 server="$(nextsql <<< 'SELECT @@server_uuid;')"
 row="$(nextsql <<< 'SELECT CONCAT(trial_id,":",server_uuid) FROM erp_trial_isolation_marker WHERE id=1;')"
 [[ "$row" == "$TRIAL_ID:$server" ]] || { echo "数据库试迁移标识不符"; exit 1; }
 python3 - "$STATE/files/.erp-next-trial.json" "$TRIAL_ID" "$server" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1])
if p.is_symlink() or json.loads(p.read_text())!={"format":"erp-next-trial-v1","trialId":sys.argv[2],"serverUuid":sys.argv[3]}:raise SystemExit("附件试迁移标识不符")
PY
}
migration_image(){
 docker build -q -t zjerp-trial-migration:local -f "$ROOT/scripts/Dockerfile.erp-next-migration" "$ROOT/scripts" >/dev/null
}
pytrial(){
 migration_image
 docker run --rm --network "${project}_erp-next-internal" --user "$ERP_TRIAL_UID:$ERP_TRIAL_GID" \
   --mount "type=bind,src=$STATE,dst=/state" --mount "type=bind,src=$ROOT/scripts,dst=/scripts,readonly" \
   -e ERP_NEXT_DB_PASSWORD -e ERP_LEGACY_DB_PASSWORD -e PYTHONDONTWRITEBYTECODE=1 \
   zjerp-trial-migration:local python "$@"
}
pause_app(){ dc stop erp-next-web erp-next-api; }
actor(){ nextsql <<< "SELECT id FROM erp_identity WHERE username='trial-admin';"; }
migration(){
 verify_marker
 pause_app
 local id
 id="$(actor)"; [[ "$id" =~ ^[1-9][0-9]*$ ]] || { echo "试迁移管理员不存在"; exit 1; }
 pytrial /scripts/erp-next-legacy-import.py "$1" --archive /state/archive --plan /state/review.json --host erp-next-mysql --user erp_next --actor "$id" --files-root /state/files --isolated-trial "${@:2}"
}
case "$action" in
 start)
   ensure_databases
   dc up -d --build erp-next-api erp-next-web
   ready=false
   for ((i=0;i<150;i++)); do
     if curl -fsS "http://127.0.0.1:$ERP_NEXT_API_PORT/actuator/health" >/dev/null 2>&1 &&
        curl -fsS "http://127.0.0.1:$ERP_NEXT_WEB_PORT/next-api/bootstrap/status" >/dev/null 2>&1; then ready=true; break; fi
     sleep 2
   done
   if [[ "$ready" != true ]]; then dc ps -a; dc logs --tail=100 erp-next-schema erp-next-api erp-next-web; exit 1; fi
   python3 - "$STATE" "$ERP_NEXT_WEB_PORT" <<'PY'
import json,os,sys,urllib.request
from pathlib import Path
base="http://127.0.0.1:"+sys.argv[2]+"/next-api"
with urllib.request.urlopen(base+"/bootstrap/status",timeout=15) as r:status=json.load(r)
if not status["initialized"]:
    password=(Path(sys.argv[1])/"admin-password").read_text().strip()
    req=urllib.request.Request(base+"/bootstrap/admin",data=json.dumps({"username":"trial-admin","displayName":"验收管理员","password":password}).encode(),headers={"Content-Type":"application/json","X-Bootstrap-Token":os.environ["ERP_BOOTSTRAP_TOKEN"]})
    with urllib.request.urlopen(req,timeout=30) as r:
        if r.status!=201:raise SystemExit("管理员初始化失败")
PY
   [[ "$(actor)" =~ ^[1-9][0-9]*$ ]] || { echo "缺少试迁移管理员"; exit 1; }
   dc ps -a
   echo "访问：http://localhost:$ERP_NEXT_WEB_PORT/next.html"
   echo "账号：trial-admin；初始密码文件：$STATE/admin-password"
   echo "旧版附件副本目录：$STATE/legacy-files"
   echo "试迁移环境目录：$STATE（不要上传其中的密码/真实资料）"
   ;;
 status) dc ps -a; echo "状态目录：$STATE";;
 logs) dc logs --tail=200 -f erp-next-api erp-next-web erp-next-schema;;
 stop) dc stop; echo "服务已停止；所有数据保留";;
 legacy-import)
   [[ -f "$1" ]] || { echo "SQL 文件不存在"; exit 1; }
   # Only the isolated legacy loader can execute SQL; never use the new database or old production host.
   count="$(legacyroot <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='server_migration';")"
   [[ "$count" == 0 ]] || { echo "旧库非空，拒绝重复导入；请人工核查"; exit 1; }
   loader -T < "$1"
   echo "旧库导入完成，表数量：$(legacyroot <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='server_migration';")"
   ;;
 legacy-shell) loader;;
 archive)
   [[ ! -e "$STATE/archive" && ! -e "$STATE/archive.partial" ]] || { echo "已存在归档，请保留并使用独立状态目录重做"; exit 1; }
   pytrial /scripts/erp-next-legacy-export.py export --host legacy-mysql --user legacy_readonly --schema server_migration --namespace "trial-$TRIAL_ID" --files /state/legacy-files --output /state/archive
   pytrial /scripts/erp-next-legacy-export.py verify --archive /state/archive
   ;;
 plan) pytrial /scripts/erp-next-legacy-import.py new-plan --archive /state/archive --plan /state/review.json; echo "请编辑 $STATE/review.json，按实际业务核对";;
 preview)
   receipt="preview-$(date -u +%Y%m%dT%H%M%S)-$(python3 -c 'import secrets;print(secrets.token_hex(4))').json"
   migration preview --preview-receipt "/state/$receipt"
   printf '%s\n' "$receipt" > "$STATE/latest-preview"
   echo "预演完成，Web/API 保持停止；请核对 planSha256。"
   ;;
 apply)
   [[ "$1" =~ ^[0-9a-f]{64}$ && -f "$STATE/latest-preview" ]] || { echo "先成功 preview，再提供完整 planSha256"; exit 1; }
   receipt="$(cat "$STATE/latest-preview")"
   [[ "$receipt" =~ ^preview-[0-9]{8}T[0-9]{6}-[0-9a-f]{8}\.json$ ]] || exit 1
   migration apply --preview-receipt "/state/$receipt" --confirm-plan-sha256 "$1"
   echo "迁移完成；请执行 reconcile，再 start"
   ;;
 reconcile) migration reconcile; echo "核对通过；执行 bash deploy.sh 恢复页面";;
esac
