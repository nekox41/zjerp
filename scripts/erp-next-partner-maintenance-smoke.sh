set -euo pipefail
year=$(TZ=Asia/Shanghai date +%Y)

# Partner, contract-project scope, project leadership and maintenance acceptance checks.
# New contracts create their matching project in the same transaction.
UNLINKED_RESERVATION=$(reserve_business_number other contract)
UNLINKED_RESERVATION_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UNLINKED_RESERVATION")
UNLINKED_DRAFT=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/contracts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"numberReservationId\":\"$UNLINKED_RESERVATION_ID\",\"title\":\"CI 待分配项目的合同草稿\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"other\"}")
UNLINKED_DRAFT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UNLINKED_DRAFT")
UNLINKED_DRAFT_PROJECT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["projectId"])' <<< "$UNLINKED_DRAFT")
UNLINKED_DRAFT_NO=$(python -c 'import json,sys; print(json.load(sys.stdin)["contract_no"])' <<< "$UNLINKED_DRAFT")
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNLINKED_DRAFT_PROJECT_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; p=json.load(sys.stdin); assert p["project_no"]==sys.argv[1]' "$UNLINKED_DRAFT_NO"
# New contracts are linked to their matching project atomically.
NO_PROJECT_ACTIVE=$(curl -sS -o /tmp/erp-contract-no-project.json -w '%{http_code}' -X PUT "${SMOKE_API_URL}/api/business/contracts/$UNLINKED_DRAFT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"active"}')
test "$NO_PROJECT_ACTIVE" = "200"
# Creating a contract directly in an active state is still rejected.
NO_PROJECT_CREATE=$(curl -sS -o /tmp/erp-active-create.json -w '%{http_code}' -X POST ${SMOKE_API_URL}/api/business/contracts \
 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"contractNo\":\"CI-ACTIVE-CREATE\",\"title\":\"无项目生效\",\"customerId\":$CUSTOMER_ID,\"status\":\"active\"}")
test "$NO_PROJECT_CREATE" = "400"
BAD_DATES=$(curl -sS -o /tmp/erp-contract-dates.json -w '%{http_code}' -X PUT "${SMOKE_API_URL}/api/business/contracts/$UNLINKED_DRAFT_ID" \
 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"startsOn":"2028-01-01","endsOn":"2027-12-31"}')
test "$BAD_DATES" = "400"


PARTNER=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/partners \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"partnerCode":"CI-PARTNER-001","name":"CI 介绍合作方","partnerType":"introducer","contactName":"CI 联系人"}')
PARTNER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PARTNER")
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/partners/$PARTNER_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"CI 介绍合作方已更新","phone":"13800000000","status":"active"}' | grep -q 'CI 介绍合作方已更新'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/partners" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"partnerId\":$PARTNER_ID,\"sharePercent\":20,\"notes\":\"CI 合作分成\"}" | grep -q '20'
curl -fsS "${SMOKE_API_URL}/api/business/partners/$PARTNER_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 介绍合作方已更新'
curl -fsS "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/partners" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 介绍合作方'

PARTNER_2=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/partners \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"partnerCode":"CI-PARTNER-002","name":"CI 第二合作方","partnerType":"introducer"}')
PARTNER_2_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PARTNER_2")
OVER_SHARE=$(curl -sS -o /tmp/erp-partner-over-share.json -w '%{http_code}' -X POST "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/partners" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"partnerId\":$PARTNER_2_ID,\"sharePercent\":90}")
test "$OVER_SHARE" = "400"
UNLINKED=$(curl -fsS -X DELETE "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/partners/$PARTNER_ID" -H "Authorization: Bearer $TOKEN")
echo "$UNLINKED" | grep -q '"signing_source":"direct"'
echo "$UNLINKED" | grep -q '"partner_share_percent":0'
PARTNER_LINKS=$(curl -fsS "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/partners" -H "Authorization: Bearer $TOKEN")
test "$PARTNER_LINKS" = "[]"


# Contact profile changes include primary-contact promotion and removal.
CI_CONTACT=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/contacts" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"CI 待编辑联系人","phone":"111111"}')
CI_CONTACT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$CI_CONTACT")
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/contacts/$CI_CONTACT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"CI 已更新联系人","positionName":"负责人","phone":"222222","email":"ci@example.invalid","isPrimary":true}' | grep -q 'CI 已更新联系人'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/contacts" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; rows=json.load(sys.stdin); assert sum(bool(x["is_primary"]) for x in rows)==1'
curl -fsS -X DELETE "${SMOKE_API_URL}/api/business/contacts/$CI_CONTACT_ID" -H "Authorization: Bearer $TOKEN" >/dev/null
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/contacts" -H "Authorization: Bearer $TOKEN" | grep -qv 'CI 已更新联系人'

