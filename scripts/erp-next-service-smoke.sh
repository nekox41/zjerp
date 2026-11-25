set -euo pipefail

# V4 service-delivery acceptance checks; source this file from the authenticated smoke-test shell.
curl -fsS -X POST ${SMOKE_API_URL}/api/business/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"projectNo\":\"CI-P-NC\",\"name\":\"CI 无合同项目\",\"customerId\":$CUSTOMER_ID}" | grep -q 'CI 无合同项目'

curl -fsS -X POST "${SMOKE_API_URL}/api/business/employees/$EMP_ID/qualifications" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"qualificationName":"CI 资质","expiresOn":"2099-12-31"}' | grep -q 'CI 资质'

curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/issues" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"CI 整改项\",\"ownerEmployeeId\":$EMP_ID}" | grep -q 'CI 整改项'

WORK=$(curl -fsS -X POST "${SMOKE_API_URL}/api/business/projects/$PROJECT_ID/work-packages" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"packageNo\":\"CI-WP-1\",\"title\":\"CI 服务工作包\",\"assigneeEmployeeId\":$EMP_ID}")
WORK_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$WORK")

curl -fsS -X POST "${SMOKE_API_URL}/api/business/work-packages/$WORK_ID/records" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"CI 现场记录","content":"现场检查完成"}' | grep -q 'CI 现场记录'

curl -fsS -X PUT "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"active"}' >/dev/null

curl -fsS -X POST "${SMOKE_API_URL}/api/business/contracts/$CONTRACT_ID/amendments" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"amendmentNo":"CI-AM-1","title":"CI 合同变更","changeSummary":"变更测试","revisedAmount":1200}' | grep -q 'CI-AM-1'


# E03: atomic classification of persisted photos, separate manual confirmation, no cross-batch changes.
SW="${SMOKE_API_URL}/api/service-workflow"
VISIT_ROW=$(curl -fsS -X POST "$SW/work-packages/$WORK_ID/visits" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"title\":\"CI 检测现场\",\"visitDate\":\"2026-11-23\",\"staff\":[{\"employeeId\":$EMP_ID,\"role\":\"engineer\"}]}")
VISIT_ROW_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$VISIT_ROW")
PHOTO_BATCH=$(curl -fsS -X POST "$SW/work-packages/$WORK_ID/photo-batches" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"title\":\"CI 照片分类\",\"visitDate\":\"2026-11-23\",\"visitId\":$VISIT_ROW_ID}")
PHOTO_BATCH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$PHOTO_BATCH")
OTHER_BATCH=$(curl -fsS -X POST "$SW/work-packages/$WORK_ID/photo-batches" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"CI 其他批次","visitDate":"2026-11-24"}')
OTHER_BATCH_ID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$OTHER_BATCH")
python - <<'PY'
import struct,zlib
def chunk(kind,data):
    return struct.pack(">I",len(data))+kind+data+struct.pack(">I",zlib.crc32(kind+data)&0xffffffff)
