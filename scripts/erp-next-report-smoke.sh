set -euo pipefail

export TOKEN
# Fixed roles: no public registration, no custom roles, no second admin.
python - <<'PY'
import json,os,urllib.request,urllib.error
base=os.environ["SMOKE_API_URL"]+"/api"
admin=os.environ["TOKEN"]
def call(path,method="GET",body=None,token=admin,status=200):
    h={"Content-Type":"application/json"}
    if token:h["Authorization"]="Bearer "+token
    req=urllib.request.Request(base+path,method=method,headers=h,data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req,timeout=30) as r:
            assert r.status==status,(path,r.status,status)
            return json.load(r)
    except urllib.error.HTTPError as e:
        if e.code!=status:raise AssertionError((path,e.code,status,e.read().decode(errors="replace"))) from e
body={"username":"ci-fixed-employee","displayName":"CI Employee","password":"ci-employee-password-123","role":"employee"}
employee=call("/admin/identities","POST",body,status=201)
assert employee["role"]=="employee"
call("/admin/identities","POST",body,status=409)
call("/admin/identities","POST",dict(body,username="ci-forbidden-admin",role="admin"),status=400)
call("/admin/identities","POST",dict(body,username="ADMIN",role="employee"),status=400)
rows=call("/admin/identities")
superuser=next(x for x in rows if x["username"]=="admin")
assert sum(x["role"]=="admin" for x in rows)==1
call("/admin/identities/"+str(superuser["id"]),"PUT",{"role":"manager"},status=400)
call("/admin/identities/"+str(superuser["id"]),"PUT",{"status":"disabled"},status=400)
login={"username":body["username"],"password":body["password"]}
session=call("/auth/login","POST",login,token=None)
assert "report:write" in session["permissions"] and "report:review" not in session["permissions"] and "identity:write" not in session["permissions"]
token=session["token"]
call("/admin/identities",token=token,status=403)
profile=call("/business/employees/"+os.environ["EMP_ID"],token=token)
assert profile["full_name"]=="CI 员工" and not ({"id_number","phone","certificate_no","documents","extra_fields"} & profile.keys())
call("/business/employees/"+os.environ["EMP_ID"]+"/documents/"+os.environ["EMP_DOC"]+"/download",token=token,status=403)
call("/business/employees/account-links",token=token,status=403)
call("/business/customers","POST",{"customerCode":"CI-NO-WRITE","name":"Forbidden"},token=token,status=403)
call("/migration/source-status",token=token,status=403)
call("/admin/identities/"+str(employee["id"]),"PUT",{"role":"manager"})
try:
    call("/auth/me",token=token,status=403)
except AssertionError as e:
    # Some Spring Security configurations return 401 for revoked sessions.
    if e.args[0][1]!=401:raise
manager=call("/auth/login","POST",login,token=None)
assert "report:issue" in manager["permissions"] and "identity:write" not in manager["permissions"]
call("/admin/identities",token=manager["token"],status=403)
call("/admin/identities/"+str(employee["id"]),"PUT",{"status":"disabled"})
call("/auth/login","POST",login,token=None,status=401)
print("Fixed tiers / single admin / revocation checks passed")
PY

REVIEWER=$(curl -fsS -X POST ${SMOKE_API_URL}/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"ci-reviewer\",\"displayName\":\"CI Reviewer\",\"password\":\"ci-reviewer-password-123\",\"role\":\"manager\"}") 
REVIEWER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REVIEWER")
curl -fsS -X PUT "${SMOKE_API_URL}/api/admin/identities/$REVIEWER_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"displayName\":\"CI Reviewer Updated\",\"status\":\"active\",\"role\":\"manager\"}" | grep -q 'CI Reviewer Updated'
REVIEWER_EMP=$(curl -fsS -X POST ${SMOKE_API_URL}/api/business/employees -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"certificateNo\":\"CI-REVIEWER\",\"fullName\":\"CI Reviewer\",\"identityId\":$REVIEWER_ID,\"employeeType\":\"engineer\"}")
REVIEWER_EMP_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REVIEWER_EMP")
curl -fsS -X POST ${SMOKE_API_URL}/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"ci-issuer\",\"displayName\":\"CI Issuer\",\"password\":\"ci-issuer-password-123\",\"role\":\"manager\"}" >/dev/null

