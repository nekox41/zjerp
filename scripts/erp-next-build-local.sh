#!/usr/bin/env bash
# Host-side build for the disposable trial stack. Do not delete dependency caches.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
for cmd in java javac mvn node npm sha256sum; do
  command -v "$cmd" >/dev/null || { echo "缺少本地构建工具 $cmd。需要 JDK 17、Maven、Node.js 22 和 npm 10。" >&2; exit 1; }
done
javac_version="$(javac -version 2>&1)"
[[ "$javac_version" =~ ^javac[[:space:]]17(\.|$) ]] || { echo "需要 JDK 17，当前 $javac_version" >&2; exit 1; }
maven_version="$(mvn -v)"
if ! grep -Eq 'Java version: 17([.,[:space:]]|$)' <<< "$maven_version"; then
  echo "Maven 未使用 JDK 17；请检查 JAVA_HOME。" >&2
  exit 1
fi
node_version="$(node --version)"
npm_version="$(npm --version)"
[[ "$node_version" =~ ^v22\. && "$npm_version" =~ ^10\. ]] || { echo "需要 Node.js 22 / npm 10，当前 $node_version / $npm_version" >&2; exit 1; }

echo "=== 后端：本地 Maven（保留 ~/.m2/repository） ==="
(cd "$ROOT/backend/erp-next" && mvn -B -ntp -DskipTests clean package)
test -s "$ROOT/backend/erp-next/target/erp-next-api-0.1.0-SNAPSHOT.jar"

echo "=== 前端：本地 npm / Vite ==="
(
  cd "$ROOT/frontend"
  # Include manifest, lockfile, project npm settings, tool versions and platform.
  fingerprint="$(
    {
      sha256sum package.json package-lock.json
      if [[ -f .npmrc ]]; then sha256sum .npmrc; fi
      printf '%s\n' "$node_version" "$npm_version"
      node -p 'process.platform + "/" + process.arch'
    } | sha256sum | cut -d ' ' -f 1
  )"
  marker=node_modules/.erp-next-dependency-fingerprint
  if [[ -f "$marker" && "$(cat "$marker")" == "$fingerprint" && -x node_modules/.bin/vite ]]; then
    echo "[npm] 依赖未变化，复用 node_modules，跳过安装"
  else
    echo "[npm] 首次构建或依赖变更，按锁文件安装，优先使用本地 npm 缓存"
    rm -f -- "$marker"
    npm ci --include=dev --prefer-offline --no-audit --no-fund
    printf '%s\n' "$fingerprint" > "$marker"
  fi
  npm run build:prod
  test -s dist/next.html
)
echo "本地构建完成：backend/erp-next/target/erp-next-api-0.1.0-SNAPSHOT.jar 与 frontend/dist/"
