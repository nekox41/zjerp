set -euo pipefail

# Partner, contract-project scope, project leadership and maintenance acceptance checks.
PARTNER=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/partners \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"partnerCode":"CI-PARTNER-001","name":"CI 介绍合作方","partnerType":"introducer","contactName":"CI 联系人"}')
PARTNER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PARTNER")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/partners" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"partnerId\":$PARTNER_ID,\"sharePercent\":20,\"notes\":\"CI 合作分成\"}" | grep -q '20'
curl -fsS "http://127.0.0.1:8090/api/business/partners/$PARTNER_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 介绍合作方'
curl -fsS "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/partners" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 介绍合作方'

PARTNER_2=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/partners \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"partnerCode":"CI-PARTNER-002","name":"CI 第二合作方","partnerType":"introducer"}')
PARTNER_2_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PARTNER_2")
OVER_SHARE=$(curl -sS -o /tmp/erp-partner-over-share.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/partners" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"partnerId\":$PARTNER_2_ID,\"sharePercent\":90}")
test "$OVER_SHARE" = "400"

# A project may exist before a contract is linked; a later contract can cover it.
UNCOVERED=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectNo\":\"CI-P-MAINT-1\",\"name\":\"CI 维保项目\",\"customerId\":$CUSTOMER_ID}")
UNCOVERED_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UNCOVERED")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/projects" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectId\":$UNCOVERED_ID,\"relationType\":\"renewal\"}" | grep -q 'renewal'
curl -fsS "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/contracts" -H "Authorization: Bearer $TOKEN" | grep -q 'CI-CT-001'

# Project manager and technical lead must be distinct qualified engineers.
TECH=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"employeeCode\":\"CI-TECH-001\",\"fullName\":\"CI 技术负责人\",\"employeeType\":\"engineer\"}")
TECH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$TECH")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/employees/$TECH_ID/qualifications" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"qualificationName":"CI 技术负责人资格","expiresOn":"2099-12-31"}' >/dev/null
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/leadership" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"managerEmployeeId\":$EMP_ID,\"technicalLeadEmployeeId\":$TECH_ID}" | grep -q 'CI 技术负责人'
SAME_LEAD=$(curl -sS -o /tmp/erp-project-same-lead.json -w '%{http_code}' -X PUT "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/leadership" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"managerEmployeeId\":$EMP_ID,\"technicalLeadEmployeeId\":$EMP_ID}")
test "$SAME_LEAD" = "400"

# Switch this contract to a maintenance contract and track project status and visits.
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"serviceType":"maintenance","status":"active"}' >/dev/null
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/maintenance" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"currentContractId\":$CONTRACT_ID,\"honeybeeContractUploaded\":true,\"honeybeeProjectCreated\":true,\"projectMaterialsCompleted\":true,\"publicCardStatus\":\"preparing\",\"notes\":\"CI 维保状态\"}" | grep -q 'preparing'
VISIT=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/maintenance-visits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"contractId\":$CONTRACT_ID,\"visitDate\":\"2026-11-23\",\"employeeIds\":[$EMP_ID,$TECH_ID],\"notes\":\"CI 维护到场\"}")
VISIT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$VISIT")
curl -fsS "http://127.0.0.1:8090/api/business/projects/$UNCOVERED_ID/maintenance" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 技术负责人'

# Contract originals, public cards and project materials use the private file store.
printf 'CI maintenance attachment.\\n' > /tmp/erp-maintenance-ci.txt
UPLOAD=$(curl -fsS -X POST http://127.0.0.1:8090/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-maintenance-ci.txt;type=text/plain')
ATTACH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UPLOAD")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID/files" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"fileId\":$ATTACH_ID,\"fileType\":\"maintenance_card\",\"title\":\"CI 维保公示卡\"}" | grep -q 'maintenance_card'
curl -fsS "http://127.0.0.1:8090/api/business/contracts/$CONTRACT_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 维保公示卡'
