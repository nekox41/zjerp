import json,sys
from urllib.request import Request,urlopen
base="http://127.0.0.1:8089/next-api"
def call(path,data=None,token=None,method=None):
    h={"Content-Type":"application/json"}
    if token:h["Authorization"]="Bearer "+token
    with urlopen(Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h,method=method),timeout=15) as r:return json.load(r)
assert b"next-app" in urlopen("http://127.0.0.1:8089/next.html",timeout=15).read()
token=call("/auth/login",{"username":"admin","password":"123456"})["token"]
rows=call("/business/employees",token=token)
assert rows==[],rows
if sys.argv[1]=="first":
    call("/business/employees",{"fullName":"CI Preview","employeeType":"operator"},token)
    assert len(call("/business/employees",token=token))==1
print("Native preview smoke passed:",sys.argv[1])

# Ordinary employees can maintain intermediary records; details show linked contracts.
call("/admin/identities",{"username":"ci-partner-employee","displayName":"CI Partner Employee","password":"ci-partner-password-123","role":"employee"},token)
partner_login=call("/auth/login",{"username":"ci-partner-employee","password":"ci-partner-password-123"})
partner_token=partner_login["token"]
partner_me=call("/auth/me",token=partner_token)
assert "partner:read" in partner_me["permissions"] and "partner:write" in partner_me["permissions"]
partner=call("/business/partners",{"name":"CI 中间人","contact":"13800000000 / 微信 ci-partner","region":"上海市"},partner_token)
assert partner["name"]=="CI 中间人" and partner["contact"]=="13800000000 / 微信 ci-partner" and partner["region"]=="上海市"
partner_id=partner["id"]
from urllib.parse import quote
from datetime import datetime
from zoneinfo import ZoneInfo
year=datetime.now(ZoneInfo("Asia/Shanghai")).year
rows=call("/business/partners?q="+quote("上海市"),token=partner_token)
assert any(x["id"]==partner_id and x["contract_count"]==0 for x in rows)
# Bind a real draft contract as admin, then verify an ordinary employee can see
# its identity in the intermediary detail without receiving contract:read.
customer=call("/business/customers",{"customerCode":"CI-PARTNER-CUSTOMER","name":"CI 中间人测试甲方"},token)
# Verify all service prefixes and that a maintenance renewal receives a new
# contract number while preserving the original project's number.
for service_type,prefix in [("maintenance","ZJWB"),("fire_inspection","ZJJC"),("fire_assessment","ZJPD"),("fire_evaluation","ZJPG")]:
    payload={"title":"CI "+service_type,"customerId":customer["id"],"serviceType":service_type,"amount":"100.00"}
    if service_type=="maintenance":
        payload.update({"startsOn":f"{year}-01-01","endsOn":f"{year}-12-31"})
    created=call("/business/contracts",payload,token)
    assert created["contract_no"]==f"{prefix}-{year}-001",created
    project_id=created["projectId"]
    project=call("/business/projects/"+str(project_id),token=token)
    assert project["project_no"]==created["contract_no"] and project["name"]==created["title"]
    if service_type=="maintenance":
        renewal=call("/business/projects/"+str(project_id)+"/maintenance-renewals",{
            "title":"CI 维保续签","amount":"100.00","startsOn":f"{year}-01-01",
            "endsOn":f"{year}-12-31","externalContractNo":"CI-RENEWAL-EXT"
        },token)
        assert renewal["contract_no"]==f"ZJWB-{year}-002",renewal
        renewal_detail=call("/business/contracts/"+str(renewal["id"]),token=token)
        assert len(renewal_detail["projects"])==1 and renewal_detail["projects"][0]["id"]==project_id
        assert call("/business/projects/"+str(project_id),token=token)["project_no"]==f"ZJWB-{year}-001"
contract=call("/business/contracts",{"contractNo":"CI-PARTNER-CONTRACT","title":"CI 中间人绑定合同","customerId":customer["id"],"serviceType":"other","externalContractNo":"甲方-EXT-001","partnerId":partner_id},token)
assert contract["contract_no"]==f"ZJFW-{year}-001",contract
assert contract["external_contract_no"]=="甲方-EXT-001"
project=call("/business/projects/"+str(contract["projectId"]),token=token)
assert project["project_no"]==contract["contract_no"] and project["name"]==contract["title"]
call("/business/contracts/"+str(contract["id"])+"/partners",{"partnerId":partner_id,"sharePercent":"20"},token)
detail=call("/business/partners/"+str(partner_id),token=partner_token)
assert detail["contact"]=="13800000000 / 微信 ci-partner"
assert len(detail["contracts"])==1
assert detail["contracts"][0]["id"]==contract["id"]
assert detail["contracts"][0]["contract_no"]==contract["contract_no"]
assert detail["contracts"][0]["title"]=="CI 中间人绑定合同"
assert set(detail["contracts"][0])=={"id","contract_no","title"}
filtered=call("/business/contracts?serviceType=other&q="+quote("甲方-EXT-001"),token=token)
assert len(filtered)==1 and filtered[0]["id"]==contract["id"]
standalone=call("/business/projects",{"name":"CI 无合同项目","customerId":customer["id"],"serviceType":"other"},token)
assert standalone["project_no"]==f"ZJFW-{year}-002",standalone
assert standalone.get("contract_id") is None
updated=call("/business/partners/"+str(partner_id),{"name":"CI 中间人更新","contact":"13900000000","region":"杭州市"},partner_token,"PUT")
assert updated["name"]=="CI 中间人更新" and updated["contact"]=="13900000000" and updated["region"]=="杭州市"
assert call("/business/partners?q="+quote("上海市"),token=partner_token)==[]
