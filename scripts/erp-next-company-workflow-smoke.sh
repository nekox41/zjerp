set -euo pipefail

# End-to-end acceptance for the normalized company, maintenance, service, report and photo workflow.
# Source after the common authenticated smoke setup and after erp-next-report-smoke.sh.

INTERMEDIARY=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/intermediaries \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"intermediaryCode":"CI-INT-001","name":"CI 独立中间人","contactName":"CI 联系人","phone":"13800000000"}')
INTERMEDIARY_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$INTERMEDIARY")

RENEWAL=$(curl -fsS -X POST http://127.0.0.1:8090/api/business/contracts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"contractNo\":\"CI-CT-INT-001\",\"title\":\"CI 中间人合同\",\"customerId\":$CUSTOMER_ID,\"contractType\":\"maintenance\",\"signingMode\":\"intermediary\",\"intermediaryId\":$INTERMEDIARY_ID,\"intermediarySharePercent\":20,\"amount\":5000}")
RENEWAL_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$RENEWAL")
curl -fsS "http://127.0.0.1:8090/api/business/intermediaries/$INTERMEDIARY_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 中间人合同'

curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/contracts" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"contractId\":$RENEWAL_ID,\"relationType\":\"renewal\",\"isCurrent\":true}" | grep -q 'CI 中间人合同'
curl -fsS "http://127.0.0.1:8090/api/business/contracts/$RENEWAL_ID" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 项目'

curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/scope-units" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"unitName":"一期地下车库","unitType":"parking_phase","isSharedFacility":true,"serviceScope":"地下车库一期及共用消防设施"}' | grep -q '一期地下车库'
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/maintenance" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"honeybeeUploaded":true,"documentsReady":true,"maintenanceCardSent":true}' | grep -q '"maintenance_card_sent":true'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/maintenance-visits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"contractId\":$RENEWAL_ID,\"visitedOn\":\"2026-11-23\",\"employeeIds\":[$EMP_ID],\"summary\":\"CI 外地项目维保记录\"}" | grep -q 'CI 外地项目维保记录'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/service-visits" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"visitNo\":\"CI-VISIT-001\",\"visitType\":\"onsite\",\"visitedAt\":\"2026-11-23T10:00:00\",\"employeeIds\":[$EMP_ID],\"summary\":\"CI 多次现场服务记录\"}" | grep -q 'CI 多次现场服务记录'

ISSUE=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/issues" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"CI 整改状态历史\",\"ownerEmployeeId\":$EMP_ID}")
ISSUE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$ISSUE")
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/issues/$ISSUE_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"in_progress","note":"开始整改"}' | grep -q 'in_progress'
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/issues/$ISSUE_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"resolved","resolution":"整改完成，复检合格"}' | grep -q 'resolved'
curl -fsS "http://127.0.0.1:8090/api/business/issues/$ISSUE_ID/events" -H "Authorization: Bearer $TOKEN" | grep -q '整改完成'

# Create a report directly under the project, upload a DOCX template and a classified photo,
# then preview and commit the DOCX insertion as a new immutable report version.
REPORT=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/projects/$PROJECT_ID/reports" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reportNo":"CI-PHOTO-RPT-001","title":"CI 照片插入报告","reportType":"inspection","content":{"summary":"现场检测","scope":"火灾自动报警系统","methods":"目视检查","findings":"设备状态正常","conclusion":"满足检测要求"}}')
PHOTO_REPORT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$REPORT")

python3 - <<'PY'
import base64, zipfile
from pathlib import Path
png = "iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAIAAAA7ljmRAAAAFElEQVR4nGP8//8/AwwwMSABFA4Aby0DAyMYAwQAAAAASUVORK5CYII="
Path("/tmp/erp-photo-ci.png").write_bytes(base64.b64decode(png))
def p(text):
    return f'<w:p><w:r><w:t>{text}</w:t></w:r></w:p>'
def table(rows, cols):
    cell='<w:tc><w:tcPr/><w:p/></w:tc>'
    return '<w:tbl><w:tblPr/><w:tblGrid/>' + ''.join('<w:tr>'+cell*cols+'</w:tr>' for _ in range(rows)) + '</w:tbl>'
document = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
'<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
'<w:body>'+p('火灾自动报警系统')+table(2,6)+p('现场检测照片')+table(2,2)+
p('注：应附彩色照片并注明日期，日期应如实填写。')+
'<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body></w:document>')
types=('<?xml version="1.0" encoding="UTF-8"?>'
'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
'<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
'<Default Extension="xml" ContentType="application/xml"/>'
'<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
'</Types>')
rels=('<?xml version="1.0" encoding="UTF-8"?>'
'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
'<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
'</Relationships>')
Path('/tmp/erp-photo-template.docx').unlink(missing_ok=True)
with zipfile.ZipFile('/tmp/erp-photo-template.docx','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('[Content_Types].xml',types)
    z.writestr('_rels/.rels',rels)
    z.writestr('word/document.xml',document)
    z.writestr('word/_rels/document.xml.rels','<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>')
PY

TEMPLATE=$(curl -fsS -X POST http://127.0.0.1:8090/api/files -H "Authorization: Bearer $TOKEN" -F 'file=@/tmp/erp-photo-template.docx;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document')
TEMPLATE_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$TEMPLATE")
BATCH=$(curl -fsS -X POST http://127.0.0.1:8090/api/report-photos/batches \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectId\":$PROJECT_ID,\"title\":\"CI 现场照片批次\"}")
BATCH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$BATCH")
UPLOADED=$(curl -fsS -X POST "http://127.0.0.1:8090/api/report-photos/batches/$BATCH_ID/photos" \
  -H "Authorization: Bearer $TOKEN" -F 'photos=@/tmp/erp-photo-ci.png;type=image/png')
PHOTO_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["photos"][0]["photo_id"])' <<< "$UPLOADED")
curl -fsS -X PUT "http://127.0.0.1:8090/api/report-photos/batches/$BATCH_ID/photos" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"photos\":[{\"photoId\":$PHOTO_ID,\"systemCode\":\"火灾自动报警系统\",\"sortOrder\":0}]}" | grep -q '"status":"ready'
REPORT_DETAIL=$(curl -fsS "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID" -H "Authorization: Bearer $TOKEN")
VERSION=$(python -c 'import json,sys; print(json.load(sys.stdin)["current_version_no"])' <<< "$REPORT_DETAIL")
INSERT_BODY="{\"reportId\":$PHOTO_REPORT_ID,\"templateFileId\":$TEMPLATE_ID,\"expectedVersionNo\":$VERSION}"
curl -fsS -X POST "http://127.0.0.1:8090/api/report-photos/batches/$BATCH_ID/preview" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$INSERT_BODY" -o /tmp/erp-photo-preview.docx
unzip -t /tmp/erp-photo-preview.docx >/dev/null
INSERTED=$(curl -fsS -X POST "http://127.0.0.1:8090/api/report-photos/batches/$BATCH_ID/insert" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$INSERT_BODY")
test "$(python -c 'import json,sys; print(json.load(sys.stdin)["versionNo"])' <<< "$INSERTED")" = "2"
PHOTO_OUTPUT_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["fileId"])' <<< "$INSERTED")
curl -fsS "http://127.0.0.1:8090/api/files/$PHOTO_OUTPUT_ID/download" -H "Authorization: Bearer $TOKEN" -o /tmp/erp-photo-output.docx
unzip -t /tmp/erp-photo-output.docx >/dev/null

curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/start-review" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"reviewing"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/approve" -H "Authorization: Bearer $REVIEW_TOKEN" | grep -q '"status":"approved"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/issue" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"issued"'
curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/archive" -H "Authorization: Bearer $ISSUE_TOKEN" | grep -q '"status":"archived"'
DELIVERY=$(curl -fsS -X POST "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/deliveries" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientName":"CI 建设单位","deliveryAddress":"CI 测试地址"}')
DELIVERY_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$DELIVERY")
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/deliveries/$DELIVERY_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"deliveryStatus":"sent","courierName":"CI 快递","trackingNo":"CI-TRACK-001","sentAt":"2026-11-23T10:00:00"}' | grep -q '"delivery_status":"sent"'
curl -fsS -X PUT "http://127.0.0.1:8090/api/business/deliveries/$DELIVERY_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"deliveryStatus":"delivered","deliveredAt":"2026-11-24T10:00:00"}' | grep -q '"delivery_status":"delivered"'
curl -fsS "http://127.0.0.1:8090/api/business/reports/$PHOTO_REPORT_ID/deliveries" -H "Authorization: Bearer $TOKEN" | grep -q 'CI-TRACK-001'
