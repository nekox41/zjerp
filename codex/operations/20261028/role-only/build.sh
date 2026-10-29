#!/bin/sh
set -eu
cd /vol2/zjerp-migration/role-only/source
python3 /vol2/zjerp-migration/role-only/prepare-build.py
docker build -f zjerp_backend-master/Dockerfile.role-only -t zjerp-backend:role-only-20261028 zjerp_backend-master > /vol2/zjerp-migration/role-only/backend-build.log 2>&1
docker build -f zjerp_frontend-master/Dockerfile.role-only -t zjerp-frontend:role-only-20261028 zjerp_frontend-master > /vol2/zjerp-migration/role-only/frontend-build.log 2>&1
echo success > /vol2/zjerp-migration/role-only/build-result.txt
