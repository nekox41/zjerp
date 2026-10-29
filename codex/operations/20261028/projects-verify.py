import json,getpass,urllib.request,urllib.error,urllib.parse,time,secrets,re,concurrent.futures
from pathlib import Path
base='http://127.0.0.1:18089/prod-api';passed=[]
def req(path,data=None,token=None,method=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 r=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(r,timeout=40) as response:return json.load(response)
 except urllib.error.HTTPError as e:
  try:return json.load(e)
  except:return {'code':e.code}
def good(r):assert r.get('code')==200,r;return r
def mark(s):passed.append(s);print('PASS',s,flush=True)
token=good(req('/login',dict(username='admin',password=getpass.getpass('Isolated administrator password: '))))['token']
assert 'work_project_stage_20261028' in good(req('/getInfo',token=token))['roles'],'Refusing mutations outside isolated stage'
prefix='/management/work-project';suffix=str(int(time.time()))
menus=good(req('/system/menu/list',token=token))['data'];brand=next(x for x in menus if x['menuName']=='众基消防');folder=next(x for x in menus if x['menuName']=='项目管理' and x['parentId']==brand['menuId']);pages=[x for x in menus if x['parentId']==folder['menuId'] and x['menuType']=='C'];assert len(pages)==5
assert not any(x.get('component') in ['project/code/index','management/projectSystem/index'] for x in menus)
allprojects=good(req(prefix+'/list?pageSize=1',token=token));assert allprojects['total']==200
for code,n in [('WB',1),('JC',126),('PD',66),('PG',5),('QT',2)]:assert good(req(prefix+'/list?projectType='+code+'&pageSize=1',token=token))['total']==n
assert 'manager' not in good(req('/management/contract/list?pageSize=1',token=token))['rows'][0]
mark('five project menus, 200 migrated projects and removal of contract manager field')
created=[];contracts={}
for code in ['WB','JC','PD','PG','QT']:
 contracts[code]=good(req(prefix+'/contracts?projectType='+code+'&pageSize=1&name=',token=token))['rows'][0]['id']
 body=dict(projectType=code,projectName='Stage project '+code+' '+suffix,contractId=contracts[code],leaderUserId=1,technicalUserId=1,startDate='2026-10-01',endDate='2026-12-31')
 row=good(req(prefix,body,token))['data'];created.append(row)
 assert re.fullmatch(r'ZJXF\d{6}'+code+r'\d{3,}',row['projectNo']);assert row['contractId']==contracts[code]
 if code=='JC':assert int(re.search(r'JC(\d+)$',row['projectNo'])[1])==127
 assert 'projectNo' in row and row['leaderUserId']==1
mark('new projects require correct contracts; historical annual number sequences continued')
def concurrent_create(n):return good(req(prefix,dict(projectType='WB',projectName='Concurrent '+str(n)+' '+suffix,contractId=contracts['WB']),token))['data']
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:concurrent_rows=list(pool.map(concurrent_create,range(3)))
assert len(set(x['projectNo'] for x in created+concurrent_rows))==8
mark('three concurrent requests allocated unique project numbers transactionally')
assert req(prefix,dict(projectType='JC',projectName='Wrong contract',contractId=contracts['WB']),token)['code']!=200
assert req(prefix,dict(projectType='WB',projectName='No contract'),token)['code']!=200
assert req(prefix,dict(projectType='WB',projectName='Wrong dates',contractId=contracts['WB'],startDate='2026-12-10',endDate='2026-01-01'),token)['code']!=200
first=created[0]['id'];second=created[1]['id']
def get(id=first):return good(req(prefix+'/'+str(id),token=token))['data']
def section(name,data,version=None):return req(prefix+'/'+str(first)+'/section/'+name,{**data,'version':get()['version'] if version is None else version},token,'PUT')
v=get()['version'];good(section('details',{'details':{'buildingOverview':'Two buildings','buildingArea':'1200','fireFacilities':'Alarm system','reportBasis':'Approved criteria'}}));assert json.loads(get()['detailsJson'])['buildingArea']=='1200'
assert section('notes',{'remark':'Stale overwrite'},v)['code']!=200
good(section('registration',{'registration':{'visitStatus':'visited','visitDate':'2026-10-28','reportStatus':'in_progress','reportNo':'REPORT-TEST','notes':'Stage'}}));assert json.loads(get()['registrationJson'])['reportStatus']=='in_progress'
good(section('notes',{'remark':'Project notes'}));assert get()['remark']=='Project notes'
body=get();body['siteAddress']='Updated site';good(section('basic',body));assert get()['siteAddress']=='Updated site'
assert section('registration',{'registration':{'visitStatus':'made_up','reportStatus':'unknown'}})['code']!=200
mark('basic, details, registration and notes save independently; stale versions rejected')
issue=dict(title='Stage site issue',description='Inspection issue',severity='high',status='open',foundDate='2026-10-28',files='')
good(req(prefix+'/'+str(first)+'/issues',issue,token));issues=good(req(prefix+'/'+str(first)+'/issues',token=token))['rows'];iid=issues[0]['id'];issue.update(id=iid,status='resolved',resolvedDate='2026-10-29');good(req(prefix+'/'+str(first)+'/issues',issue,token))
assert req(prefix+'/'+str(second)+'/issues',issue,token)['code']!=200
assert req(prefix+'/'+str(second)+'/issues/'+str(iid),token=token,method='DELETE')['code']!=200
good(req(prefix+'/'+str(first)+'/issues/'+str(iid),token=token,method='DELETE'))
plan=dict(title='Visit plan',planType='visit',startDate='2026-11-01',endDate='2026-11-02',status='planned',assigneeId=1,remark='Stage')
good(req(prefix+'/'+str(first)+'/plans',plan,token));pid=good(req(prefix+'/'+str(first)+'/plans',token=token))['rows'][0]['id'];plan.update(id=pid,status='done');good(req(prefix+'/'+str(first)+'/plans',plan,token))
assert req(prefix+'/'+str(second)+'/plans',plan,token)['code']!=200
good(req(prefix+'/'+str(first)+'/plans/'+str(pid),token=token,method='DELETE'))
mark('site issues and schedules create/update/delete; cross-project mutations rejected')
legacy=good(req(prefix+'/list?projectType=QT&pageSize=50',token=token))['rows'];expense_project=next(x for x in legacy if str(x.get('legacyNumber') or '')=='' and x.get('numberPending'))
expense=good(req(prefix+'/'+str(expense_project['id'])+'/expenses',token=token));assert expense['total']==1 and float(expense['summary']['totalAmount'])==100;assert float(json.loads(expense['legacySummary'][0]['snapshotJson'])['project_cost'])==0
assert req('/management/contract/'+str(contracts['WB']),token=token,method='DELETE')['code']!=200
mark('historical expense detail and original ledger preserved separately; contracts with projects protected')
# A project reader may view project information but cannot edit or read financial summaries.
query=next(x for x in menus if x.get('perms')=='management:project:query');ids=[x['menuId'] for x in pages]+[query['menuId'],brand['menuId'],folder['menuId']]
rolekey='wp_'+suffix;rolebody=dict(roleName='Project reader '+suffix,roleKey=rolekey,roleSort=60,status='0',menuCheckStrictly=True,menuIds=ids);good(req('/system/role',rolebody,token));rid=next(x['roleId'] for x in good(req('/system/role/list?roleKey='+rolekey,token=token))['rows'] if x['roleKey']==rolekey)
uname='wp_'+suffix;pw=secrets.token_hex(8);good(req('/system/user',dict(userName=uname,nickName='Stage project reader',password=pw,status='0',roleIds=[rid]),token));reader=good(req('/login',dict(username=uname,password=pw)))['token']
good(req(prefix+'/list',token=reader));good(req(prefix+'/'+str(first),token=reader));assert req(prefix+'/'+str(first)+'/expenses',token=reader)['code']==403;assert req(prefix,dict(projectType='WB'),reader)['code']==403
good(req('/logout',{},reader));mark('project reader permissions enforced; expense access requires its own role permission')
good(req('/logout',{},token))
result=dict(success=True,passed=passed,tests=len(passed));Path('/vol2/zjerp-migration/projects/check/api-validation.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
