set -euo pipefail
ROLES=$(curl -fsS http://127.0.0.1:8090/api/admin/roles -H "Authorization: Bearer $TOKEN")
FIN_ROLE=$(python -c 'import json,sys;print(next(x["id"] for x in json.load(sys.stdin) if x["code"]=="finance"))' <<< "$ROLES")
curl -fsS -X POST http://127.0.0.1:8090/api/admin/identities -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"username\":\"ci-finance\",\"displayName\":\"CI Finance\",\"password\":\"ci-finance-password-123\",\"roleIds\":[$FIN_ROLE]}" >/dev/null
FIN_LOGIN=$(curl -fsS -X POST http://127.0.0.1:8090/api/auth/login -H 'Content-Type: application/json' -d '{"username":"ci-finance","password":"ci-finance-password-123"}')
FIN_TOKEN=$(python -c 'import json,sys;print(json.load(sys.stdin)["token"])' <<< "$FIN_LOGIN")

CLAIM=$(curl -fsS -X POST http://127.0.0.1:8090/api/finance/expenses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"claimNo\":\"CI-EXP-001\",\"title\":\"现场差旅\",\"projectId\":$PROJECT_ID,\"lines\":[{\"description\":\"交通\",\"category\":\"transport\",\"incurredOn\":\"2026-11-20\",\"amount\":\"12.30\"},{\"description\":\"住宿\",\"category\":\"travel\",\"incurredOn\":\"2026-11-20\",\"amount\":\"20.20\"}]}")
CLAIM_ID=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$CLAIM")
python -c 'import json,sys;d=json.load(sys.stdin);assert str(d["total_amount"])=="32.50" or float(d["total_amount"])==32.5' <<< "$CLAIM"

BAD_AMOUNT=$(curl -sS -o /tmp/erp-expense-invalid.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/finance/expenses -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"claimNo":"CI-EXP-BAD","title":"无效精度","lines":[{"description":"测试","category":"other","incurredOn":"2026-11-20","amount":"0.001"}]}')
test "$BAD_AMOUNT" = "400"
MISSING=$(curl -sS -o /tmp/erp-expense-noauth.json -w '%{http_code}' http://127.0.0.1:8090/api/finance/expenses)
test "$MISSING" = "403" || test "$MISSING" = "401"

curl -fsS -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
SELF_APPROVE=$(curl -sS -o /tmp/erp-expense-selfapprove.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/approve" -H "Authorization: Bearer $TOKEN")
test "$SELF_APPROVE" = "403"
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/return" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"reason":"请补充说明"}' | grep -q '"status":"returned"'
curl -fsS -X PUT "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":1,"title":"现场差旅补充","lines":[{"description":"交通补充","category":"transport","incurredOn":"2026-11-20","amount":"32.50"}]}' | grep -q '"version_no":2'
STALE=$(curl -sS -o /tmp/erp-expense-stale.json -w '%{http_code}' -X PUT "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"expectedVersionNo":1,"title":"重复编辑","lines":[{"description":"重复","category":"other","incurredOn":"2026-11-20","amount":"1.00"}]}')
test "$STALE" = "409"
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/approve" -H "Authorization: Bearer $FIN_TOKEN" | grep -q '"status":"approved"'
WRONG_PAY=$(curl -sS -o /tmp/erp-expense-pay-bad.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/pay" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-TRANSFER-1","paidAmount":"32.49"}')
test "$WRONG_PAY" = "409"
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/pay" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-TRANSFER-1","paidAmount":"32.50"}' | grep -q '"status":"paid"'
DOUBLE_PAY=$(curl -sS -o /tmp/erp-expense-pay-repeat.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID/pay" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-TRANSFER-1","paidAmount":"32.50"}')
test "$DOUBLE_PAY" = "409"
curl -fsS "http://127.0.0.1:8090/api/finance/expenses/$CLAIM_ID" -H "Authorization: Bearer $FIN_TOKEN" | grep -q 'CI-TRANSFER-1'
