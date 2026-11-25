#!/usr/bin/env bash
# Shared local/manual Actions smoke journey. Requires an EMPTY isolated test stack.
set -euo pipefail
: "${ERP_BOOTSTRAP_TOKEN:?Set temporary test bootstrap token}"
# Manual Actions retain their isolated default ports; local Compose supplies
# the actual randomly allocated loopback ports. Never accept arbitrary hosts.
for port in "${ERP_SMOKE_API_PORT:-8090}" "${ERP_SMOKE_WEB_PORT:-8089}"; do
  [[ "$port" =~ ^[1-9][0-9]{0,4}$ ]] && ((port<=65535)) || { echo "Invalid smoke port"; exit 1; }
done
export SMOKE_API_URL="http://127.0.0.1:${ERP_SMOKE_API_PORT:-8090}"
export SMOKE_WEB_URL="http://127.0.0.1:${ERP_SMOKE_WEB_PORT:-8089}"
export NO_PROXY="127.0.0.1,localhost,${NO_PROXY:-}"
export no_proxy="127.0.0.1,localhost,${no_proxy:-}"
echo "Smoke API: $SMOKE_API_URL; web: $SMOKE_WEB_URL"
for i in $(seq 1 60); do
  curl -fsS ${SMOKE_API_URL}/actuator/health >/dev/null && break
  sleep 2
done
curl -fsS ${SMOKE_API_URL}/actuator/health
for i in $(seq 1 30); do
  curl -fsS ${SMOKE_WEB_URL}/next.html -o /tmp/erp-next-page.html && grep -q 'next-app' /tmp/erp-next-page.html && break
  sleep 2
done
grep -q 'next-app' /tmp/erp-next-page.html || { echo 'New frontend entry missing:'; head -c 1000 /tmp/erp-next-page.html; exit 1; }
curl -fsS ${SMOKE_WEB_URL}/next-api/bootstrap/status -o /tmp/erp-next-bootstrap.json
grep -q '"initialized":false' /tmp/erp-next-bootstrap.json || { cat /tmp/erp-next-bootstrap.json; exit 1; }
curl -fsS -X POST ${SMOKE_WEB_URL}/next-api/bootstrap/admin \
  -H 'Content-Type: application/json' -H "X-Bootstrap-Token: $ERP_BOOTSTRAP_TOKEN" \
  -d '{"username":"admin","displayName":"CI Admin","password":"ci-only-password-123"}' >/dev/null
LOGIN=$(curl -fsS -X POST ${SMOKE_WEB_URL}/next-api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"ci-only-password-123"}')
TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$LOGIN")
test -n "$TOKEN"
curl -fsS ${SMOKE_WEB_URL}/next-api/auth/me -H "Authorization: Bearer $TOKEN" | grep -q 'CI Admin'

ORG=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/organizations -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI 业务部"}')
ORG_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$ORG")
EMP=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/employees -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"employeeCode\":\"CI-001\",\"fullName\":\"CI 员工\",\"organizationId\":$ORG_ID,\"employeeType\":\"engineer\"}")
EMP_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$EMP")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/employees/$EMP_ID/qualifications" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"qualificationName":"CI 项目负责人资格","expiresOn":"2099-12-31"}' >/dev/null
CUSTOMER=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/customers -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"customerCode\":\"CI-C-001\",\"name\":\"CI 客户\",\"ownerEmployeeId\":$EMP_ID}")
CUSTOMER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$CUSTOMER")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/contacts" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI 联系人","phone":"123456"}' >/dev/null
CONTRACT=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/contracts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"contractNo\":\"CI-CT-001\",\"title\":\"CI 合同\",\"customerId\":$CUSTOMER_ID,\"amount\":1000}")
CONTRACT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$CONTRACT")
PROJECT=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/projects -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"projectNo\":\"CI-P-001\",\"name\":\"CI 项目\",\"customerId\":$CUSTOMER_ID,\"contractId\":$CONTRACT_ID,\"managerEmployeeId\":$EMP_ID}")
PROJECT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PROJECT")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/milestones" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"CI 里程碑"}' >/dev/null
curl -fsS "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/milestones" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 里程碑'
MILESTONE_ID=$(curl -fsS "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/milestones" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/milestones/$MILESTONE_ID/complete" -H "Authorization: Bearer $TOKEN" | grep -q 'completed'
curl -fsS "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 合同'
curl -fsS "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 项目'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/members" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"employeeId\":$EMP_ID,\"role\":\"负责人\"}" >/dev/null
curl -fsS "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/members" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 员工'
source scripts/erp-next-service-smoke.sh
source scripts/erp-next-partner-maintenance-smoke.sh
source scripts/erp-next-report-smoke.sh
source scripts/erp-next-inspection-library-smoke.sh
source scripts/erp-next-expense-smoke.sh
source scripts/erp-next-invoice-smoke.sh
source scripts/erp-next-dashboard-smoke.sh
if curl -fsS "${SMOKE_API_URL}/api/business/customers" >/dev/null; then echo 'Unauthenticated business request unexpectedly succeeded'; exit 1; fi

# New installations have no legacy DB configured. Detection must be nonblocking and admin-only.
curl -fsS "${SMOKE_API_URL}/api/migration/source-status" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert json.load(sys.stdin)["state"]=="not_configured"'
if curl -fsS "${SMOKE_API_URL}/api/migration/source-status" >/dev/null; then echo 'Unauthenticated migration discovery unexpectedly succeeded'; exit 1; fi
