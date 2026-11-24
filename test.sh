#!/usr/bin/env bash
# WSL2/Ubuntu: Docker supplies Java, Maven and Node. Never touch deployment volumes.
set -Eeuo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
mode="${1:---full}"
case "$mode" in --full|--new-only|--smoke|--static) ;; *) echo "Usage: bash test.sh [--full|--new-only|--smoke|--static]"; exit 2;; esac
mkdir -p test-results
log="$(mktemp "$ROOT/test-results/test-$(date +%Y%m%d-%H%M%S)-XXXXXX.log")"
echo "测试日志: $log"
# Preserve the real exit status (including tee failures).
exec > >(tee -a "$log") 2>&1
trap 'rc=$?; if ((rc==0)); then echo "TEST RESULT: PASS ($mode)"; else echo "TEST RESULT: FAIL ($mode), exit=$rc"; fi; echo "日志: $log"; exit "$rc"' EXIT
echo "Commit: $(git rev-parse HEAD)"
echo "Mode: $mode"
echo "Started: $(date -Is)"
git status --short
for cmd in python3 bash; do command -v "$cmd" >/dev/null || { echo "缺少 $cmd"; exit 1; }; done
shim="$(mktemp -d)"
trap 'rc=$?; rm -rf -- "$shim"; if ((rc==0)); then echo "TEST RESULT: PASS ($mode)"; else echo "TEST RESULT: FAIL ($mode), exit=$rc"; fi; echo "日志: $log"; exit "$rc"' EXIT
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
