#!/usr/bin/env python3
"""Writes fixtures ONLY to an isolated DB carrying finance_ledger_stage_20261029.
Run with an isolated application's URL, matching database and MySQL container.
Requires Python 3; prompts for the existing administrator password.
"""
import argparse,json,subprocess,getpass,urllib.request,urllib.error,urllib.parse,uuid,concurrent.futures
from decimal import Decimal
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--url',required=True);p.add_argument('--database',required=True);p.add_argument('--mysql-container',required=True);p.add_argument('--report',required=True);p.add_argument('--token-file');a=p.parse_args()
assert a.database.startswith('finance_ledger_check_') and a.database.replace('_','').isalnum()
def sql(s):return subprocess.check_output(['docker','exec','-i',a.mysql_container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N --batch --raw "$1"','sh',a.database],input=s.encode()).decode().strip()
assert sql("SELECT COUNT(*) FROM sys_role WHERE role_key='finance_ledger_stage_20261029'")=='1'
def req(path,data=None,method=None,auth=True):
 headers={'Content-Type':'application/json'}
 if auth and token:headers['Authorization']='Bearer '+token
 request=urllib.request.Request(a.url.rstrip('/')+urllib.parse.quote(path,safe='/?=&:'),headers=headers,data=None if data is None else json.dumps(data).encode(),method=method)
 try:
  with urllib.request.urlopen(request,timeout=60) as response:return json.load(response)
 except urllib.error.HTTPError as e:return json.load(e)
def good(v):assert v.get('code')==200,v;return v
def no(v):assert v.get('code')!=200,v
def data(path):return good(req(path))['data']
def post(kind,v):return good(req('/finance/ledger/'+kind,v))['data']
def tokenid():return str(uuid.uuid4())
passed=[]
def mark(s):passed.append(s);print('PASS',s,flush=True)
token=None;password=getpass.getpass('Isolated administrator password: ');token=good(req('/login',dict(username='admin',password=password),auth=False))['token'];assert 'finance_ledger_stage_20261029' in good(req('/getInfo'))['roles']
if a.token_file:target=Path(a.token_file);target.write_text(token);target.chmod(0o600)
users=good(req('/finance/public/users',auth=False))['data'];assert users and all(x['id']!=1 for x in users);uid=users[0]['id'];name=users[0]['name'];assert uid in [x['id'] for x in good(req('/finance/public/users?name='+name[:1],auth=False))['data']]
assert not good(req('/finance/public/users?name=admin',auth=False))['data'];projects=good(req('/finance/public/projects',auth=False))['data'];assert projects;pid=projects[0]['id'];assert any(x['id']==pid for x in good(req('/finance/public/projects?name='+projects[0]['code'],auth=False))['data'])
mark('Public empty/one-character/name/code choices work and admin is excluded')
def claim(user,amount='18.50'):
 boundary='----'+uuid.uuid4().hex;fields=dict(clientToken=tokenid(),amount=amount,purpose='Finance isolated claim',expenseDate='2026-10-29',userId=user);body=''.join('--'+boundary+'\r\nContent-Disposition: form-data; name="'+k+'"\r\n\r\n'+str(v)+'\r\n' for k,v in fields.items())+'--'+boundary+'--\r\n';request=urllib.request.Request(a.url+'/finance/public/submit',data=body.encode(),headers={'Content-Type':'multipart/form-data; boundary='+boundary})
 try:return json.load(urllib.request.urlopen(request))
 except urllib.error.HTTPError as ex:return json.load(ex)
no(claim(1));receipt=good(claim(uid))['data']['receipt'];cid=int(sql("SELECT id FROM zj_reimbursement WHERE receipt='"+receipt+"'"));mark('Server rejects admin as claimant; ordinary anonymous claim succeeds')
account=post('accounts',dict(name='Isolated account A',openingBalance='1000.00',active=True));other=post('accounts',dict(name='Isolated account B',openingBalance='100.00',active=True))
def balance(key):return Decimal(next(x for x in data('/finance/ledger/overview')['accounts'] if x['id']==key)['balance'])
def tx(**kwargs):return dict(clientToken=tokenid(),accountId=account,direction='income',amount='10.10',transactionDate='2026-10-29',purpose='Isolated transaction',**kwargs)
# Build an issued invoice linked to an actual project's contract when available.
linked=sql('SELECT id FROM zj_work_project WHERE contract_id IS NOT NULL LIMIT 1');project=int(linked) if linked else None
invoice=dict(clientToken=tokenid(),invoiceNo='ISOLATED-'+uuid.uuid4().hex,buyerName='Isolated buyer',invoiceDate='2026-10-29',amount='100.10',taxAmount='3.00',projectId=project)
inv=post('invoices',invoice);assert post('invoices',invoice)==inv;bad=dict(invoice,amount='100.20');no(req('/finance/ledger/invoices',bad));no(req('/finance/ledger/invoices',dict(invoice,clientToken=tokenid())));assert data('/finance/ledger/invoices/'+str(inv))['amount']=='100.10';mark('Invoice amount, unique active number, association and idempotent retry')
payment=tx();payment['invoiceId']=inv;payment['amount']='40.10';tid=post('transactions',payment);assert post('transactions',payment)==tid;assert balance(account)==Decimal('1040.10');assert data('/finance/ledger/invoices/'+str(inv))['receivedAmount']=='40.10';no(req('/finance/ledger/transactions',dict(payment,amount='99.99')));mark('Invoice partial receipt updates account balance once; changed retry rejected')
no(req('/finance/ledger/accounts',dict(id=account,version=0,name='A',openingBalance='999.99',active=True)));no(req('/finance/ledger/transactions',dict(tx(),amount='1.001')));no(req('/finance/ledger/transactions',dict(tx(),amount='0')));mark('Opening balance locked after first entry and invalid money rejected')
transfer=dict(tx(),direction='transfer',toAccountId=other,amount='50.00');transferid=post('transactions',transfer);assert balance(account)==Decimal('990.10') and balance(other)==Decimal('150.00');before=data('/finance/ledger/overview')['cash'];assert before['income']=='40.10' and before['expense']=='0.00';good(req('/finance/ledger/transactions/'+str(transferid)+'/void',dict(version=0,reason='Isolated reversal'),method='PUT'));assert balance(account)==Decimal('1040.10') and balance(other)==Decimal('100.00');no(req('/finance/ledger/transactions/'+str(transferid)+'/void',dict(version=0,reason='Repeated'),method='PUT'));mark('Atomic internal transfer, cash-summary exclusion and paired versioned void')
settle=dict(tx(),direction='expense',reimbursementId=cid,amount='18.50');ids=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:req('/finance/ledger/transactions',dict(settle,clientToken=tokenid())),range(2)))
assert sum(x.get('code')==200 for x in results)==1;paidid=next(x['data'] for x in results if x.get('code')==200);assert sql('SELECT status FROM zj_reimbursement WHERE id='+str(cid))=='paid';assert balance(account)==Decimal('1021.60');no(req('/finance/reimbursements/'+str(cid)+'/status',dict(status='pending',version=1),method='PUT'));good(req('/finance/ledger/transactions/'+str(paidid)+'/void',dict(version=0,reason='Isolated payment reversal'),method='PUT'));assert sql('SELECT status FROM zj_reimbursement WHERE id='+str(cid))=='pending';assert balance(account)==Decimal('1040.10');mark('Concurrent reimbursement payment posts once; void restores pending claim and balance')
no(req('/finance/ledger/invoices/'+str(inv)+'/void',dict(version=0,reason='Cannot void paid invoice'),method='PUT'));good(req('/finance/ledger/transactions/'+str(tid)+'/void',dict(version=0,reason='Receipt reversed'),method='PUT'));good(req('/finance/ledger/invoices/'+str(inv)+'/void',dict(version=0,reason='Correct invoice record'),method='PUT'));assert balance(account)==Decimal('1000.00');newinv=post('invoices',dict(invoice,clientToken=tokenid(),amount='101.10'));assert newinv!=inv;assert data('/finance/ledger/invoices/'+str(inv))['status']=='void';mark('Invoice with receipts protected; void retains audit and allows corrected replacement')
for resource in ['overview','accounts','transactions','invoices','contracts','projects']:no(req('/finance/ledger/'+resource,auth=False));
mark('Anonymous clients cannot access any financial ledger resource')
for resource in ['accounts','transactions','invoices','contracts','projects']:
 response=good(req('/finance/ledger/'+resource+'?pageNum=1&pageSize=1'));assert len(response['rows'])<=1
