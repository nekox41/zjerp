set -euo pipefail

# V4 service-delivery acceptance checks; source this file from the authenticated smoke-test shell.
curl -fsS -X POST http://127.0.0.1:8090/api/business/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectNo\":\"CI-P-NC\",\"name\":\"CI 无合同项目\",\"customerId\":$CUSTOMER_ID}" | grep -q 'CI 无合同项目'

curl -fsS -X POST "http://127.0.0.1:8090/api/business/employees/$EMP_ID/qualifications" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"qualificationName":"CI 资质","expiresOn":"2099-12-31"}' | grep -q 'CI 资质'

curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/issues" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"CI 整改项\",\"ownerEmployeeId\":$EMP_ID}" | grep -q 'CI 整改项'

WORK=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/work-packages" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"packageNo\":\"CI-WP-1\",\"title\":\"CI 服务工作包\",\"assigneeEmployeeId\":$EMP_ID}")
WORK_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$WORK")

curl -fsS -X POST "http://127.0.0.1:8090/api/business/work-packages/$WORK_ID/records" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"CI 现场记录","content":"现场检查完成"}' | grep -q 'CI 现场记录'

curl -fsS -X PUT "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"active"}' >/dev/null

curl -fsS -X POST "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/amendments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"amendmentNo":"CI-AM-1","title":"CI 合同变更","changeSummary":"变更测试","revisedAmount":1200}' | grep -q 'CI-AM-1'
