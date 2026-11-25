#!/usr/bin/env bash
# Sourced after bootstrap; only synthetic records in the isolated test database.
cp_json(){ curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }
for svc in maintenance fire_inspection fire_assessment fire_evaluation other; do
  starts='"startsOn":"2026-01-01"'
  if [[ "$svc" == maintenance ]]; then ends='"endsOn":"2026-12-31"'; else ends='"endsOn":null'; fi
  file=$(mktemp)
  printf 'Synthetic contract file for %s\n' "$svc" > "$file"
  fid=$(curl -fsS -H "Authorization: Bearer $TOKEN" -F "file=@$file;filename=contract.txt;type=text/plain" "$SMOKE_API_URL/api/files" | python -c 'import json,sys;print(json.load(sys.stdin)["id"])')
  rm -f "$file"
  result=$(cp_json -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"contractNo\":\"AUTO-$svc\",\"title\":\"Auto $svc\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"$svc\",$starts,$ends,\"fileId\":$fid}")
  cid=$(python -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$result")
  pid=$(python -c 'import json,sys;print(json.load(sys.stdin)["projectId"])' <<< "$result")
  cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;p=json.load(sys.stdin);t=sys.argv[1];assert p["service_type"]==t and p["name"]=="Auto "+t;assert p["planned_start"]=="2026-01-01";assert p.get("planned_end")==("2026-12-31" if t=="maintenance" else None)' "$svc"
  cp_json "$SMOKE_API_URL/api/business/projects?serviceType=$svc" | python -c 'import json,sys;rows=json.load(sys.stdin);assert any(p["id"]==int(sys.argv[1]) for p in rows);assert all(p["service_type"]==sys.argv[2] for p in rows)' "$pid" "$svc"
  cp_json "$SMOKE_API_URL/api/business/contracts/$cid" | python -c 'import json,sys;c=json.load(sys.stdin);assert len(c["projects"])==1;assert c["projects"][0]["id"]==int(sys.argv[1]);assert c["files"][0]["file_id"]==int(sys.argv[2])' "$pid" "$fid"
  if [[ "$svc" == fire_inspection ]]; then
    cp_json -X PUT "$SMOKE_API_URL/api/business/contracts/$cid" -d '{"endsOn":"2026-06-01"}' >/dev/null
    cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;assert json.load(sys.stdin)["planned_end"]=="2026-06-01"'
    cp_json -X PUT "$SMOKE_API_URL/api/business/contracts/$cid" -d '{"endsOn":null}' >/dev/null
    cp_json "$SMOKE_API_URL/api/business/projects/$pid" | python -c 'import json,sys;assert json.load(sys.stdin).get("planned_end") is None'
    cp_json -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"contractNo\":\"AUTO-EXISTING\",\"title\":\"Existing project\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"fire_inspection\",\"existingProjectId\":$pid,\"createProject\":true}" | python -c 'import json,sys;assert json.load(sys.stdin)["projectId"]==int(sys.argv[1])' "$pid"
  fi
done
bad=$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -X POST "$SMOKE_API_URL/api/business/contracts" -d "{\"contractNo\":\"AUTO-BAD-MAINT\",\"title\":\"Missing dates\",\"customerId\":$CUSTOMER_ID,\"serviceType\":\"maintenance\",\"createProject\":true}")
test "$bad" = 400