REVIEW_LOGIN=$(curl -fsS -X POST ${SMOKE_API_URL}/api/auth/login -H 'Content-Type: application/json' -d '{"username":"ci-reviewer","password":"ci-reviewer-password-123"}')
REVIEW_TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$REVIEW_LOGIN")
ISSUE_LOGIN=$(curl -fsS -X POST ${SMOKE_API_URL}/api/auth/login -H 'Content-Type: application/json' -d '{"username":"ci-issuer","password":"ci-issuer-password-123"}')
ISSUE_TOKEN=$(python -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<< "$ISSUE_LOGIN")

printf 'Private report attachment smoke test.\n' > /tmp/erp-report-ci.txt
UPLOAD=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-report-ci.txt;type=text/plain')
FILE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$UPLOAD")
FILE_SHA=$(python -c 'import json,sys; print(json.load(sys.stdin)["sha256"])' <<< "$UPLOAD")
LOCAL_SHA=$(sha256sum /tmp/erp-report-ci.txt | cut -d' ' -f1)
test "$FILE_SHA" = "$LOCAL_SHA"

REPORT=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/work-packages/$WORK_ID/reports" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reportNo":"CI-RPT-001","title":"CI 技术服务报告","reportType":"service","content":{"summary":"完成现场检查","scope":"设备外观与运行检查","methods":"目视与功能检查","findings":"设备运行正常","conclusion":"满足服务要求"}}')
REPORT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPORT")
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/attachments" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"fileId\":$FILE_ID}" | grep -q 'CI 技术服务报告'

