set -euo pipefail
INVOICE=$(curl -fsS -X POST http://127.0.0.1:8090/api/finance/invoices -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"invoiceNo\":\"CI-INV-001\",\"direction\":\"receivable\",\"counterpartyName\":\"CI 客户\",\"title\":\"服务发票\",\"amount\":\"100.25\",\"issuedOn\":\"2026-11-20\",\"customerId\":$CUSTOMER_ID,\"contractId\":$CONTRACT_ID,\"projectId\":$PROJECT_ID}")
INVOICE_ID=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$INVOICE")
SELF_APPROVE=$(curl -sS -o /tmp/erp-invoice-selfapprove.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/approve" -H "Authorization: Bearer $TOKEN")
test "$SELF_APPROVE" = "403"
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/approve" -H "Authorization: Bearer $FIN_TOKEN" | grep -q '"status":"open"'
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/settlements" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-INVOICE-PAY-1","amount":"20.10","settledOn":"2026-11-20"}' | grep -q '"status":"partial"'
OVERPAY=$(curl -sS -o /tmp/erp-invoice-overpay.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/settlements" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-INVOICE-PAY-2","amount":"80.16","settledOn":"2026-11-20"}')
test "$OVERPAY" = "409"
REUSED=$(curl -sS -o /tmp/erp-invoice-reused.json -w '%{http_code}' -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/settlements" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-INVOICE-PAY-1","amount":"1.00","settledOn":"2026-11-20"}')
test "$REUSED" = "409"
curl -fsS -X POST "http://127.0.0.1:8090/api/finance/invoices/$INVOICE_ID/settlements" -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"paymentReference":"CI-INVOICE-PAY-2","amount":"80.15","settledOn":"2026-11-20"}' | grep -q '"status":"settled"'
curl -fsS "http://127.0.0.1:8090/api/finance/invoices/summary" -H "Authorization: Bearer $FIN_TOKEN" | grep -q 'receivable'
INVALID=$(curl -sS -o /tmp/erp-invoice-invalid.json -w '%{http_code}' -X POST http://127.0.0.1:8090/api/finance/invoices -H "Authorization: Bearer $FIN_TOKEN" -H 'Content-Type: application/json' -d '{"invoiceNo":"CI-INV-INVALID","direction":"payable","counterpartyName":"供应商","title":"无效金额","amount":"1.001","issuedOn":"2026-11-20"}')
test "$INVALID" = "400"
