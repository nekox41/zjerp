import json,getpass,urllib.request
from pathlib import Path
root=Path('/vol2/zjerp-migration/projects');base='http://127.0.0.1:8088/prod-api'
def get(path,t=None):
 r=urllib.request.Request(base+path,headers={'Authorization':'Bearer '+t} if t else {})
 with urllib.request.urlopen(r,timeout=30) as s:return json.load(s)
def auth(path,data,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 with urllib.request.urlopen(urllib.request.Request(base+path,data=json.dumps(data).encode(),headers=h),timeout=30) as s:return json.load(s)
assert get('/health')['code']==200
r=auth('/login',dict(username='admin',password=getpass.getpass('Production administrator password: ')));assert r['code']==200;t=r['token']
assert 'work_project_stage_20261028' not in get('/getInfo',t)['roles']
stats=json.loads((root/'production-update/result.json').read_text())['stats'];menus=get('/system/menu/list',t)['data'];brand=next(x for x in menus if x['menuName']=='众基消防');folder=next(x for x in menus if x['menuName']=='项目管理' and x['parentId']==brand['menuId']);assert len([x for x in menus if x['parentId']==folder['menuId'] and x['menuType']=='C'])==5
assert not any(x.get('component') in ['project/code/index','management/projectSystem/index'] for x in menus)
prefix='/management/work-project';counts={}
for code,n in stats['counts'].items():
 r=get(prefix+'/list?projectType='+code+'&pageSize=1',t);assert r['code']==200 and r['total']>=n;counts[code]=r['total']
 if r['rows']:
  id=r['rows'][0]['id'];assert get(prefix+'/'+str(id),t)['code']==200
  assert get(prefix+'/'+str(id)+'/issues',t)['code']==200
  assert get(prefix+'/'+str(id)+'/plans',t)['code']==200
r=get('/management/contract/list?pageSize=1',t);assert r['code']==200 and r['total']==380 and 'manager' not in r['rows'][0]
people=get('/management/intermediary/list?pageSize=1',t);assert people['code']==200
qt=get(prefix+'/list?projectType=QT&pageSize=50',t)['rows'];cost=next(x for x in qt if x['projectNo'].startswith('LEGACY-ZJ_PROJECT_COST-'))
expense=get(prefix+'/'+str(cost['id'])+'/expenses',t);assert expense['code']==200 and expense['total']==1 and float(expense['summary']['totalAmount'])==100 and float(expense['users'][0]['totalAmount'])==100
files=get('/system/file/list?pageSize=1',t);assert files['code']==200
assert get('/system/user/1/materials',t)['code']==200
assert auth('/logout',{},t)['code']==200
result=dict(success=True,read_only_business_checks=True,counts=counts,intermediaries=people['total'],file_records=files['total'],historical_expenses_preserved=True,user_expense_summary=True,contract_manager_removed=True)
(root/'production-update/api-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
