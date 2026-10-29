import getpass,json,urllib.request
from pathlib import Path
base='http://127.0.0.1:8088/prod-api'
def get(path,token=None):
 r=urllib.request.Request(base+path,headers={'Authorization':'Bearer '+token} if token else {})
 with urllib.request.urlopen(r,timeout=30) as response:return json.load(response)
def auth(path,data,token=None):
 r=urllib.request.Request(base+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})})
 with urllib.request.urlopen(r,timeout=30) as response:return json.load(response)
assert get('/health')['code']==200
login=auth('/login',dict(username='admin',password=getpass.getpass('Production administrator password: ')));assert login['code']==200;t=login['token']
assert 'stage_probe_20261028' not in get('/getInfo',t)['roles']
menus=get('/system/menu/list',t)['data'];root=next(x for x in menus if x['menuName']=='众基消防');assert root['parentId']==0 and root['orderNum']==0
folder=next(x for x in menus if x['menuName']=='合同管理' and x['parentId']==root['menuId']);assert len([x for x in menus if x['parentId']==folder['menuId'] and x['menuType']=='C'])==5
expected=json.loads(Path('/vol2/zjerp-migration/contracts/production-update/result.json').read_text())['counts'];counts={}
for code,n in expected.items():
 r=get('/management/contract/list?contractType='+code+'&pageSize=1',t);assert r['code']==200 and r['total']==n;counts[code]=n
 if r['rows']:
  detail=get('/management/contract/'+str(r['rows'][0]['id']),t);assert detail['code']==200 and detail['data']['ownership']=='pending'
assert get('/management/intermediary/list',t)['code']==200
assert get('/system/user/1/materials',t)['code']==200
files=get('/system/file/list?pageSize=1',t);assert files['code']==200 and files['total']==1574
assert auth('/logout',{},t)['code']==200
result=dict(success=True,read_only_business_checks=True,counts=counts,file_records=files['total'],five_routes=True,user_materials_preserved=True)
Path('/vol2/zjerp-migration/contracts/production-update/api-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
