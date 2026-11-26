#!/usr/bin/env bash
# Sourced after bootstrap; only synthetic records in the isolated test database.
set -euo pipefail
cp_json(){ curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }
year=$(TZ=Asia/Shanghai date +%Y)
for svc in maintenance fire_inspection fire_assessment fire_evaluation other; do
  case "$svc" in
    maintenance) prefix=ZJWB; starts='"startsOn":"2026-01-01"'; ends='"endsOn":"2026-12-31"' ;;
    fire_inspection) prefix=ZJJC; starts='"startsOn":"2026-01-01"'; ends='"endsOn":null' ;;
    fire_assessment) prefix=ZJPD; starts='"startsOn":null'; ends='"endsOn":null' ;;
    fire_evaluation) prefix=ZJPG; starts='"startsOn":null'; ends='"endsOn":null' ;;
    other) prefix=ZJFW; starts='"startsOn":null'; ends='"endsOn":null' ;;
  esac
  file=$(mktemp)
  printf 'Synthetic contract file for %s\n' "$svc" > "$file"
  fid=$(curl -fsS -H "Authorization: Bearer $TOKEN" -F "file=@$file;filename=contract.txt;type=text/plain" "$SMOKE_API_URL/api/files" | python -c 'import json,sys;print(json.load(sys.stdin)["id"])')
  rm -f "$file"
  reservation=$(reserve_business_number "$svc" contract)
  reservation_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$reservation")
  reserved_no=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$reservation")
  result=$(cp_json -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"numberReservationId\":\"$reservation_id\",\"title\":\"Auto $svc\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"$svc\",$starts,$ends,\"externalContractNo\":\"EXT-$svc\",\"fileId\":$fid}")
  cid=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$result")
  pid=$(python -c 'import json,sys;print(json.load(sys.stdin)["projectId"])' <<< "$result")
  cno=$(python -c 'import json,sys;print(json.load(sys.stdin)["contract_no"])' <<< "$result")
  test "$cno" = "$reserved_no"
  [[ "$cno" == "$prefix-$year-"* ]]
  if [[ "$svc" == fire_inspection || "$svc" == fire_assessment || "$svc" == fire_evaluation ]]; then
    test "$cno" = "$prefix-$year-001"
  fi
  cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;p=json.load(sys.stdin);t=sys.argv[1];no=sys.argv[2];assert p["service_type"]==t and p["name"]=="Auto "+t;assert p["project_no"]==no;assert p.get("planned_start")==("2026-01-01" if t in ("maintenance","fire_inspection") else None);assert p.get("planned_end")==("2026-12-31" if t=="maintenance" else None)' "$svc" "$cno"
  cp_json "$SMOKE_API_URL/api/business/projects?serviceType=$svc" | python -c 'import json,sys;rows=json.load(sys.stdin);assert any(p["id"]==int(sys.argv[1]) for p in rows);assert all(p["service_type"]==sys.argv[2] for p in rows)' "$pid" "$svc"
  cp_json "$SMOKE_API_URL/api/business/contracts/$cid" | python -c 'import json,sys;c=json.load(sys.stdin);assert len(c["projects"])==1;assert c["projects"][0]["id"]==int(sys.argv[1]);assert c["files"][0]["file_id"]==int(sys.argv[2]);assert c["contract_no"]==sys.argv[3];assert c["external_contract_no"]=="EXT-"+sys.argv[4]' "$pid" "$fid" "$cno" "$svc"
  if [[ "$svc" == fire_inspection ]]; then
    cp_json -X PUT "$SMOKE_API_URL/api/business/contracts/$cid" -d '{"endsOn":"2026-06-01"}' >/dev/null
    cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;assert json.load(sys.stdin)["planned_end"]=="2026-06-01"'
    cp_json -X PUT "$SMOKE_API_URL/api/business/contracts/$cid" -d '{"endsOn":null}' >/dev/null
    cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;assert json.load(sys.stdin).get("planned_end") is None'
    existing_reservation=$(reserve_business_number fire_inspection contract)
    existing_reservation_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$existing_reservation")
    existing_number=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$existing_reservation")
    existing=$(cp_json -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"numberReservationId\":\"$existing_reservation_id\",\"title\":\"Existing project\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"fire_inspection\",\"existingProjectId\":$pid}")
    python -c 'import json,sys;c=json.loads(sys.argv[1]);assert c["projectId"]==int(sys.argv[2]) and c["contract_no"]==sys.argv[3]' "$existing" "$pid" "$existing_number"
  fi
done
standalone_reservation=$(reserve_business_number other project)
standalone_reservation_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$standalone_reservation")
standalone_number=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$standalone_reservation")
standalone=$(cp_json -X POST "$SMOKE_API_URL/api/business/projects" -d "{\"numberReservationId\":\"$standalone_reservation_id\",\"name\":\"Standalone project\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"other\"}")
python -c 'import json,sys;p=json.loads(sys.argv[1]); assert p["project_no"]==sys.argv[2] and p.get("contract_id") is None' "$standalone" "$standalone_number"

# Two open forms hold distinct numbers. Cancelling the earlier one makes its gap reusable,
# even after the later form has been cancelled and another project has consumed the gap.
first=$(reserve_business_number other project)
second=$(reserve_business_number other project)
first_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$first")
second_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$second")
first_no=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$first")
second_no=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$second")
test "$first_no" != "$second_no"
release_business_number "$first_id" >/dev/null
third=$(reserve_business_number other project)
third_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$third")
third_no=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$third")
test "$third_no" = "$first_no"
reused_project=$(cp_json -X POST "$SMOKE_API_URL/api/business/projects" -d "{\"numberReservationId\":\"$third_id\",\"name\":\"Reused number project\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"other\"}")
python -c 'import json,sys;p=json.loads(sys.argv[1]);assert p["project_no"]==sys.argv[2]' "$reused_project" "$first_no"
release_business_number "$second_id" >/dev/null
fourth=$(reserve_business_number other project)
fourth_id=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$fourth")
fourth_no=$(python -c 'import json,sys;print(json.load(sys.stdin)["number"])' <<< "$fourth")
test "$fourth_no" = "$second_no"
release_business_number "$fourth_id" >/dev/null

missing_reservation=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -X POST "$SMOKE_API_URL/api/business/projects" -d "{\"name\":\"Missing reservation project\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"other\"}")
test "$missing_reservation" = 400

bad=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"title\":\"Missing dates\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"maintenance\"}")
test "$bad" = 400