mark('All five ledger resources paginate on the server')
if project:
 contract=int(sql('SELECT contract_id FROM zj_work_project WHERE id='+str(project)));invoices=good(req('/finance/ledger/invoices?contractId='+str(contract)))['rows'];assert any(x['id']==newinv for x in invoices);wrong=int(sql('SELECT id FROM zj_contract_identity WHERE id<>'+str(contract)+' LIMIT 1'));no(req('/finance/ledger/invoices',dict(invoice,clientToken=tokenid(),invoiceNo='BAD-'+tokenid(),contractId=wrong)));mark('Derived contract linkage and mismatched project/contract rejection')
overview=data('/finance/ledger/overview');assert overview['invoices']['amount']=='101.10' and overview['cash']['income']=='0.00' and overview['cash']['expense']=='0.00';mark('Effective financial totals exclude voids and do not count invoice/claim as cash')
sql("INSERT INTO sys_role(role_name,role_key,role_sort,status,del_flag) VALUES('Isolated finance reader','finance_ledger_reader',999,'0','0'); SET @reader=LAST_INSERT_ID(); INSERT INTO sys_role_menu SELECT @reader,menu_id FROM sys_menu WHERE component='zhongji/finance/workspace'; INSERT INTO sys_user(user_name,nick_name,password,status,del_flag) SELECT 'fin_reader','Isolated reader',password,'0','0' FROM sys_user WHERE user_id=1; INSERT INTO sys_user_role VALUES(LAST_INSERT_ID(),@reader);")
administrator=token;token=good(req('/login',dict(username='fin_reader',password=password),auth=False))['token'];good(req('/finance/ledger/overview'));no(req('/finance/ledger/accounts',dict(name='Forbidden account',openingBalance='0',active=True)));token=administrator;mark('Read-only finance role can view but cannot create financial entries')
image=json.loads(subprocess.check_output(['docker','inspect','zjerp-finance-ledger-check-backend']))[0]['Image'];Path(a.report).write_text(json.dumps(dict(success=True,checks=passed,groups=len(passed),backendImage=image),ensure_ascii=False));print('Finance ledger integration checks passed')
