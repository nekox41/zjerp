#!/usr/bin/env bash
# Disposable ERP Next local development stack. Never point at production.
set -Eeuo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
action="${1:-start}"
usage(){ cat <<'EOF'
Usage: bash deploy.sh [start|fresh|status|logs|stop]
默认 start（fresh 为同义命令）：每次删除新版测试库全部记录与私有附件，重新初始化并部署。
仅使用独立测试 PostgreSQL，不检测、连接或迁移旧生产 server。
不删除其他项目容器、共享数据库、历史归档或旧版附件；Docker 构建缓存保留。
测试端口仅绑定 127.0.0.1，账号 admin，密码 123456；禁止用于生产。
ERP_TRIAL_DIR 可指定专用状态目录（默认 ~/.local/share/zjerp-trial）。
首次创建状态目录时可设置 ERP_NEXT_WEB_PORT / ERP_NEXT_API_PORT（默认 8089/8090）。
EOF
}
case "$action" in help|-h|--help) usage; exit 0;; start|fresh|status|logs|stop) ;; *) usage >&2; exit 2;; esac
[[ $# -le 1 ]] || { usage >&2; exit 2; }
for cmd in docker python3 flock curl; do command -v "$cmd" >/dev/null || { echo "缺少 $cmd" >&2; exit 1; }; done
docker compose version >/dev/null
docker info >/dev/null
source "$ROOT/scripts/erp-next-images.sh"
STATE="$(python3 -c 'from pathlib import Path;import sys;print(Path(sys.argv[1]).expanduser().resolve())' "${ERP_TRIAL_DIR:-$HOME/.local/share/zjerp-trial}")"
case "$STATE/" in "$ROOT/"*|/mnt/[a-zA-Z]/*) echo "请设置仓库之外的 Linux 专用状态目录" >&2; exit 1;; esac
[[ "$STATE" != / && "$STATE" != "$HOME" && "$STATE" != /tmp && "$STATE" != /var && "$STATE" != /usr && "$STATE" != /home && "$STATE" != /opt ]] || { echo "拒绝非专用状态目录" >&2; exit 1; }
if [[ "$action" != start && "$action" != fresh && ! -f "$STATE/.env" ]]; then echo "先执行 bash deploy.sh"; exit 1; fi
mkdir -p "$STATE"
[[ -O "$STATE" && ! -L "$STATE/.lock" ]] || { echo "无效状态目录" >&2; exit 1; }
chmod 700 "$STATE"
exec 9>"$STATE/.lock"
flock -n 9 || { echo "另一个测试部署命令正在运行" >&2; exit 1; }
web="${ERP_NEXT_WEB_PORT:-8089}"; api="${ERP_NEXT_API_PORT:-8090}"
if [[ -n "${ERP_MYSQL_CONTAINER:-}" ]]; then echo "测试部署不允许绑定外部数据库" >&2; exit 1; fi
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN ERP_TRIAL_FILES ERP_TRIAL_UID ERP_TRIAL_GID ERP_NEXT_WEB_PORT ERP_NEXT_API_PORT COMPOSE_PROJECT_NAME COMPOSE_FILE COMPOSE_PROFILES TRIAL_ID; do unset "$key"; done
if [[ ! -e "$STATE/.env" ]]; then
 python3 - "$STATE" "$web" "$api" <<'PY'
from pathlib import Path
import os,secrets,sys,uuid
p=Path(sys.argv[1]); ports=[int(x) for x in sys.argv[2:]]
if len(set(ports))!=2 or any(not 1024<=x<=65535 for x in ports):raise SystemExit("端口必须不同且在1024–65535")
if any(x in str(p) for x in ("\n","\r","'","$","\\")):raise SystemExit("状态路径包含不支持字符")
if os.getuid()==0:raise SystemExit("不要 sudo 运行脚本")
if any(x.name!=".lock" for x in p.iterdir()):raise SystemExit("新状态目录必须为空")
(p/"files").mkdir(mode=0o700)
values={k:secrets.token_hex(32) for k in ("ERP_NEXT_DB_ROOT_PASSWORD","ERP_NEXT_DB_PASSWORD","ERP_BOOTSTRAP_TOKEN")}
values.update(TRIAL_ID=uuid.uuid4().hex,ERP_TRIAL_FILES=str(p/"files"),ERP_TRIAL_UID=str(os.getuid()),ERP_TRIAL_GID=str(os.getgid()),ERP_NEXT_WEB_PORT=str(ports[0]),ERP_NEXT_API_PORT=str(ports[1]))
with (p/".env").open("x") as f:
    for k,v in values.items():f.write(k+"='"+v+"'\n")
PY
fi
[[ -f "$STATE/.env" && ! -L "$STATE/.env" && -O "$STATE/.env" && ! -L "$STATE/files" && ! -L "$STATE/mysql-binding" && ! -L "$STATE/admin-password" ]] || { echo "拒绝无效状态文件" >&2; exit 1; }
# Generated state only; reject shell syntax and unknown values before sourcing.
python3 - "$STATE/.env" <<'PY'
import re,sys
from pathlib import Path
allowed={"ERP_NEXT_DB_ROOT_PASSWORD","ERP_NEXT_DB_PASSWORD","ERP_BOOTSTRAP_TOKEN","ERP_LEGACY_ROOT_PASSWORD","ERP_LEGACY_DB_PASSWORD","ERP_LEGACY_LOADER_PASSWORD","TRIAL_ID","ERP_TRIAL_FILES","ERP_TRIAL_UID","ERP_TRIAL_GID","ERP_NEXT_WEB_PORT","ERP_NEXT_API_PORT"}
seen=set()
for line in Path(sys.argv[1]).read_text().splitlines():
    m=re.fullmatch(r"([A-Z_]+)='([^'$\x5c\r\n]+)'",line)
    if not m or m[1] not in allowed or m[1] in seen:raise SystemExit("无效测试配置")
    seen.add(m[1])
if not allowed-{"ERP_LEGACY_ROOT_PASSWORD","ERP_LEGACY_DB_PASSWORD","ERP_LEGACY_LOADER_PASSWORD"}<=seen:raise SystemExit("缺少测试配置")
PY
set -a; source "$STATE/.env"; set +a
[[ "$TRIAL_ID" =~ ^[0-9a-f]{32}$ && "$ERP_TRIAL_FILES" == "$STATE/files" && "$ERP_TRIAL_UID" == "$(id -u)" && "$ERP_TRIAL_GID" == "$(id -g)" ]] || { echo "状态目录与创建者不一致" >&2; exit 1; }
for key in ERP_NEXT_DB_ROOT_PASSWORD ERP_NEXT_DB_PASSWORD ERP_BOOTSTRAP_TOKEN; do [[ "${!key}" =~ ^[0-9a-f]{64}$ ]] || { echo "无效测试凭据 $key" >&2; exit 1; }; done
for port in "$ERP_NEXT_WEB_PORT" "$ERP_NEXT_API_PORT"; do [[ "$port" =~ ^[1-9][0-9]{3,4}$ ]] && ((port>=1024 && port<=65535)) || { echo "无效测试端口" >&2; exit 1; }; done
[[ "$ERP_NEXT_WEB_PORT" != "$ERP_NEXT_API_PORT" ]] || exit 1
project="erp-next-pg-trial-$TRIAL_ID"
old_project="erp-next-trial-$TRIAL_ID"
dc(){ docker compose -p "$project" --profile managed-db --env-file "$STATE/.env" -f "$ROOT/erp-next/compose.trial.yaml" "$@"; }
case "$action" in
 status) dc ps -a; exit;;
 logs) dc logs --tail=200 -f erp-next-api erp-next-web erp-next-schema; exit;;
 stop) dc stop erp-next-web erp-next-api erp-next-schema erp-next-postgres; exit;;
esac
echo "=== 本地测试全新部署：旧新版业务数据与附件将清空 ==="
erp_build_images
dc config --quiet
dc build erp-next-api erp-next-web
# The old MySQL trial has a different Compose project. Retire only its own containers;
# do not delete its volume (it could contain an imported legacy server schema).
for svc in erp-next-web erp-next-api erp-next-schema erp-next-mysql; do
 mapfile -t old_ids < <(docker ps -aq --filter "label=com.docker.compose.project=$old_project" --filter "label=com.docker.compose.service=$svc")
 for old_id in "${old_ids[@]}"; do
  [[ "$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$old_id")" == "$old_project" ]] || exit 1
  docker rm -f "$old_id"
 done
done
# Only this dedicated PostgreSQL trial project owns this volume.
volume="${project}_trial_postgres"
if docker volume inspect "$volume" >/dev/null 2>&1; then
 [[ "$(docker volume inspect -f '{{index .Labels "com.docker.compose.project"}}' "$volume")" == "$project" && "$(docker volume inspect -f '{{index .Labels "com.docker.compose.volume"}}' "$volume")" == trial_postgres ]] || { echo "测试数据库卷归属校验失败" >&2; exit 1; }
fi
dc down -v --remove-orphans
echo "[1/3] 重建独立 PostgreSQL 测试数据库与附件"
if [[ -e "$STATE/files" ]]; then
 [[ -d "$STATE/files" && ! -L "$STATE/files" && -O "$STATE/files" ]] || { echo "附件目录校验失败" >&2; exit 1; }
 find "$STATE/files" -xdev -mindepth 1 -delete
else
 mkdir -m 700 "$STATE/files"
fi
for item in schema-init-pending schema-build last-successful-commit latest-preview review.json; do rm -f -- "$STATE/$item"; done
printf '123456\n' > "$STATE/admin-password"
dc up -d --wait --wait-timeout 180 erp-next-postgres
echo "[2/3] 初始化最终 Schema"
dc up --no-deps --force-recreate --exit-code-from erp-next-schema erp-next-schema
echo "[3/3] 启动新 API 与 Web"
dc up -d --no-deps --force-recreate --wait --wait-timeout 180 erp-next-api
dc up -d --no-deps --force-recreate --wait --wait-timeout 90 erp-next-web
python3 - "$ERP_NEXT_WEB_PORT" <<'PY'
import json,os,sys,urllib.request
base="http://127.0.0.1:"+sys.argv[1]+"/next-api"
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
with opener.open(base+"/bootstrap/status",timeout=15) as r: status=json.load(r)
if status.get("initialized") is not False:raise SystemExit("预期全新数据库，拒绝覆盖现有管理员")
def post(path,data,headers={}):
    req=urllib.request.Request(base+path,data=json.dumps(data).encode(),headers={"Content-Type":"application/json",**headers})
    with opener.open(req,timeout=30) as r:return r.status,json.load(r)
status,_=post("/bootstrap/admin",{"username":"admin","displayName":"管理员","password":"123456"},{"X-Bootstrap-Token":os.environ["ERP_BOOTSTRAP_TOKEN"]})
if status!=201:raise SystemExit("管理员初始化失败")
status,login=post("/auth/login",{"username":"admin","password":"123456"})
if status!=200 or not login.get("token"):raise SystemExit("测试管理员登录验证失败")
PY
dc ps -a
echo "访问：http://localhost:$ERP_NEXT_WEB_PORT/next.html"
echo "测试账号：admin / 123456（仅限本机测试）"
echo "全新部署完成。未执行旧生产库检测或迁移。"
