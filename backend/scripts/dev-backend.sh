#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env.dev ]]; then set -a; source .env.dev; set +a; fi
: "${TOKEN_SECRET:?Set TOKEN_SECRET in .env.dev or the environment}"
# Build every reactor module: spring-boot:run against a single module can reuse stale installed dependencies.
mvn -B clean package -DskipTests
exec java -jar zjerp-admin/target/zjerp-admin.jar --spring.profiles.active=dev "$@"
