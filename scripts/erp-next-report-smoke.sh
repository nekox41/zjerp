set -euo pipefail

# Report author is the bootstrap administrator already authenticated as TOKEN.
ROLES=$(curl -fsS http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN")
PM_ROLE_ID=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="project_manager"))' <<< "$ROLES")
ADMIN_ROLE_ID=$(python -c 'import json,sys; print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="admin"))' <<< "$ROLES")

REVIEWER=$(curl -fsS -X POST http://127.0.0.1:8090/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"username\":\"ci-reviewer\",\"displayName\":\"CI Reviewer\",\"password\":\"ci-reviewer-password-123\",\"roleIds\":[$PM_ROLE_ID]}") 
REVIEWER_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REVIEWER")
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
