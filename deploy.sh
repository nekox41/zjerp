#!/usr/bin/env bash
# Persistent WSL2 acceptance environment; reuse the existing ERP MySQL when available.
set -Eeuo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
action="${1:-start}"
shift || true
usage(){ cat <<'EOF'
Usage: bash deploy.sh [start|fresh|status|logs|stop|legacy-import SQL|legacy-shell|archive|plan|preview|apply PLAN_SHA256|reconcile|backup]
默认 start：首次安装或重启同一开发版本，优先复用旧版 zjerp/mysql 容器；不存在时创建唯一 MySQL。
fresh：明确重建新版测试库及私有附件；旧测试数据先备份，server/旧附件/MySQL 实例不变。不同候选版本必须 fresh，不做中间版本升级。
ERP_MYSQL_CONTAINER 可显式指定已有 MySQL 容器（首次部署前）。
已有数据库实例内仅初始化 erp_next；不会创建、导入或迁移 server。
ERP_TRIAL_DIR 可指定独立状态目录；默认 ~/.local/share/zjerp-trial。
首次启动前可设置 ERP_NEXT_WEB_PORT / ERP_NEXT_API_PORT（默认 8089/8090）。
legacy-import SQL：在同一 MySQL 实例按需创建空 server 并导入（仅允许单 schema SQL）。
legacy-shell：手工操作 server（仅在独立新建的实例允许）。
archive：将旧库及状态目录 legacy-files 中的旧附件归档。
plan：生成空白人工审核映射 review.json。
preview：停止新版 Web/API 后演练，生成唯一凭据。
apply SHA256：执行明确确认的同一计划（停止新版服务）。
reconcile：核对目标映射与附件；完成后 start 恢复 Web/API。
backup：停止新版 Web/API，创建数据库和附件成对备份。
stop：停止容器，保留全部数据和凭据。
EOF
}
case "$action" in help|-h|--help) usage; exit 0;; start|fresh|status|logs|stop|legacy-import|legacy-shell|archive|plan|preview|apply|reconcile|backup) ;; *) usage >&2; exit 2;; esac
case "$action" in legacy-import|apply) [[ $# == 1 ]] || { usage >&2; exit 2; };; *) [[ $# == 0 ]] || { usage >&2; exit 2; };; esac
for cmd in docker python3 flock curl; do command -v "$cmd" >/dev/null || { echo "缺少 $cmd" >&2; exit 1; }; done
docker compose version >/dev/null
docker info >/dev/null
source "$ROOT/scripts/erp-next-images.sh"
STATE="$(python3 -c 'from pathlib import Path;import sys; print(Path(sys.argv[1]).expanduser().resolve())' "${ERP_TRIAL_DIR:-$HOME/.local/share/zjerp-trial}")"
# State must not be stored in the Git checkout or a Windows-mounted filesystem.
case "$STATE/" in "$ROOT/"*|/mnt/[a-zA-Z]/*) echo "请在 WSL Linux 文件系统的仓库外设置 ERP_TRIAL_DIR" >&2; exit 1;; esac
if [[ "$action" != start && "$action" != fresh && ! -f "$STATE/.env" ]]; then echo "先执行 bash deploy.sh"; exit 1; fi
[[ "$STATE" != / && "$STATE" != "$HOME" && "$STATE" != /tmp && "$STATE" != /var && "$STATE" != /usr ]] || { echo "请指定专用状态目录"; exit 1; }
mkdir -p "$STATE"
[[ -O "$STATE" ]] || { echo "状态目录不属于当前用户"; exit 1; }
chmod 700 "$STATE"
exec 9>"$STATE/.lock"
flock -n 9 || { echo "另一个部署/迁移命令正在运行"; exit 1; }
# Generate local credentials only once. Never use inherited production credentials.
web="${ERP_NEXT_WEB_PORT:-8089}"; api="${ERP_NEXT_API_PORT:-8090}"
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN ERP_LEGACY_ROOT_PASSWORD ERP_LEGACY_DB_PASSWORD ERP_LEGACY_LOADER_PASSWORD ERP_TRIAL_FILES ERP_TRIAL_UID ERP_TRIAL_GID ERP_NEXT_WEB_PORT ERP_NEXT_API_PORT COMPOSE_PROJECT_NAME COMPOSE_FILE COMPOSE_PROFILES; do unset "$key"; done
if [[ ! -e "$STATE/.env" ]]; then
 [[ "$action" == start || "$action" == fresh ]] || exit 1
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
if [[ "$action" == fresh && -f "$STATE/schema-init-pending" && ! -e "$STATE/files" ]]; then mkdir -m 700 "$STATE/files"; fi
# Only source the private configuration generated above, not a user-supplied SQL or arbitrary env file.
set -a; source "$STATE/.env"; set +a
[[ "$TRIAL_ID" =~ ^[0-9a-f]{32}$ && "$ERP_TRIAL_FILES" == "$STATE/files" && "$ERP_TRIAL_UID" == "$(id -u)" && "$ERP_TRIAL_GID" == "$(id -g)" ]] || { echo "状态目录与创建者不一致"; exit 1; }
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN ERP_LEGACY_ROOT_PASSWORD ERP_LEGACY_DB_PASSWORD ERP_LEGACY_LOADER_PASSWORD; do [[ "${!key}" =~ ^[0-9a-f]{64}$ ]] || { echo "无效本地凭据 $key"; exit 1; }; done
project="erp-next-trial-$TRIAL_ID"
# The binding is pinned to the state directory, not rediscovered on every restart.
# No automatic switch between instances after data has been initialized.
dc(){ docker compose -p "$project" --profile managed-db --env-file "$STATE/.env" -f "$ROOT/erp-next/compose.trial.yaml" "$@"; }
binding="$STATE/mysql-binding"
if [[ -f "$binding" ]]; then
 [[ ! -L "$binding" ]] || { echo "无效数据库绑定"; exit 1; }
 db_mode="$(sed -n '1p' "$binding")"; db_name="$(sed -n '2p' "$binding")"
 if [[ -n "${ERP_MYSQL_CONTAINER:-}" && "$ERP_MYSQL_CONTAINER" != "$db_name" ]]; then echo "已绑定数据库，不允许自动切换"; exit 1; fi
else
 [[ "$action" == start || "$action" == fresh ]] || { echo "先启动 ERP"; exit 1; }
 # Previous versions may have created an isolated copy. Preserve it and require explicit backup.
 if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=legacy-mysql")" ]]; then
  echo "检测到旧脚本创建的独立旧库容器；请先备份其 SQL，再使用新的 ERP_TRIAL_DIR。原数据不会删除。" >&2
  exit 1
 fi
 existing="$(dc ps -aq erp-next-mysql)"
 if [[ -n "$existing" || -f "$STATE/files/.erp-next-trial.json" ]]; then
  db_mode=managed; db_name=erp-next-mysql
  [[ -z "${ERP_MYSQL_CONTAINER:-}" ]] || { echo "已有新版数据，不允许自动切换 MySQL"; exit 1; }
 else
  requested="${ERP_MYSQL_CONTAINER:-}"
  if [[ -z "$requested" ]]; then
   mapfile -t candidates < <(docker ps -a --filter 'label=com.docker.compose.project=zjerp' --filter 'label=com.docker.compose.service=mysql' --format '{{.Names}}')
   if (("${#candidates[@]}" > 1)); then echo "发现多个旧版 MySQL，请设置 ERP_MYSQL_CONTAINER"; exit 1; fi
   requested="${candidates[0]:-}"
  fi
  if [[ -n "$requested" ]]; then
   db_mode=shared
   db_name="$(docker inspect --type container -f '{{.Name}}' "$requested")"; db_name="${db_name#/}"
  else
   db_mode=managed; db_name=erp-next-mysql
  fi
 fi
 printf '%s\n%s\n' "$db_mode" "$db_name" > "$binding"
fi
[[ "$db_mode" == managed || "$db_mode" == shared ]] && [[ "$db_name" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || { echo "无效数据库绑定"; exit 1; }
db_container(){
 if [[ "$db_mode" == managed ]]; then dc ps -q erp-next-mysql; else docker inspect --type container -f '{{.Id}}' "$db_name"; fi
}
root_sql(){
 local id; id="$(db_container)"; [[ -n "$id" ]] || return 1
 # Use the existing container's configured root password locally, never print it.
 docker exec -i "$id" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -u root -N' 
}
nextsql(){
 local id; id="$(db_container)"; [[ -n "$id" ]] || return 1
 docker exec -i -e MYSQL_PWD="$ERP_NEXT_DB_PASSWORD" "$id" mysql --protocol=TCP -h 127.0.0.1 -u erp_next erp_next -N "$@"
}
legacyroot(){ root_sql; }
loader(){
 local id; id="$(db_container)"
 docker exec "$@" -e MYSQL_PWD="$ERP_LEGACY_LOADER_PASSWORD" "$id" mysql --protocol=TCP -h 127.0.0.1 -u erp_legacy_loader server
}
# Explicit initialization receipt, bound to the pinned server and trial.
pending="$STATE/schema-init-pending"
build_stamp="$STATE/schema-build"
verify_pending(){
 [[ -f "$pending" && ! -L "$pending" ]] || return 1
 local server
 server="$(root_sql <<< 'SELECT @@server_uuid;')"
 [[ "$(cat "$pending")" == "$TRIAL_ID:$server" ]] || { echo "初始化标识与数据库实例不一致" >&2; return 1; }
}
mark_pending(){
 local server
 server="$(root_sql <<< 'SELECT @@server_uuid;')"
 [[ "$server" =~ ^[0-9a-fA-F-]{36}$ ]] || return 1
 printf '%s:%s\n' "$TRIAL_ID" "$server" > "$pending"
}
prepare_mysql(){
 if [[ "$db_mode" == managed ]]; then
  dc up -d --wait --wait-timeout 180 erp-next-mysql
 else
  [[ "$(docker inspect -f '{{.State.Running}}' "$db_name")" == true ]] || { echo "已选旧版 MySQL 未运行，请先启动 $db_name；不会另建实例"; exit 1; }
  # Create only the new project's network and migration container, without starting it.
  dc create --no-deps erp-next-schema
  local network="${project}_erp-next-internal" id
  id="$(db_container)"
  if [[ "$(docker inspect -f "{{with index .NetworkSettings.Networks \"$network\"}}{{.NetworkID}}{{end}}" "$id")" == "" ]]; then
   docker network connect --alias erp-next-mysql "$network" "$id"
  fi
 fi
 local version
 version="$(root_sql <<< 'SELECT VERSION();')" || { echo "无法使用现有 MySQL 容器内管理员凭据；请核对 MYSQL_ROOT_PASSWORD，不会重置旧密码"; exit 1; }
 [[ "$version" =~ ^8\. ]] || { echo "仅支持 MySQL 8.x（当前 $version），不会升级旧实例"; exit 1; }
 # Never adopt a pre-existing erp_next database without our persisted identity.
 local exists
 exists="$(root_sql <<< "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='erp_next';")"
 if [[ -e "$pending" ]]; then
  [[ "$action" == fresh ]] || { echo "初始化尚未完成，请使用 bash deploy.sh fresh 重新部署测试库" >&2; exit 1; }
  verify_pending
 elif [[ -f "$STATE/files/.erp-next-trial.json" ]]; then
  verify_marker
 elif [[ "$exists" == 1 && "$db_mode" == shared ]]; then
  echo "共享实例已有 erp_next，但没有本部署标识；拒绝自动接管或清库，请人工核对" >&2; exit 1
 fi
 # Scoped users, no changes to old account credentials or old tables. CREATE USER
 # fails safely on unexpected pre-existing names rather than resetting passwords.
 if [[ ! -f "$STATE/mysql-users-ready" ]]; then
  # Before creating the schema, check dedicated account names to avoid partial provisioning.
  local conflicts
  conflicts="$(root_sql <<< "SELECT COUNT(*) FROM mysql.user WHERE User='erp_legacy_readonly' OR (User='erp_next' AND '$db_mode'='shared');")"
  [[ "$conflicts" == 0 ]] || { echo "已有专用数据库账号同名，拒绝覆盖或重置；请人工核对"; exit 1; }
  if [[ "$db_mode" == shared ]]; then
   root_sql <<SQL
CREATE DATABASE erp_next CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'erp_next'@'%' IDENTIFIED BY '$ERP_NEXT_DB_PASSWORD';
GRANT ALL PRIVILEGES ON erp_next.* TO 'erp_next'@'%';
SQL
  fi
  root_sql <<SQL
CREATE USER 'erp_legacy_readonly'@'%' IDENTIFIED BY '$ERP_LEGACY_DB_PASSWORD';
GRANT SELECT, SHOW VIEW ON server.* TO 'erp_legacy_readonly'@'%';
SQL
  touch "$STATE/mysql-users-ready"
 fi
 echo "MySQL：$db_mode / $(db_container)；版本 $version"
}
ensure_databases(){
 dc config --quiet
 echo "[1/3] 初始化新版 MySQL（不要求旧库存在）"
 prepare_mysql
 pause_app
 local fingerprint count server marker previous
 fingerprint="$(git rev-parse HEAD):$(sha256sum "$ROOT/erp-next/db/migration/V1__initial_schema.sql" | cut -d ' ' -f1)"
 marker="$STATE/files/.erp-next-trial.json"
 count="$(root_sql <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='erp_next';")"
 if [[ "$action" == fresh ]]; then
   if [[ -e "$pending" ]]; then
     verify_pending
   elif [[ -f "$marker" ]]; then
     verify_marker
     backup_target
     mark_pending
   else
     [[ "$count" == 0 && -z "$(find "$STATE/files" -mindepth 1 -maxdepth 1 -print -quit)" ]] || { echo "非空新版库缺少隔离标识，拒绝重建" >&2; exit 1; }
     mark_pending
   fi
   # Preserve prior test attachments/mappings, never delete legacy files or archives.
   previous="$(mktemp -d "$STATE/backups-reset-XXXXXX")"
   mv -- "$STATE/files" "$previous/files"
   mkdir -m 700 "$STATE/files"
   for item in review.json latest-preview schema-build last-successful-commit; do
     if [[ -e "$STATE/$item" ]]; then mv -- "$STATE/$item" "$previous/$item"; fi
   done
   echo "旧测试附件及映射已保留：$previous"
   root_sql <<'SQL'
DROP DATABASE IF EXISTS erp_next;
CREATE DATABASE erp_next CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
 elif [[ -f "$marker" ]]; then
   verify_marker
   [[ -f "$build_stamp" && "$(cat "$build_stamp")" == "$fingerprint" ]] || { echo "开发候选版本变更：请执行 bash deploy.sh fresh 全新部署，不支持中间版本升级" >&2; exit 1; }
 else
   [[ "$count" == 0 && -z "$(find "$STATE/files" -mindepth 1 -maxdepth 1 -print -quit)" ]] || { echo "非空新版库缺少隔离标识，拒绝接管" >&2; exit 1; }
   mark_pending
 fi
 echo "[2/3] 初始化或校验最终新版数据库结构（不导入旧业务数据）"
 dc up --no-deps --force-recreate --exit-code-from erp-next-schema erp-next-schema
 if [[ -e "$pending" ]]; then
   verify_pending
   server="$(nextsql <<< 'SELECT @@server_uuid;')"
   [[ "$(nextsql <<< 'SELECT COUNT(*) FROM erp_identity;')" == 0 ]] || { echo "全新目标不应含账户" >&2; exit 1; }
   nextsql <<SQL
CREATE TABLE erp_trial_isolation_marker(id INT PRIMARY KEY,trial_id CHAR(32) NOT NULL,server_uuid CHAR(36) NOT NULL);
INSERT INTO erp_trial_isolation_marker VALUES(1,'$TRIAL_ID','$server');
SQL
   python3 - "$marker" "$TRIAL_ID" "$server" <<'PY'
import json,sys
from pathlib import Path
with Path(sys.argv[1]).open("x") as f:json.dump({"format":"erp-next-trial-v1","trialId":sys.argv[2],"serverUuid":sys.argv[3]},f)
PY
   verify_marker
   printf '%s\n' "$fingerprint" > "$build_stamp"
   rm -- "$pending"
 fi
 verify_marker
}
ensure_legacy(){
 [[ "$db_mode" == managed ]] || { echo "共享旧版实例的 server 不允许 SQL 导入或修改；请使用原数据只读迁移"; exit 1; }
 verify_marker
 local count
 count="$(root_sql <<< "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='server';")"
 if [[ "$count" == 0 ]]; then root_sql <<< 'CREATE DATABASE server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'; fi
 if [[ ! -f "$STATE/legacy-loader-ready" ]]; then
  root_sql <<SQL
CREATE USER 'erp_legacy_loader'@'%' IDENTIFIED BY '$ERP_LEGACY_LOADER_PASSWORD';
GRANT ALL PRIVILEGES ON server.* TO 'erp_legacy_loader'@'%';
SQL
  touch "$STATE/legacy-loader-ready"
 fi
}
verify_marker(){
 local server row
 server="$(nextsql <<< 'SELECT @@server_uuid;')"
 row="$(nextsql <<< "SELECT CONCAT(trial_id,':',server_uuid) FROM erp_trial_isolation_marker WHERE id=1;")"
 [[ "$row" == "$TRIAL_ID:$server" ]] || { echo "数据库试迁移标识不符"; exit 1; }
 python3 - "$STATE/files/.erp-next-trial.json" "$TRIAL_ID" "$server" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1])
if p.is_symlink() or json.loads(p.read_text())!={"format":"erp-next-trial-v1","trialId":sys.argv[2],"serverUuid":sys.argv[3]}:raise SystemExit("附件试迁移标识不符")
PY
}
migration_image(){
 erp_image_check python:3.11-slim
 docker build --build-arg "ERP_IMAGE_PREFIX=$ERP_IMAGE_PREFIX" -q -t zjerp-trial-migration:local -f "$ROOT/scripts/Dockerfile.erp-next-migration" "$ROOT/scripts" >/dev/null
}
pytrial(){
 migration_image
 docker run --rm --network "${project}_erp-next-internal" --user "$ERP_TRIAL_UID:$ERP_TRIAL_GID" \
   --mount "type=bind,src=$STATE,dst=/state" --mount "type=bind,src=$ROOT/scripts,dst=/scripts,readonly" \
   -e ERP_NEXT_DB_PASSWORD -e ERP_LEGACY_DB_PASSWORD -e PYTHONDONTWRITEBYTECODE=1 \
   zjerp-trial-migration:local python "$@"
}
pause_app(){ dc stop erp-next-web erp-next-api; }
backup_target(){
 verify_marker
 pause_app
 local dest
 mkdir -p "$STATE/backups"
 dest="$(mktemp -d "$STATE/backups/target-$(date -u +%Y%m%dT%H%M%S)-XXXXXX")"
 echo "正在创建数据库和附件成对备份：$dest"
 docker exec "$(db_container)" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -u root --single-transaction --no-tablespaces --routines --triggers --events --default-character-set=utf8mb4 erp_next' > "$dest/database.sql"
 [[ -s "$dest/database.sql" ]] || { echo "数据库备份为空"; exit 1; }
 tar -C "$STATE/files" -czf "$dest/files.tar.gz" .
 (cd "$dest" && sha256sum database.sql files.tar.gz > SHA256SUMS && sha256sum -c SHA256SUMS)
 if [[ -f "$build_stamp" ]]; then cut -d : -f1 "$build_stamp" > "$dest/commit"; else printf 'unknown-pre-release\n' > "$dest/commit"; fi
 touch "$dest/COMPLETE"
 echo "备份已完成：$dest（敏感数据，请勿分享）"
}
actor(){ nextsql <<< "SELECT id FROM erp_identity WHERE username='admin';"; }
migration(){
 verify_marker
 [[ ! -e "$pending" && -f "$build_stamp" && "$(cat "$build_stamp")" == "$(git rev-parse HEAD):$(sha256sum "$ROOT/erp-next/db/migration/V1__initial_schema.sql" | cut -d ' ' -f1)" ]] || { echo "请先部署当前完整候选版，再进行旧库试迁移" >&2; exit 1; }
 pause_app
 local id
 id="$(actor)"; [[ "$id" =~ ^[1-9][0-9]*$ ]] || { echo "试迁移管理员不存在"; exit 1; }
 pytrial /scripts/erp-next-legacy-import.py "$1" --archive /state/archive --plan /state/review.json --host erp-next-mysql --user erp_next --actor "$id" --files-root /state/files --isolated-trial "${@:2}"
}
case "$action" in
 start|fresh)
   erp_build_images
   ensure_databases
   echo "[3/3] 构建并启动新版 API 和 Web"
   dc up -d --no-deps --build --wait --wait-timeout 180 erp-next-api
   dc up -d --no-deps --build --wait --wait-timeout 60 erp-next-web
   echo "新版已启动；旧 server 仅在迁移页面检测，不自动迁移。"
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
    req=urllib.request.Request(base+"/bootstrap/admin",data=json.dumps({"username":"admin","displayName":"验收管理员","password":password}).encode(),headers={"Content-Type":"application/json","X-Bootstrap-Token":os.environ["ERP_BOOTSTRAP_TOKEN"]})
    with urllib.request.urlopen(req,timeout=30) as r:
        if r.status!=201:raise SystemExit("管理员初始化失败")
PY
   [[ "$(actor)" =~ ^[1-9][0-9]*$ ]] || { echo "缺少试迁移管理员"; exit 1; }
   dc ps -a
   echo "访问：http://localhost:$ERP_NEXT_WEB_PORT/next.html"
   echo "账号：admin；初始密码文件：$STATE/admin-password"
   echo "旧版附件副本目录：$STATE/legacy-files"
   echo "试迁移环境目录：$STATE（不要上传其中的密码/真实资料）"
   ;;
 status) dc ps -a; echo "状态目录：$STATE";;
 logs) dc logs --tail=200 -f erp-next-api erp-next-web erp-next-schema;;
 stop) dc stop erp-next-web erp-next-api erp-next-schema; if [[ "$db_mode" == managed ]]; then dc stop erp-next-mysql; fi; echo "服务已停止；共享旧版 MySQL 不会停止，所有数据保留";;
 legacy-import)
   [[ -f "$1" ]] || { echo "SQL 文件不存在"; exit 1; }
   # Only the isolated legacy loader can execute SQL; never use the new database or old production host.
   ensure_legacy
   count="$(legacyroot <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='server';")"
   [[ "$count" == 0 ]] || { echo "旧库非空，拒绝重复导入；请人工核查"; exit 1; }
   docker exec -i -e MYSQL_PWD="$ERP_LEGACY_LOADER_PASSWORD" "$(db_container)" mysql --protocol=TCP -h 127.0.0.1 --binary-mode -u erp_legacy_loader server < "$1"
   echo "旧库导入完成，表数量：$(legacyroot <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='server';")"
   ;;
 legacy-shell) ensure_legacy; loader;;
 archive)
   verify_marker
   [[ "$(root_sql <<< "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='server' AND TABLE_TYPE='BASE TABLE';")" != 0 ]] || { echo "旧库为空或不存在，跳过归档；请先导入 SQL"; exit 1; }
   if [[ "$db_mode" == shared && "${ERP_LEGACY_SOURCE_FROZEN:-}" != yes ]]; then
     echo "请先停止旧应用写入、备份并复制附件，再设置 ERP_LEGACY_SOURCE_FROZEN=yes 执行 archive。不会自动停止旧服务。"; exit 1
   fi
   [[ ! -e "$STATE/archive" && ! -e "$STATE/archive.partial" ]] || { echo "已存在归档，请保留并使用独立状态目录重做"; exit 1; }
   pytrial /scripts/erp-next-legacy-export.py export --host erp-next-mysql --user erp_legacy_readonly --schema server --confirm-server-read-only --namespace "trial-$TRIAL_ID" --files /state/legacy-files --output /state/archive
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
   backup_target
   migration apply --preview-receipt "/state/$receipt" --confirm-plan-sha256 "$1"
   echo "迁移完成；请执行 reconcile，再 start"
   ;;
 reconcile) migration reconcile; echo "核对通过；执行 bash deploy.sh 恢复页面";;
 backup) backup_target; echo "Web/API 已停止；执行 bash deploy.sh 恢复";;
esac
