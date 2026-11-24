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
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/roles/$REG_ROLE_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"CI Customer Read Removed","permissionIds":[]}' >/dev/null
REG_OLD_SESSION=$(curl -sS -o /tmp/erp-reg-session.json -w '%{http_code}' http://127.0.0.1:8090/api/auth/me -H "Authorization: Bearer $REG_TOKEN")
test "$REG_OLD_SESSION" = "401" || test "$REG_OLD_SESSION" = "403"
REG_NEW_SESSION=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d "$REG_LOGIN")
python -c 'import json,sys; assert json.load(sys.stdin)["permissions"]==[]' <<< "$REG_NEW_SESSION"
curl -fsS -X PUT "http://127.0.0.1:8090/api/admin/identities/$REG_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"disabled"}' >/dev/null

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