# A project may exist before a contract is linked; a later contract can cover it.
UNCOVERED_RESERVATION=$(reserve_business_number maintenance project)
UNCOVERED_RESERVATION_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UNCOVERED_RESERVATION")
UNCOVERED=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"numberReservationId\":\"$UNCOVERED_RESERVATION_ID\",\"name\":\"CI 维保项目\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"maintenance\"}")
UNCOVERED_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UNCOVERED")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/projects" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectId\":$UNCOVERED_ID,\"relationType\":\"renewal\"}" | grep -q 'renewal'
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/contracts" -H "Authorization: Bearer $TOKEN" | grep -q "$CONTRACT_NO"
# Both directions must retain the same identifiers for workspace navigation.
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; p=json.load(sys.stdin); assert p["customer_id"]==int(sys.argv[1]); assert any(c["id"]==int(sys.argv[2]) for c in p["contracts"])' "$CUSTOMER_ID" "$CONTRACT_ID"
curl -fsS "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; c=json.load(sys.stdin); assert c["customer_id"]==int(sys.argv[1]); assert any(p["id"]==int(sys.argv[2]) for p in c["projects"])' "$CUSTOMER_ID" "$UNCOVERED_ID"
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"CI 甲方客户已更新","customerType":"enterprise","phone":"13900000000"}' | grep -q 'CI 甲方客户已更新'
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/employees/$EMP_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"employeeType":"engineer","phone":"13700000000"}' | grep -q 'engineer'
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"CI 维保项目已更新","plannedStart":"2026-01-01","plannedEnd":"2026-12-31","status":"in_progress"}' | grep -q 'CI 维保项目已更新'

# Project manager and technical lead must be distinct qualified engineers.
TECH=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"certificateNo\":\"CI-TECH-001\",\"fullName\":\"CI 技术负责人\",\"employeeType\":\"engineer\"}")
TECH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$TECH")
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/leadership" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"managerEmployeeId\":$EMP_ID,\"technicalLeadEmployeeId\":$TECH_ID}" | grep -q 'CI 技术负责人'
SAME_LEAD=$(curl -sS -o /tmp/erp-project-same-lead.json -w '%{http_code}' -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/leadership" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"managerEmployeeId\":$EMP_ID,\"technicalLeadEmployeeId\":$EMP_ID}")
test "$SAME_LEAD" = "400"

# Activate the maintenance contract and track project status and visits.
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"active","startsOn":"2026-01-01","endsOn":"2026-12-31"}' >/dev/null
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"currentContractId\":$CONTRACT_ID,\"honeybeeContractUploaded\":true,\"honeybeeProjectCreated\":true,\"projectMaterialsCompleted\":true,\"publicCardStatus\":\"preparing\",\"notes\":\"CI 维保状态\"}" | grep -q 'preparing'
VISIT=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance-visits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"contractId\":$CONTRACT_ID,\"visitDate\":\"2026-11-23\",\"employeeIds\":[$EMP_ID,$TECH_ID],\"notes\":\"CI 维护到场\"}")
VISIT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$VISIT")
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 技术负责人'

# Renew on the existing project, retaining original contract and project history.
BAD_RENEWAL_RESERVATION=$(reserve_business_number maintenance maintenance_renewal)
BAD_RENEWAL_RESERVATION_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$BAD_RENEWAL_RESERVATION")
BAD_RENEWAL=$(curl -sS -o /tmp/erp-invalid-renewal.json -w '%{http_code}' -X POST "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance-renewals" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"numberReservationId\":\"$BAD_RENEWAL_RESERVATION_ID\",\"externalContractNo\":\"CI-RENEWAL-INVALID\",\"title\":\"CI 无效续签\",\"amount\":100,\"startsOn\":\"2028-01-01\",\"endsOn\":\"2027-12-31\"}")
test "$BAD_RENEWAL" = "400"
release_business_number "$BAD_RENEWAL_RESERVATION_ID" >/dev/null
RENEWAL_RESERVATION=$(reserve_business_number maintenance maintenance_renewal)
RENEWAL_RESERVATION_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$RENEWAL_RESERVATION")
RENEWAL=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance-renewals" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"numberReservationId\":\"$RENEWAL_RESERVATION_ID\",\"externalContractNo\":\"CI-RENEWAL-001\",\"title\":\"CI 第二年度续签\",\"amount\":1200,\"signedOn\":\"2026-11-23\",\"startsOn\":\"2027-01-01\",\"endsOn\":\"2027-12-31\"}")
RENEWAL_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$RENEWAL")
RENEWAL_NO=$(python -c 'import json,sys; print(json.load(sys.stdin)["contract_no"])' <<< "$RENEWAL")
test -n "$RENEWAL_ID"
python -c 'import json,sys; c=json.loads(sys.argv[1]); assert c["contract_no"].startswith("ZJWB-"+sys.argv[2]+"-"); assert c["contract_no"]!=sys.argv[3]; assert c["external_contract_no"]=="CI-RENEWAL-001"' "$RENEWAL" "$year" "$CONTRACT_NO"
echo "$RENEWAL" | grep -q '"status":"draft"'
echo "$RENEWAL" | grep -q '"service_type":"maintenance"'
PROJECT_HISTORY=$(curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/contracts" -H "Authorization: Bearer $TOKEN")
echo "$PROJECT_HISTORY" | grep -q "$CONTRACT_NO"
echo "$PROJECT_HISTORY" | grep -q 'CI-RENEWAL-001'
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; data=json.load(sys.stdin); assert len(data["contractHistory"])>=2; assert data["current_contract_id"]==int(sys.argv[1])' "$CONTRACT_ID"
# Draft renewals cannot silently become the active maintenance contract.
DRAFT_CURRENT=$(curl -sS -o /tmp/erp-renewal-draft-current.json -w '%{http_code}' -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"currentContractId\":$RENEWAL_ID,\"publicCardStatus\":\"not_started\"}")
test "$DRAFT_CURRENT" = "400"
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/contracts/$RENEWAL_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"active"}' >/dev/null
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"currentContractId\":$RENEWAL_ID,\"publicCardStatus\":\"preparing\"}" | grep -q "$RENEWAL_NO"
curl -fsS "${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; p=json.load(sys.stdin); assert str(p["planned_start"])=="2027-01-01"; assert str(p["planned_end"])=="2027-12-31"'


