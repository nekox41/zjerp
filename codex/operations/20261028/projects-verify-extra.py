import getpass,json,urllib.request,urllib.error,base64,re
from pathlib import Path
base='http://127.0.0.1:18089/prod-api'
def req(path,data=None,t=None,method=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 r=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(r,timeout=30) as s:return json.load(s)
 except urllib.error.HTTPError as e:return json.load(e)
def good(r):assert r.get('code')==200,r;return r
t=good(req('/login',dict(username='admin',password=getpass.getpass('Isolated administrator password: '))))['token'];assert 'work_project_stage_20261028' in good(req('/getInfo',t=t))['roles']
prefix='/management/work-project'
rows=good(req(prefix+'/list?projectType=QT&pageSize=50',t=t))['rows'];old=next(x for x in rows if x.get('classificationPending') and x.get('numberPending') and x.get('legacyNumber'))
id=old['id'];p=good(req(prefix+'/'+str(id),t=t))['data'];legacy=p['legacyNumber'];contract=good(req(prefix+'/contracts?projectType=JC&name=&pageSize=1',t=t))['rows'][0]['id']
p.update(projectType='JC',contractId=contract,classificationPending=False);good(req(prefix+'/'+str(id)+'/section/basic',p,t,'PUT'))
p=good(req(prefix+'/'+str(id)+'/apply-number',{},t))['data'];assert re.fullmatch(r'ZJXF\d{6}JC\d+',p['projectNo']) and p['legacyNumber']==legacy and not p['numberPending'] and not p['classificationPending']
assert req(prefix+'/'+str(id)+'/apply-number',{},t)['code']!=200
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=');boundary='ProjectIssueUploadBoundary';body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="site.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+('\r\n--'+boundary+'--\r\n').encode()
r=urllib.request.Request(base+'/common/upload',data=body,headers={'Authorization':'Bearer '+t,'Content-Type':'multipart/form-data; boundary='+boundary})
with urllib.request.urlopen(r) as s:stored=good(json.load(s))
url=stored['fileName'];good(req(prefix+'/'+str(id)+'/issues',dict(title='Stage photo issue',description='Photo validation',severity='low',status='open',files=url),t));issue=good(req(prefix+'/'+str(id)+'/issues',t=t))['rows'][0];assert issue['files']==url
with urllib.request.urlopen('http://127.0.0.1:18089'+url) as s:assert s.read()==png
good(req(prefix+'/'+str(id)+'/issues/'+str(issue['id']),t=t,method='DELETE'))
cost=next(x for x in rows if not x.get('legacyNumber') and x.get('numberPending'));e=good(req(prefix+'/'+str(cost['id'])+'/expenses',t=t));assert len(e['users'])==1 and float(e['users'][0]['totalAmount'])==100
good(req('/logout',{},t));path=Path('/vol2/zjerp-migration/projects/check/api-validation.json');result=json.loads(path.read_text());result['passed']+=['legacy classification confirmation and formal numbering preserve original number','site issue PNG upload, association, download and unlink verified','per-user expense aggregation verified'];result['tests']=len(result['passed']);path.write_text(json.dumps(result,indent=2));print('Additional legacy numbering, photo and user expense tests passed')