# Draft report cannot be reviewed or issued before submission.
curl -fsS ${SMOKE_API_URL}/api/auth/me -H "Authorization: Bearer $ISSUE_TOKEN" | tee /tmp/erp-report-issuer-me.json | grep -q 'report:issue'
EARLY_ISSUE=$(curl -sS -o /tmp/erp-report-early-issue.json -w '%{http_code}' -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN")
if [ "$EARLY_ISSUE" != "409" ]; then cat /tmp/erp-report-early-issue.json; exit 1; fi

curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
SELF_REVIEW=$(curl -sS -o /tmp/erp-report-self-review.json -w '%{http_code}' -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $TOKEN")
test "$SELF_REVIEW" = "403"
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/return" -H "Authorization: Bearer $REVIEW_TOKEN" -H 'Content-Type: application/json' -d '{"reason":"请补充设备型号"}' | grep -q '"status":"returned"'
RETURNED=$(curl -fsS "${SMOKE_API_URL}/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN")
RETURN_VERSION=$(python -c 'import json,sys; print(json.load(sys.stdin)["current_version_no"])' <<< "$RETURNED")
UPDATED=$(curl -fsS -X PUT "${SMOKE_API_URL}/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"expectedVersionNo\":$RETURN_VERSION,\"title\":\"CI 技术服务报告修订\",\"content\":{\"summary\":\"补充设备信息\",\"scope\":\"设备外观与运行检查\",\"methods\":\"目视与功能检查\",\"findings\":\"设备型号已补充\",\"conclusion\":\"满足服务要求\"}}")
echo "$UPDATED" | grep -q '"status":"draft"'
echo "$UPDATED" | grep -q '"current_version_no":2'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/approve" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"approved"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"issued"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/archive" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"archived"'
ARCHIVE_EDIT=$(curl -sS -o /tmp/erp-report-archive-edit.json -w '%{http_code}' -X PUT "${SMOKE_API_URL}/api/business/reports/$REPORT_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":2,"content":{"summary":"不允许修改","conclusion":"不允许修改"}}')
test "$ARCHIVE_EDIT" = "409"

curl -fsS "${SMOKE_API_URL}/api/files/$FILE_ID/download" -H "Authorization: Bearer $REVIEW_TOKEN" -o /tmp/erp-report-downloaded.txt
cmp /tmp/erp-report-ci.txt /tmp/erp-report-downloaded.txt

REVISED=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$REPORT_ID/revise" -H "Authorization: Bearer $TOKEN")
echo "$REVISED" | grep -q '"status":"draft"'
echo "$REVISED" | grep -q '"revision_no":2'
echo "$REVISED" | grep -q 'erp-report-ci.txt'

curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/reports" -H "Authorization: Bearer $TOKEN" | grep -q 'CI-RPT-001'


# E07 foundation: the selected inspection data revision is an immutable, server-resolved report snapshot.
BOUND=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/work-packages/$WORK_ID/reports" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"reportNo":"CI-INSPECTION-PROFILE","title":"CI 检测资料绑定","reportType":"inspection","inspectionProfileVersionNo":1,"content":{"summary":"CI 摘要","conclusion":"待人工审核","inspectionProfile":{"versionNo":999}}}')
BOUND_ID=$(python -c 'import json,sys; r=json.load(sys.stdin); assert r["content"]["inspectionProfile"]["versionNo"]==1; assert r["content"]["inspectionProfile"]["content"]["general"]["address"]=="CI 地址"; print(r["id"])' <<< "$BOUND")
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":1,"title":"CI 保留资料快照","content":{"summary":"修改摘要","conclusion":"待人工审核"}}' | python -c 'import json,sys; r=json.load(sys.stdin); assert r["current_version_no"]==2 and r["content"]["inspectionProfile"]["versionNo"]==1'
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":2,"inspectionProfileVersionNo":2,"content":{"summary":"明确切换资料版本","conclusion":"待人工审核"}}' | python -c 'import json,sys; r=json.load(sys.stdin); assert r["current_version_no"]==3 and r["content"]["inspectionProfile"]["versionNo"]==2 and r["content"]["inspectionProfile"]["content"]["general"]["address"]=="CI 更新地址"'


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
TEMPLATE_UPLOAD=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-inspection-template.docx;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document')
TEMPLATE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$TEMPLATE_UPLOAD")
DOCX_API="${SMOKE_API_URL}/api/service-workflow/reports/$BOUND_ID"
PRECHECK_BODY=$(python -c 'import json,sys; print(json.dumps({"templateFileId":int(sys.argv[1]),"expectedVersionNo":3,"bodyBindings":[{"target":"T1/R1/C2/P1","expectedText":"旧地址","field":"general.address"}]}))' "$TEMPLATE_ID")
PRECHECK=$(curl -fsS -X POST "$DOCX_API/inspect-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$PRECHECK_BODY")
python -c 'import json,sys; x=json.load(sys.stdin); i=x["inspection"]; assert not i["errors"],i["errors"]; assert len(i["changes"])==3; assert any(c["after"]=="CI 更新地址" for c in i["changes"]); assert x["profileVersionNo"]==2' <<< "$PRECHECK"
# Confirm that preflight is read-only and a fabricated plan hash is rejected.
curl -fsS "${SMOKE_API_URL}/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert json.load(sys.stdin)["artifacts"]==[]'
GEN_BODY=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); p=json.loads(sys.argv[2]); b.update(fillBody=True,templateReviewed=True,templateSha256=p["templateSha256"],bodyPlanSha256=p["planSha256"],batchIds=[],photoMode="append"); print(json.dumps(b))' "$PRECHECK_BODY" "$PRECHECK")
BAD_GEN=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); b["bodyPlanSha256"]="bad"; print(json.dumps(b))' "$GEN_BODY")
BAD_STATUS=$(curl -sS -o /tmp/erp-bad-plan.json -w '%{http_code}' -X POST "$DOCX_API/generate-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$BAD_GEN")
test "$BAD_STATUS" = "409"
# A returned batch is editable again; preserve the reason, then resubmit.
curl -fsS -X POST "${SMOKE_API_URL}/api/service-workflow/photo-batches/$PHOTO_BATCH_ID/return" -H "Authorization: Bearer $REVIEW_TOKEN" -H 'Content-Type: application/json' -d '{"reason":"CI 核查分类"}' | python -c 'import json,sys; b=json.load(sys.stdin); assert b["status"]=="collecting" and any(h["action"]=="report.photo_batch.returned" for h in b["reviewHistory"])'
curl -fsS -X POST "${SMOKE_API_URL}/api/service-workflow/photo-batches/$PHOTO_BATCH_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
# Review the real persisted photo batch using a different qualified employee.
curl -fsS -X POST "${SMOKE_API_URL}/api/service-workflow/photo-batches/$PHOTO_BATCH_ID/review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewed"'
GEN_BODY=$(python -c 'import json,sys; b=json.loads(sys.argv[1]); b["batchIds"]=[int(sys.argv[2])]; print(json.dumps(b))' "$GEN_BODY" "$PHOTO_BATCH_ID")
GENERATED=$(curl -fsS -X POST "$DOCX_API/generate-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$GEN_BODY")
GEN_FILE=$(python -c 'import json,sys; x=json.load(sys.stdin); assert x["bodyFilled"] and x["photoCount"]==2; print(x["fileId"])' <<< "$GENERATED")
curl -fsS "${SMOKE_API_URL}/api/files/$GEN_FILE/download" -H "Authorization: Bearer $TOKEN" -o /tmp/erp-inspection-filled.docx
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
curl -fsS "${SMOKE_API_URL}/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; a=json.load(sys.stdin)["artifacts"][0]; m=a["generation_manifest"]; m=json.loads(m) if isinstance(m,str) else m; assert m["profileVersionNo"]==2 and len(m["bodyTargets"])==3 and len(m["batchIds"])==1'

