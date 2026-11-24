#!/usr/bin/env bash
# Internal runner; invoked by test.sh.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"
mode="$1"
echo "Commit: $(git rev-parse HEAD)"
echo "Mode: $mode"
echo "Started: $(date -Is)"
git status --short
for cmd in python3 bash; do command -v "$cmd" >/dev/null || { echo "缺少 $cmd"; exit 1; }; done
shim="$(mktemp -d)"
trap 'rm -rf -- "$shim"' EXIT
ln -s "$(command -v python3)" "$shim/python"
export PATH="$shim:$PATH"
bash -n test.sh deploy.sh
bash scripts/erp-next-local-verify.sh --static
[[ "$mode" != --static ]] || exit 0
for cmd in docker curl flock; do
 command -v "$cmd" >/dev/null || { echo "缺少 $cmd；Ubuntu: sudo apt install curl util-linux（Docker 需自行配置）"; exit 1; }
done
docker compose version
docker info --format 'Docker server: {{.ServerVersion}}'
if [[ "$mode" != --smoke ]]; then
 echo "=== Maven 新版后端测试（容器） ==="
 docker run --rm --mount "type=bind,src=$ROOT/backend,dst=/source,readonly" \
   maven:3.9.9-eclipse-temurin-17 bash -ec 'cp -a /source /tmp/backend; mvn -B -f /tmp/backend/erp-next/pom.xml clean verify'
 if [[ "$mode" == --full ]]; then
   echo "=== Maven 旧版 reactor（容器） ==="
   docker run --rm --mount "type=bind,src=$ROOT/backend,dst=/source,readonly" \
     maven:3.9.9-eclipse-temurin-17 bash -ec 'cp -a /source /tmp/backend; mvn -B -f /tmp/backend/pom.xml clean verify'
 fi
fi
echo "=== Docker 前端生产构建、隔离数据库及 API Smoke ==="
bash scripts/erp-next-local-verify.sh --smoke
echo "自动化检查完成；仍需 Word、浏览器及真实迁移人工验收。"
