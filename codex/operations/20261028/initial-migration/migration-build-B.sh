#!/bin/sh
set -u
cd /vol2/zjerp-migration/source/zjerp_backend-master
docker compose build frontend > /vol2/zjerp-migration/build-frontend.log 2>&1
result=$?
echo "$result" > /vol2/zjerp-migration/build-frontend.exit
exit "$result"
