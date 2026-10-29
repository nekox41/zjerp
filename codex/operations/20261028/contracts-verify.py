import getpass,json,secrets,time,urllib.request,urllib.error,urllib.parse,sys
from pathlib import Path
port=int(sys.argv[1]) if len(sys.argv)>1 else 18089
base=f'http://127.0.0.1:{port}/prod-api';passed=[]
def req(path,data=None,token=None,method=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 r=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
 try:
  with urllib.request.urlopen(r,timeout=30) as response:return json.load(response)
 except urllib.error.HTTPError as e:
  try:return json.load(e)
  except:return {'code':e.code}
def good(r):assert r.get('code')==200,r;return r
def mark(s):passed.append(s);print('PASS',s,flush=True)
password=getpass.getpass('Administrator password: ')
token=good(req('/login',dict(username='admin',password=password)))['token']
info=good(req('/getInfo',token=token))
assert ('stage_probe_20261028' in info['roles'])==(port==18089)
assert req('/management/contract/list')['code']!=200
menus=good(req('/system/menu/list',token=token))['data']
top=next(x for x in menus if x['menuName']=='众基消防');assert top['parentId']==0 and top['orderNum']==0
folder=next(x for x in menus if x['menuName']=='合同管理' and x['parentId']==top['menuId'])
assert len([x for x in menus if x['parentId']==folder['menuId'] and x['menuType']=='C'])==5
assert any(x['menuName']=='中间人管理' and x['parentId']==top['menuId'] for x in menus)
mark('top directory, intermediary and five contract routes verified')
for t,count in [('WB',301),('JC',47),('PD',25),('PG',2),('QT',5)]:
 r=good(req('/management/contract/list?contractType='+t+'&pageSize=1',token=token));assert r['total']==count,(t,r['total']);assert len(r['rows'])==1
mark('all 380 historical contracts correctly distributed; server pagination verified')
if port==18089:
 # SQL migration has already verified every original field and all file references.
 suffix=str(int(time.time()))
 person=good(req('/management/intermediary',dict(name='Validation '+suffix,phone='000000',company='Test only',email='test@example.invalid',remark='isolated validation'),token))['data']
 pid=person['id'];person['phone']='000001';good(req('/management/intermediary',person,token,'PUT'))
 assert good(req('/management/intermediary/'+str(pid),token=token))['data']['phone']=='000001'
 ids=[]
 for t in ['WB','JC','PD','PG','QT']:
  body=dict(contractId='TEST-'+t+'-'+suffix,contractType=t,contractName='Validation '+t,amount='1234.56',ownership='intermediary',intermediaryId=pid,paymentPlan=json.dumps([dict(name='First',amount='234.56',dueDate='2026-12-01')]),contractFile='',remark='Isolated')
  good(req('/management/contract',body,token))
  row=good(req('/management/contract/list?contractId='+body['contractId'],token=token))['rows'][0];ids.append(row['id'])
  detail=good(req('/management/contract/'+str(row['id']),token=token))['data'];assert detail['amount'].startswith('1234.56') and detail['intermediaryName']==person['name']
  detail.update(ownership='company',intermediaryId=None,remark='Updated');good(req('/management/contract',detail,token,'PUT'))
  assert good(req('/management/contract/'+str(row['id']),token=token))['data']['intermediaryId'] is None
  detail['contractType']='BAD';assert req('/management/contract',detail,token,'PUT')['code']!=200
 assert len(set(ids))==5
 mark('all five modules create/read/update, global IDs and intermediary ownership work')
 bad=dict(contractId='BAD',contractType='WB',contractName='Bad',amount='10',ownership='intermediary',intermediaryId=99999999)
 assert req('/management/contract',bad,token)['code']!=200
 bad.update(ownership='company',paymentPlan=json.dumps([dict(name='Over',amount='20')]))
 assert req('/management/contract',bad,token)['code']!=200
 bad.update(paymentPlan='[]',contractType='WB;DROP TABLE sys_user')
 assert req('/management/contract',bad,token)['code']!=200
 mark('invalid ownership, over-budget payment plan and unsupported type rejected')
 # Unprivileged user cannot read or modify contracts or intermediaries.
 uname='ct_'+suffix;pw=secrets.token_hex(8)
 good(req('/system/user',dict(userName=uname,nickName='Contract probe',password=pw,status='0',roleIds=[]),token))
 limited=good(req('/login',dict(username=uname,password=pw)))['token']
 for path in ['/management/contract/list','/management/intermediary/list']:
  assert req(path,token=limited)['code']==403
 assert req('/management/contract',dict(contractType='WB'),limited)['code']==403
 good(req('/logout',{},limited));mark('role permission checks enforced for unauthorised accounts')
 # Reference guard: attach one contract again then attempt deleting the intermediary.
 detail=good(req('/management/contract/'+str(ids[0]),token=token))['data'];detail.update(ownership='intermediary',intermediaryId=pid);good(req('/management/contract',detail,token,'PUT'))
 assert req('/management/intermediary/'+str(pid),token=token,method='DELETE')['code']!=200
 for id in ids:good(req('/management/contract/'+str(id),token=token,method='DELETE'))
 good(req('/management/intermediary/'+str(pid),token=token,method='DELETE'))
 assert good(req('/management/contract/list',token=token))['total']==380
 mark('referenced intermediary protected; contract and intermediary deletion verified')
good(req('/logout',{},token))
result=dict(success=True,passed=passed,port=port)
path=Path('/vol2/zjerp-migration/contracts')/('check/api-validation.json' if port==18089 else 'production-update/api-verification.json');path.write_text(json.dumps(result,indent=2))
print(json.dumps(result),flush=True)