for i in range(1,4):
    raw=b'\x00'+bytes([i*40,120,200])
    with open(f'/tmp/erp-photo-{i}.png','wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
PY
PHOTO_IDS=()
for i in 1 2 3; do
  F=$(curl -fsS -X POST ${SMOKE_API_URL}/api/files -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/erp-photo-$i.png;type=image/png")
  FID=$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$F")
  BID=$PHOTO_BATCH_ID
  if [ "$i" = 3 ]; then BID=$OTHER_BATCH_ID; fi
  P=$(curl -fsS -X POST "$SW/photo-batches/$BID/photos" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"fileId\":$FID,\"takenOn\":\"2026-11-23\",\"caption\":\"CI 原始说明 $i\"}")
  PHOTO_IDS+=("$(python -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "$P")")
done
CLASSIFY="$SW/photo-batches/$PHOTO_BATCH_ID/classification"
CROSS=$(curl -sS -o /tmp/erp-cross-photo.json -w '%{http_code}' -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":[${PHOTO_IDS[0]},${PHOTO_IDS[2]}],\"systemCode\":\"消火栓系统\",\"confirmed\":false}")
test "$CROSS" = "400"
curl -fsS "$SW/photo-batches/$PHOTO_BATCH_ID" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert all(not x.get("system_code") for x in json.load(sys.stdin)["photos"])'
PAIR="[${PHOTO_IDS[0]},${PHOTO_IDS[1]}]"
curl -fsS -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":$PAIR,\"systemCode\":\"消火栓系统\",\"confirmed\":false}" | python -c 'import json,sys; p=json.load(sys.stdin)["photos"]; assert len(p)==2; assert all(x["system_code"]=="消火栓系统" and x["confirmation_status"]=="pending" and x["taken_on"]=="2026-11-23" and "原始说明" in x["caption"] for x in p)'
EARLY_BATCH=$(curl -sS -o /tmp/erp-unconfirmed-batch.json -w '%{http_code}' -X POST "$SW/photo-batches/$PHOTO_BATCH_ID/submit" -H "Authorization: Bearer $TOKEN")
test "$EARLY_BATCH" = "400" || test "$EARLY_BATCH" = "409"
curl -fsS -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":$PAIR,\"systemCode\":\"消火栓系统\",\"confirmed\":true}" | python -c 'import json,sys; assert all(x["confirmation_status"]=="confirmed" for x in json.load(sys.stdin)["photos"])'
curl -fsS -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":$PAIR,\"systemCode\":\"消防给水系统\"}" | python -c 'import json,sys; assert all(x["confirmation_status"]=="pending" for x in json.load(sys.stdin)["photos"])'
curl -fsS -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":$PAIR,\"systemCode\":\"消防给水系统\",\"confirmed\":true}" >/dev/null
curl -fsS -X POST "$SW/photo-batches/$PHOTO_BATCH_ID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"status":"submitted"'
LOCKED_BATCH=$(curl -sS -o /tmp/erp-locked-batch.json -w '%{http_code}' -X PUT "$CLASSIFY" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"photoIds\":$PAIR,\"systemCode\":\"消火栓系统\"}")
test "$LOCKED_BATCH" = "409"
curl -fsS "${SMOKE_API_URL}/api/business/customers/$CUSTOMER_ID/history/visits" -H "Authorization: Bearer $TOKEN" | grep -q 'CI 检测现场'


# E06: multiple buildings, shared facilities located outside inspection area, manual judgments and version history.
PROFILE="$SW/work-packages/$WORK_ID/inspection-profile"
curl -fsS "$PROFILE" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert json.load(sys.stdin)["versionNo"]==0'
PROFILE_DATA='{"expectedVersionNo":0,"content":{"general":{"projectName":"CI 工程","address":"CI 地址"},"buildings":[{"name":"1号楼","aboveGroundFloors":"18"},{"name":"地下车库一期","belowGroundFloors":"2"}],"facilities":[{"system":"消防给水系统","name":"消防水泵房","location":"2号楼负一层","servedAreas":"1号楼、地下车库一期"}],"records":[{"system":"消防给水系统","item":"标识","result":"未设置","judgment":"不合格"}],"summaries":[{"system":"消防给水系统","judgment":"待审核"}],"conclusion":{"text":""}}}'
curl -fsS -X PUT "$PROFILE" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$PROFILE_DATA" | python -c 'import json,sys; r=json.load(sys.stdin); assert r["versionNo"]==1 and len(r["content"]["buildings"])==2 and r["content"]["conclusion"]["text"]==""'
STALE_PROFILE=$(curl -sS -o /tmp/erp-profile-stale.json -w '%{http_code}' -X PUT "$PROFILE" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$PROFILE_DATA")
test "$STALE_PROFILE" = "409"
NEXT_PROFILE=$(python -c 'import json,sys; r=json.loads(sys.argv[1]); r["expectedVersionNo"]=1; r["content"]["general"]["address"]="CI 更新地址"; print(json.dumps(r))' "$PROFILE_DATA")
curl -fsS -X PUT "$PROFILE" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$NEXT_PROFILE" | python -c 'import json,sys; assert json.load(sys.stdin)["versionNo"]==2'
curl -fsS "$PROFILE/versions/1" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; r=json.load(sys.stdin)["content"]; assert r["general"]["address"]=="CI 地址" and r["facilities"][0]["location"]=="2号楼负一层" and r["records"][0]["judgment"]=="不合格"'
curl -fsS "$PROFILE/versions" -H "Authorization: Bearer $TOKEN" | python -c 'import json,sys; assert [x["version_no"] for x in json.load(sys.stdin)]==[2,1]'
