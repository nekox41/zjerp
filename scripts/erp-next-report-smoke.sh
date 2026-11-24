set -euo pipefail

# Self-registration is unprivileged until admin approval; custom role permissions are enforceable.
REG_PAYLOAD='{"username":"ci-self-register","displayName":"CI Registered","password":"ci-register-password-123","roleIds":[1],"status":"active"}'
curl -fsS -X POST http://127.0.0.1:8090/api/auth/register -H 'Content-Type: application/json' -d "$REG_PAYLOAD" >/dev/null
REG_DUP=$(curl -sS -o /tmp/erp-reg-dup.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/auth/register -H 'Content-Type: application/json' -d "$REG_PAYLOAD")
test "$REG_DUP" = "409"
REG_LOGIN='{"username":"ci-self-register","password":"ci-register-password-123"}'
REG_BLOCKED=$(curl -sS -o /tmp/erp-reg-blocked.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d "$REG_LOGIN")
test "$REG_BLOCKED" = "401"
REG_ID=$(curl -fsS http://127.0.0.1:8090/api/admin/identities -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; x=next(x for x in json.load(sys.stdin) if x["username"]=="ci-self-register"); assert x["status"]=="disabled" and not x["roles"]; print(x["id"])')
REG_PERMS=$(curl -fsS http://127.0.0.1:8090/api/admin/permissions -H "Authorization: Bearer $TOKEN")
REG_READ=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="customer:read"))' <<< "$REG_PERMS")
REG_ADMIN=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="identity:write"))' <<< "$REG_PERMS")
REG_BAD=$(curl -sS -o /tmp/erp-reg-admin.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"name\":\"CI Invalid Admin\",\"permissionIds\":[$REG_ADMIN]}")
test "$REG_BAD" = "400"
REG_ROLE=$(curl -fsS -X POST http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"name\":\"CI Customer Read\",\"permissionIds\":[$REG_READ]}")
REG_ROLE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REG_ROLE")
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/identities/$REG_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"status\":\"active\",\"roleIds\":[$REG_ROLE_ID]}" >/dev/null
REG_SESSION=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d "$REG_LOGIN")
REG_TOKEN=$(python -c 'import json,sys; x=json.load(sys.stdin); assert x["permissions"]==["customer:read"]; print(x["token"])' <<< "$REG_SESSION")
curl -fsS http://127.0.0.1:8090/api/business/customers -H "Authorization: Bearer $REG_TOKEN" >/dev/null
REG_FORBIDDEN=$(curl -sS -o /tmp/erp-reg-forbidden.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $REG_TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI Unauthorized","permissionIds":[]}')
test "$REG_FORBIDDEN" = "403"
REG_WRITE=$(curl -sS -o /tmp/erp-reg-write.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/business/customers -H "Authorization: Bearer $REG_TOKEN" -H 'Content-Type: application/json' -d '{"customerCode":"CI-NO-WRITE","name":"Forbidden"}')
test "$REG_WRITE" = "403"
# A customer-only account must not gain access to other workspaces through direct API calls.
for route in "business/projects/$PROJECT_ID" "business/contracts/$CONTRACT_ID" "business/projects/$PROJECT_ID/maintenance" "service-workflow/projects" "service-workflow/photo-batches/$PHOTO_BATCH_ID" "admin/identities"; do
  HTTP_CODE=$(curl -sS -o /tmp/erp-reg-cross-module.json -w '%{http_code}' "http://127.0.0.1:8090/api/$route" -H "Authorization: Bearer $REG_TOKEN")
  if [ "$HTTP_CODE" != "403" ]; then echo "Expected 403 on $route; got $HTTP_CODE"; exit 1; fi
done
ADMIN_ROLE_ID_CHECK=$(curl -fsS http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="admin"))')
PROTECTED_ROLE=$(curl -sS -o /tmp/erp-reg-protected-role.json -w '%{http_code}' -X PUT "http://127.0.0.1:8090/api/admin/roles/$ADMIN_ROLE_ID_CHECK" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI Invalid Built-in","permissionIds":[]}')
test "$PROTECTED_ROLE" = "400"
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/roles/$REG_ROLE_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI Customer Read Removed","permissionIds":[]}' >/dev/null
REG_OLD_SESSION=$(curl -sS -o /tmp/erp-reg-session.json -w '%{http_code}' http://127.0.0.1:8090/api/auth/me -H "Authorization: Bearer $REG_TOKEN")
test "$REG_OLD_SESSION" = "401" || test "$REG_OLD_SESSION" = "403"
REG_NEW_SESSION=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d "$REG_LOGIN")
python -c 'import json,sys; assert json.load(sys.stdin)["permissions"]==[]' <<< "$REG_NEW_SESSION"
REG_NEW_TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$REG_NEW_SESSION")
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/identities/$REG_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"disabled"}' >/dev/null
REG_DISABLED_SESSION=$(curl -sS -o /tmp/erp-reg-disabled-session.json -w '%{http_code}' http://127.0.0.1:8090/api/auth/me -H "Authorization: Bearer $REG_NEW_TOKEN")
test "$REG_DISABLED_SESSION" = "401" || test "$REG_DISABLED_SESSION" = "403"
REG_DISABLED_LOGIN=$(curl -sS -o /tmp/erp-reg-disabled-login.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d "$REG_LOGIN")
test "$REG_DISABLED_LOGIN" = "401"

# Report author is the bootstrap administrator already authenticated as TOKEN.
ROLES=$(curl -fsS http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN")
PM_ROLE_ID=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="project_manager"))' <<< "$ROLES")
ADMIN_ROLE_ID=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="admin"))' <<< "$ROLES")

