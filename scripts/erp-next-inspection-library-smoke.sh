#!/usr/bin/env bash
# Source only from the empty isolated smoke stack; all standard data below is synthetic.
set -euo pipefail
TOKEN="$TOKEN" WORK_ID="$WORK_ID" python - <<'PY'
import json,os,urllib.request,urllib.error,io,zipfile
from xml.etree import ElementTree as ET
base=os.environ["SMOKE_API_URL"]+"/api"
token=os.environ["TOKEN"]; work=os.environ["WORK_ID"]
def call(path,method="GET",body=None,status=200):
    req=urllib.request.Request(base+path,method=method,headers={"Authorization":"Bearer "+token,"Content-Type":"application/json"},data=None if body is None else json.dumps(body,ensure_ascii=False).encode())
    try:
        with urllib.request.urlopen(req,timeout=90) as res:
            assert res.status==status,(path,res.status,status)
            data=res.read()
            return data if path.endswith("/download") else json.loads(data)
    except urllib.error.HTTPError as e:
        if e.code!=status:raise AssertionError((path,e.code,e.read().decode(errors="replace"))) from e
        return None
# A fake catalog entry is intentionally confined to the throwaway CI database.
candidate=call("/fire-standards","POST",{"standardNo":"CI-TEST-ONLY","title":"仅测试的虚构规范","category":"CI","validity":"unknown","sourceUrl":"https://www.1190119.com/","verified":False})
profile_path="/service-workflow/work-packages/"+work+"/inspection-profile"
before=call(profile_path)["versionNo"]
call(profile_path,"PUT",{"expectedVersionNo":before,"content":{},"basisChoices":[{"id":candidate["id"],"scope":"测试","reason":"测试"}]},400)
standard=call("/fire-standards","POST",{"standardNo":"CI-TEST-ONLY","title":"仅测试的虚构规范","category":"CI","validity":"unknown","sourceUrl":"https://www.1190119.com/ci-test-only","accessUrl":"https://www.1190119.com/ci-test-only","notes":"纯测试记录，不存在实际规范，不进行网络请求","verified":True})
clause=call("/fire-standard-clauses","POST",{"standardId":standard["id"],"clauseNo":"TEST-1","summary":"仅测试的虚构要求","locator":"测试位置","confirmed":True})
system="CI自定义消防系统"
section=call("/inspection-templates/structured","POST",{"system":system,"name":"CI 结构模板","cleanConfirmed":True,"items":[{"item":"CI 检测项","clauseId":clause["id"]}]})
starter=call("/inspection-templates/starter","POST",{"name":"CI 公共模板","confirmed":True,"sections":[]})
items=call("/inspection-templates/"+str(section["id"])+"/items")
assert len(items)==1 and items[0]["clause_id"]==clause["id"]
general={k:"CI 测试" for k in ["projectName","address","commissioningUnit","inspectionDates","reportDate","constructionUnit","designUnit","contractor","supervisionUnit","totalArea","projectCategory","introduction","scope"]}
profile=call(profile_path,"PUT",{"expectedVersionNo":before,"content":{"general":general,"records":[{"system":system,"item":"客户端伪造字段","standard":"客户端伪造要求","templateItemId":str(items[0]["id"]),"location":"CI 部位","result":"CI 结果","judgment":"CI 人工判定"}],"conclusion":{"text":"CI 人工结论"}},"basisChoices":[{"id":standard["id"],"scope":"CI 系统","reason":"仅测试"}]})
assert profile["content"]["records"][0]["item"]=="CI 检测项"
assert "虚构要求" in profile["content"]["records"][0]["standard"]
assert "CI-TEST-ONLY" in profile["content"]["general"]["basis"]
r=call("/business/work-packages/"+work+"/reports","POST",{"reportNo":"CI-STRUCTURED-REPORT","title":"CI 组合报告","reportType":"inspection","inspectionProfileVersionNo":profile["versionNo"],"content":{"summary":"CI 摘要","conclusion":"CI 人工结论"}})
path="/service-workflow/reports/"+str(r["id"])
request={"baseTemplateId":starter["id"],"sectionTemplateIds":[section["id"]],"expectedVersionNo":r["current_version_no"],"bodyBindings":[]}
check=call(path+"/inspect-docx","POST",request)
assert not check["inspection"]["errors"],check["inspection"]["errors"]
request.update(fillBody=True,templateReviewed=True,templateSha256=check["templateSha256"],bodyPlanSha256=check["planSha256"],batchIds=[],photoMode="replace")
generated=call(path+"/generate-docx","POST",request)
raw=call("/files/"+str(generated["fileId"])+"/download")
with zipfile.ZipFile(io.BytesIO(raw)) as z:
    xml=z.read("word/document.xml")
    root=ET.fromstring(xml)
    ns={"w":"http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    text="".join(x.text or "" for x in root.findall(".//w:t",ns))
    assert "CI 检测项" in text and "CI 人工判定" in text
    assert "现场检测照片" not in text and "{{erp." not in text
    assert "TOC" in xml.decode()
details=call("/business/reports/"+str(r["id"]))
artifact=details["artifacts"][0]
manifest=artifact["generation_manifest"]
if isinstance(manifest,str):manifest=json.loads(manifest)
assert manifest["tocPending"] and manifest["profileVersionNo"]==profile["versionNo"]
call("/business/reports/"+str(r["id"])+"/submit","POST",{"expectedVersionNo":r["current_version_no"],"artifactId":artifact["id"],"layoutReviewed":True},409)
# A stopped standard still exists in a prior immutable report snapshot.
call("/fire-standards/"+str(standard["id"])+"/active","PUT",{"active":False})
assert call("/business/reports/"+str(r["id"]))["content"]["inspectionProfile"]["content"]["basisReferences"][0]["id"]==standard["id"]
print("Synthetic V19–V22 catalog/template/structured DOCX and pending TOC checks passed")
PY