# Contract originals, public cards and project materials use the private file store.
printf 'CI maintenance attachment.\\n' > /tmp/erp-maintenance-ci.txt
UPLOAD=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-maintenance-ci.txt;type=text/plain')
ATTACH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UPLOAD")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/files" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"fileId\":$ATTACH_ID,\"fileType\":\"maintenance_card\",\"title\":\"CI 维保公示卡\"}" | grep -q 'maintenance_card'
curl -fsS "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 维保公示卡'

# Draft association can be corrected without deleting a project; active history is protected.
curl -fsS -X POST "${SMOKE_API_URL}/api/business/contracts/$UNLINKED_DRAFT_ID/projects" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"projectId\":$UNCOVERED_ID}" >/dev/null
curl -fsS -X DELETE "${SMOKE_API_URL}/api/business/contracts/$UNLINKED_DRAFT_ID/projects/$UNCOVERED_ID" -H "Authorization: Bearer $TOKEN" >/dev/null
ACTIVE_UNLINK=$(curl -sS -o /tmp/erp-active-unlink.json -w '%{http_code}' -X DELETE "${SMOKE_API_URL}/api/business/contracts/$RENEWAL_ID/projects/$UNCOVERED_ID" -H "Authorization: Bearer $TOKEN")
test "$ACTIVE_UNLINK" = "409"
# Unified customer history must include repairs, projects and maintenance visits.
REPAIR=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/repairs" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"projectId\":$UNCOVERED_ID,\"repairDate\":\"2026-11-23\",\"title\":\"CI 维修记录\",\"description\":\"现场维修登记\",\"resultSummary\":\"完成\",\"employeeIds\":[$EMP_ID,$TECH_ID]}")
REPAIR_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPAIR")
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/repairs/$REPAIR_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"projectId\":$UNCOVERED_ID,\"repairDate\":\"2026-11-24\",\"title\":\"CI 维修记录已更新\",\"description\":\"现场维修登记\",\"resultSummary\":\"完成\",\"employeeIds\":[$TECH_ID]}" | grep -q 'CI 维修记录已更新'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/repairs?limit=1" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; r=json.load(sys.stdin); assert len(r["rows"])==1; assert r["rows"][0]["title"]=="CI 维修记录已更新"; assert "技术负责人" in r["rows"][0]["staff_names"]'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/maintenance" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 维护到场'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/contracts" -H "Authorization: Bearer $TOKEN" | grep -q 'CI-RENEWAL-001'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/projects" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert any(x["id"]==int(sys.argv[1]) for x in json.load(sys.stdin)["rows"])' "$UNCOVERED_ID"