# B06: report generation through independent review, signature registration, and delivery history.
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/approve" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"approved"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"issued"'
DELIVERY_ROW=$(curl -fsS -X POST "$DOCX_API/deliveries" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"deliveryMethod":"courier","recipientName":"CI 收件人","recipientAddress":"CI 地址","trackingNo":"CI-TRACKING"}')
DELIVERY_ROW_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$DELIVERY_ROW")
curl -fsS -X POST "${SMOKE_API_URL}/api/service-workflow/deliveries/$DELIVERY_ROW_ID/status" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"dispatched"}' | grep -q '"status":"dispatched"'
curl -fsS -X POST "${SMOKE_API_URL}/api/service-workflow/deliveries/$DELIVERY_ROW_ID/status" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"delivered"}' | grep -q '"status":"delivered"'
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/deliveries" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert any(x["report_no"]=="CI-INSPECTION-PROFILE" and x["status"]=="delivered" and x["tracking_no"]=="CI-TRACKING" for x in json.load(sys.stdin)["rows"])'


# V18: the exact issued DOCX is pinned, even after the report is revised.
SELECTED_ARTIFACT=$(curl -fsS "$DOCX_API/deliveries" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; r=json.load(sys.stdin)[0]; assert r["artifact_id"] and r["version_no"]==3 and r["artifact_sha256"]; print(r["artifact_id"])')
UNISSUED=$(curl -sS -o /tmp/erp-report-unissued-delivery.json -w '%{http_code}' -X POST "$DOCX_API/deliveries" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"versionNo":2,"recipientName":"CI","recipientAddress":"CI"}')
test "$UNISSUED" = "409"
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/revise" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; r=json.load(sys.stdin); assert r["current_version_no"]==4 and r["status"]=="draft" and not r["attachments"]'
curl -fsS "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/versions/3" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; r=json.load(sys.stdin); assert r["submitted_artifact_id"]==int(sys.argv[1]) and r["version_issued_at"] and r["title_snapshot"]=="CI 保留资料快照"' "$SELECTED_ARTIFACT"
MISSING_DOCX=$(curl -sS -o /tmp/erp-report-missing-docx.json -w '%{http_code}' -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/submit" -H "Authorization: Bearer $TOKEN")
test "$MISSING_DOCX" = "409"
EDITED_UPLOAD=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-inspection-filled.docx;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document')
EDITED_FILE=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$EDITED_UPLOAD")
EDITED_ARTIFACT=$(curl -fsS -X POST "$DOCX_API/register-docx" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"expectedVersionNo\":4,\"fileId\":$EDITED_FILE,\"manualReviewed\":true}" | python -c 'import json,sys; print(json.load(sys.stdin)["artifactId"])')
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/submit" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"expectedVersionNo\":4,\"artifactId\":$EDITED_ARTIFACT}" | grep -q '"status":"submitted"'
curl -fsS -X POST "${SMOKE_API_URL}/api/business/reports/$BOUND_ID/withdraw" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":4,"reason":"CI 撤回重新编制"}' | grep -q '"status":"withdrawn"'
curl -fsS -X PUT "${SMOKE_API_URL}/api/business/reports/$BOUND_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":4,"content":{"summary":"CI 再次编制","conclusion":"人工确认"}}' | python -c 'import json,sys; r=json.load(sys.stdin); assert r["status"]=="draft" and r["current_version_no"]==5 and not r["attachments"]'
