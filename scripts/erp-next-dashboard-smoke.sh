set -euo pipefail
DASH=$(curl -fsS "${SMOKE_API_URL}/api/business/dashboard?customerId=$CUSTOMER_ID&projectId=$PROJECT_ID" -H "Authorization: Bearer $TOKEN")
python -c 'import json,sys;d=json.load(sys.stdin);assert d["contracts"]["count"]>=1;assert d["projects"]["count"]==1;assert len(d["invoices"])>=1;assert len(d["expenses"])>=1' <<< "$DASH"
FIN_DASH=$(curl -sS -o /tmp/erp-dashboard-finance.json -w '%{http_code}' ${SMOKE_API_URL}/api/business/dashboard -H "Authorization: Bearer $FIN_TOKEN")
test "$FIN_DASH" = "403"
NOAUTH=$(curl -sS -o /tmp/erp-dashboard-unauth.json -w '%{http_code}' ${SMOKE_API_URL}/api/business/dashboard)
test "$NOAUTH" = "401" || test "$NOAUTH" = "403"

OPTIONS=$(curl -fsS ${SMOKE_API_URL}/api/finance/options -H "Authorization: Bearer $FIN_TOKEN")
python -c 'import json,sys;d=json.load(sys.stdin);assert d["customers"] and d["contracts"] and d["projects"]' <<< "$OPTIONS"
