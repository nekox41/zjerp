#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
out="${1:-release}"
mkdir -p "$out/web" "$out/db"
cp backend/erp-next/target/erp-next-api-0.1.0-SNAPSHOT.jar "$out/app.jar"
cp -a frontend/dist/. "$out/web/"
rm -f "$out/web/index.html"
# Package the one canonical, complete database schema.
cp erp-next/db/schema.sql "$out/db/schema.sql"
cp scripts/release-preview/deploy.sh scripts/release-preview/preview-deploy.py "$out/"
cp scripts/release-preview/README.md "$out/README.md"
git rev-parse HEAD > "$out/COMMIT"
(cd "$out"; find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