REVIEWER=$(curl -fsS -X POST http://127.0.0.1:8090/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"ci-reviewer\",\"displayName\":\"CI Reviewer\",\"password\":\"ci-reviewer-password-123\",\"roleIds\":[$PM_ROLE_ID]}") 
REVIEWER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REVIEWER")
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/identities/$REVIEWER_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"displayName\":\"CI Reviewer Updated\",\"status\":\"active\",\"roleIds\":[$PM_ROLE_ID]}" | grep -q 'CI Reviewer Updated'
REVIEWER_EMP=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/employees -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"employeeCode\":\"CI-REVIEWER\",\"fullName\":\"CI Reviewer\",\"identityId\":$REVIEWER_ID,\"employeeType\":\"engineer\"}")
REVIEWER_EMP_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REVIEWER_EMP")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/employees/$REVIEWER_EMP_ID/qualifications" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"qualificationName":"CI Reviewer Qualification","expiresOn":"2099-12-31"}' >/dev/null
curl -fsS -X POST http://127.0.0.1:8090/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"ci-issuer\",\"displayName\":\"CI Issuer\",\"password\":\"ci-issuer-password-123\",\"roleIds\":[$ADMIN_ROLE_ID]}" >/dev/null

REVIEW_LOGIN=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d '{"username":"ci-reviewer","password":"ci-reviewer-password-123"}')
REVIEW_TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$REVIEW_LOGIN")
ISSUE_LOGIN=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d '{"username":"ci-issuer","password":"ci-issuer-password-123"}')
ISSUE_TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$ISSUE_LOGIN")

printf 'Private report attachment smoke test.\n' > /tmp/erp-report-ci.txt
UPLOAD=$(curl -fsS -X POST http://127.0.0.1:8090/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-report-ci.txt;type=text/plain')
FILE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UPLOAD")
FILE_SHA=$(python -c 'import json,sys; print(json.load(sys.stdin)["sha256"])' <<< "$UPLOAD")
LOCAL_SHA=$(sha256sum /tmp/erp-report-ci.txt | cut -d' ' -f1)
test "$FILE_SHA" = "$LOCAL_SHA"

REPORT=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/work-packages/$WORK_ID/reports" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reportNo":"CI-RPT-001","title":"CI 技术服务报告","reportType":"service","content":{"summary":"完成现场检查","scope":"设备外观与运行检查","methods":"目视与功能检查","findings":"设备运行正常","conclusion":"满足服务要求"}}')
REPORT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPORT")
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/attachments" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"fileId\":$FILE_ID}" | grep -q 'CI 技术服务报告'