# C04/C05: preview is read-only, generated card has a stable number and project/contract history.
DOC_SCOPE="${SMOKE_API_URL}/api/maintenance/projects/$UNCOVERED_ID/contracts/$RENEWAL_ID"
CARD_PAYLOAD='{"company":"河南众基消防技术有限公司","leader":"张三","phone":"13800138000","startsOn":"2027-01-01","endsOn":"2027-12-31","systems":"火灾自动报警系统、自动喷水灭火系统、消火栓系统"}'
BEFORE=$(curl -fsS "$DOC_SCOPE" -H "Authorization: Bearer $TOKEN")
curl -fsS -X POST "$DOC_SCOPE/preview" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$CARD_PAYLOAD" -o /tmp/erp-wbcard-preview.pdf
head -c 5 /tmp/erp-wbcard-preview.pdf | grep -q '%PDF-'
AFTER=$(curl -fsS "$DOC_SCOPE" -H "Authorization: Bearer $TOKEN")
test "$BEFORE" = "$AFTER"
CARD=$(curl -fsS -X POST "$DOC_SCOPE/cards" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$CARD_PAYLOAD")
CARD_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$CARD")
CARD_FILE=$(python -c 'import json,sys; print(json.load(sys.stdin)["file_id"])' <<< "$CARD")
CARD_CODE=$(python -c 'import json,sys; print(json.load(sys.stdin)["code"])' <<< "$CARD")
DUP_PAYLOAD=$(python -c 'import json,sys; x=json.loads(sys.argv[1]); x["code"]=sys.argv[2]; print(json.dumps(x))' "$CARD_PAYLOAD" "$CARD_CODE")
DUP_CARD=$(curl -sS -o /tmp/erp-duplicate-card.json -w '%{http_code}' -X POST "$DOC_SCOPE/cards" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$DUP_PAYLOAD")
test "$DUP_CARD" = "409"
curl -fsS -X PUT "${SMOKE_API_URL}/api/maintenance/cards/$CARD_ID/status" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"printed":true,"sent":true}' | python -c 'import json,sys; x=json.load(sys.stdin); assert x["printed"] and x["sent"]'
curl -fsS "${SMOKE_API_URL}/api/files/$CARD_FILE/download" -H "Authorization: Bearer $TOKEN" -o /tmp/erp-wbcard-generated.pdf
head -c 5 /tmp/erp-wbcard-generated.pdf | grep -q '%PDF-'
curl -fsS "$DOC_SCOPE" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; x=json.load(sys.stdin); assert len(x["cards"])==1 and x["cards"][0]["code"]==sys.argv[1]' "$CARD_CODE"
curl -fsS "${SMOKE_API_URL}/api/maintenance/projects/$UNCOVERED_ID/contracts/$CONTRACT_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert json.load(sys.stdin)["cards"]==[]'
CONTACT=$(curl -fsS -X POST ${SMOKE_API_URL}/api/maintenance/contacts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI 维保联系人","phone":"13800138000"}')
CONTACT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$CONTACT")
curl -fsS -X PUT "${SMOKE_API_URL}/api/maintenance/contacts/$CONTACT_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI 维保联系人","phone":"13800138001","active":false}' | grep -q '13800138001'
# Archive a PDF as an independent unchanged resource, then disable it without removing annual history.
M_UPLOAD=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-wbcard-preview.pdf;type=application/pdf')
M_FILE=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$M_UPLOAD")
MATERIAL=$(curl -fsS -X POST ${SMOKE_API_URL}/api/maintenance/materials -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"fileId\":$M_FILE,\"name\":\"CI 附带资料\"}")
MATERIAL_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$MATERIAL")
curl -fsS -X POST "$DOC_SCOPE/materials" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"materialId\":$MATERIAL_ID}" >/dev/null
curl -fsS -X PUT "${SMOKE_API_URL}/api/maintenance/materials/$MATERIAL_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI 已停用资料","active":false}' >/dev/null
curl -fsS "$DOC_SCOPE" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; x=json.load(sys.stdin); assert len(x["materials"])==1 and x["materials"][0]["title"]=="CI 附带资料"'
curl -fsS "${SMOKE_API_URL}/api/files/$M_FILE/download" -H "Authorization: Bearer $TOKEN" -o /tmp/erp-material-download.pdf
cmp /tmp/erp-wbcard-preview.pdf /tmp/erp-material-download.pdf

# C06: switching active contracts restores the selected annual state, not the previous year's fields.
STATE_URL="${SMOKE_API_URL}/api/business/projects/$UNCOVERED_ID/maintenance"
curl -fsS -X PUT "$STATE_URL" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"publicCardStatus":"sent","projectMaterialsCompleted":true}' >/dev/null
curl -fsS -X PUT "$STATE_URL" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"currentContractId\":$CONTRACT_ID}" | python -c 'import json,sys; s=json.load(sys.stdin); assert s["public_card_status"]=="preparing"; assert s["honeybee_contract_uploaded"]; assert s["project_materials_completed"]'
curl -fsS -X PUT "$STATE_URL" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"currentContractId\":$RENEWAL_ID}" | python -c 'import json,sys; s=json.load(sys.stdin); assert s["public_card_status"]=="sent"; assert not s["honeybee_contract_uploaded"]; assert s["project_materials_completed"]; assert len(s["contractHistory"])>=2'