# Draft report cannot be reviewed or issued before submission.
curl -fsS http://127.0.0.1:8090/api/auth/me -H "Authorization: Bearer $ISSUE_TOKEN" | tee /tmp/erp-report-issuer-me.json | grep -q 'report:issue'
EARLY_ISSUE=$(curl -sS -o /tmp/erp-report-early-issue.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN")
if [ "$EARLY_ISSUE" != "409" ]; then cat /tmp/erp-report-early-issue.json; exit 1; fi

curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
SELF_REVIEW=$(curl -sS -o /tmp/erp-report-self-review.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $TOKEN")
test "$SELF_REVIEW" = "403"
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/return" -H "Authorization: Bearer $REVIEW_TOKEN" -H 'Content-Type: application/json' -d '{"reason":"请补充设备型号"}' | grep -q '"status":"returned"'
RETURNED=$(curl -fsS "http://127.0.0.1:8090/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN")
RETURN_VERSION=$(python -c 'import json,sys; print(json.load(sys.stdin)["current_version_no"])' <<< "$RETURNED")
UPDATED=$(curl -fsS -X PUT "http://127.0.0.1:8090/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"expectedVersionNo\":$RETURN_VERSION,\"title\":\"CI 技术服务报告修订\",\"content\":{\"summary\":\"补充设备信息\",\"scope\":\"设备外观与运行检查\",\"methods\":\"目视与功能检查\",\"findings\":\"设备型号已补充\",\"conclusion\":\"满足服务要求\"}}")
echo "$UPDATED" | grep -q '"status":"draft"'
echo "$UPDATED" | grep -q '"current_version_no":2'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/approve" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"approved"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"issued"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/archive" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"archived"'
ARCHIVE_EDIT=$(curl -sS -o /tmp/erp-report-archive-edit.json -w '%{http_code}' -X PUT "http://127.0.0.1:8090/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":2,"content":{"summary":"不允许修改","conclusion":"不允许修改"}}')
test "$ARCHIVE_EDIT" = "409"

curl -fsS "http://127.0.0.1:8090/api/files/$FILE_ID/download" -H "Authorization: Bearer $REVIEW_TOKEN" -o /tmp/erp-report-downloaded.txt
cmp /tmp/erp-report-ci.txt /tmp/erp-report-downloaded.txt

REVISED=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$REPORT_ID/revise" -H "Authorization: Bearer $TOKEN")
echo "$REVISED" | grep -q '"status":"draft"'
echo "$REVISED" | grep -q '"revision_no":2'
echo "$REVISED" | grep -q 'erp-report-ci.txt'

curl -fsS "http://127.0.0.1:8090/api/business/customers/$CUSTOMER_ID/history/reports" -H "Authorization: Bearer $TOKEN" | grep -q 'CI-RPT-001'


# E07 foundation: the selected inspection data revision is an immutable, server-resolved report snapshot.
BOUND=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/work-packages/$WORK_ID/reports" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"reportNo":"CI-INSPECTION-PROFILE","title":"CI 检测资料绑定","reportType":"inspection","inspectionProfileVersionNo":1,"content":{"summary":"CI 摘要","conclusion":"待人工审核","inspectionProfile":{"versionNo":999}}}')
BOUND_ID=$(python -c 'import json,sys; r=json.load(sys.stdin); assert r["content"]["inspectionProfile"]["versionNo"]==1; assert r["content"]["inspectionProfile"]["content"]["general"]["address"]=="CI 地址"; print(r["id"])' <<< "$BOUND")
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":1,"title":"CI 保留资料快照","content":{"summary":"修改摘要","conclusion":"待人工审核"}}' | python -c 'import json,sys; r=json.load(sys.stdin); assert r["current_version_no"]==2 and r["content"]["inspectionProfile"]["versionNo"]==1'
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":2,"inspectionProfileVersionNo":2,"content":{"summary":"明确切换资料版本","conclusion":"待人工审核"}}' | python -c 'import json,sys; r=json.load(sys.stdin); assert r["current_version_no"]==3 and r["content"]["inspectionProfile"]["versionNo"]==2 and r["content"]["inspectionProfile"]["content"]["general"]["address"]=="CI 更新地址"'


# E07: create a synthetic DOCX with split markers, a table cell and a photo section.
# Real client reports are deliberately not committed to the repository.
python - <<'PY'
from zipfile import ZipFile
from xml.sax.saxutils import escape
p=lambda text: '<w:p><w:r><w:t>'+escape(text)+'</w:t></w:r></w:p>'
cell=lambda text: '<w:tc><w:tcPr><w:tcW w:w="2500" w:type="dxa"/></w:tcPr>'+p(text)+'</w:tc>'
table=lambda rows:'<w:tbl><w:tblPr/><w:tblGrid><w:gridCol w:w="2500"/><w:gridCol w:w="2500"/></w:tblGrid>'+''.join('<w:tr>'+''.join(cell(c) for c in row)+'</w:tr>' for row in rows)+'</w:tbl>'
xml='<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>工程：{{erp.general.</w:t></w:r><w:r><w:t>projectName}}</w:t></w:r></w:p>'+table([['工程地址','旧地址'],['单体名称','{{erp.buildings.1.name}}']])+p('消防给水系统')+p('现场检测照片')+table([['',''],['','']])+p('现场照片说明')+p('审核人：手签保留')+'<w:sectPr/></w:body></w:document>'
with ZipFile('/tmp/erp-inspection-template.docx','w') as z:
    z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
    z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
    z.writestr('word/document.xml',xml)
PY
TEMPLATE_UPLOAD=$(curl -fsS -X POST http://127.0.0.1:8090/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-inspection-template.docx;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document')
TEMPLATE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$TEMPLATE_UPLOAD")
DOCX_API="http://127.0.0.1:8090/api/service-workflow/reports/$BOUND_ID"
PRECHECK_BODY=$(python -c 'import json,sys; print(json.dumps({"templateFileId":int(sys.argv[1]),"expectedVersionNo":3,"bodyBindings":[{"target":"T1/R1/C2/P1","expectedText":"旧地址","field":"general.address"}]}))' "$TEMPLATE_ID")
PRECHECK=$(curl -fsS -X POST "$DOCX_API/inspect-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$PRECHECK_BODY")
python -c 'import json,sys; x=json.load(sys.stdin); i=x["inspection"]; assert not i["errors"],i["errors"]; assert len(i["changes"])==3; assert any(c["after"]=="CI 更新地址" for c in i["changes"]); assert x["profileVersionNo"]==2' <<< "$PRECHECK"
# Confirm that preflight is read-only and a fabricated plan hash is rejected.
curl -fsS "http://127.0.0.1:8090/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert json.load(sys.stdin)["artifacts"]==[]'
GEN_BODY=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); p=json.loads(sys.argv[2]); b.update(fillBody=True,templateReviewed=True,templateSha256=p["templateSha256"],bodyPlanSha256=p["planSha256"],batchIds=[],photoMode="append"); print(json.dumps(b))' "$PRECHECK_BODY" "$PRECHECK")
BAD_GEN=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); b["bodyPlanSha256"]="bad"; print(json.dumps(b))' "$GEN_BODY")
BAD_STATUS=$(curl -sS -o /tmp/erp-bad-plan.json -w '%{http_code}' -X POST "$DOCX_API/generate-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$BAD_GEN")
test "$BAD_STATUS" = "409"
# Review the real persisted photo batch using a different qualified employee.
curl -fsS -X POST "http://127.0.0.1:8090/api/service-workflow/photo-batches/$PHOTO_BATCH_ID/review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewed"'
GEN_BODY=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); b["batchIds"]=[int(sys.argv[2])]; print(json.dumps(b))' "$GEN_BODY" "$PHOTO_BATCH_ID")
GENERATED=$(curl -fsS -X POST "$DOCX_API/generate-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$GEN_BODY")
GEN_FILE=$(python -c 'import json,sys; x=json.load(sys.stdin); assert x["bodyFilled"] and x["photoCount"]==2; print(x["fileId"])' <<< "$GENERATED")
curl -fsS "http://127.0.0.1:8090/api/files/$GEN_FILE/download" -H "Authorization: Bearer $TOKEN" -o /tmp/erp-inspection-filled.docx
python - <<'PY'
from zipfile import ZipFile
from xml.etree import ElementTree as ET
with ZipFile('/tmp/erp-inspection-filled.docx') as z:
    ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
    root=ET.fromstring(z.read('word/document.xml'))
    text=''.join(x.text or '' for x in root.findall('.//w:t',ns))
    assert 'CI 工程' in text and 'CI 更新地址' in text and '地下车库一期' in text
    assert '审核人：手签保留' in text and '{{erp.' not in text
    assert len(root.findall('.//w:drawing',ns))==2
PY
curl -fsS "http://127.0.0.1:8090/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; a=json.load(sys.stdin)["artifacts"][0]; m=a["generation_manifest"]; m=json.loads(m) if isinstance(m,str) else m; assert m["profileVersionNo"]==2 and len(m["bodyTargets"])==3 and len(m["batchIds"])==1'
