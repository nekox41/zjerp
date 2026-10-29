import json,hashlib,subprocess,time
from pathlib import Path
from decimal import Decimal
root=Path('/vol2/zjerp-migration');change=root/'contracts';base=root/'source/zjerp_backend-master';report=change/'production-update';report.mkdir(mode=0o700,exist_ok=True)
assert json.loads((change/'check/api-validation.json').read_text())['success']
assert json.loads((change/'check/schema-validation.json').read_text())['success']
assert json.loads((change/'check/fresh-install-validation.json').read_text())['success']
assert not (report/'result.json').exists(),'Release already applied'
def run(a,**kw):return subprocess.run(a,check=True,**kw)
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],input=s.encode()).decode()
def allrows(table):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;').splitlines()]
 return [json.loads(x,parse_float=Decimal) for x in sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in cols)+') FROM `'+table+'`;').splitlines()]
def integrity(tables):
 result={}
 for table in tables:
  cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;').splitlines()]
  rows=sql('SELECT JSON_ARRAY('+','.join('`'+x+'`' for x in cols)+') FROM `'+table+'`;').splitlines()
  result[table]=dict(rows=len(rows),sha256=hashlib.sha256('\n'.join(sorted(rows)).encode()).hexdigest())
 return result
tags={}
for kind in ['backend','frontend']:
 image=json.loads(subprocess.check_output(['docker','image','inspect','zjerp-'+kind+':contracts-20261028']))[0]['Id']
 assert image==json.loads(subprocess.check_output(['docker','inspect','zjerp-contract-check-'+kind]))[0]['Image']
 tags[kind]='zjerp-'+kind+':20261028-contracts-'+image.split(':')[1][:12];run(['docker','tag',image,tags[kind]])
env=base/'.env';override=base/'compose.override.yaml'
(report/'before.env').write_bytes(env.read_bytes());(report/'before.env').chmod(0o600);(report/'before.override.yaml').write_bytes(override.read_bytes())
run(['docker','compose','stop','frontend','backend'],cwd=base)
run(['python3',str(root/'migration-post-backup-B.py')])
backup=(root/'final/post-migration-backup-path.txt').read_text().strip();(report/'backup-path.txt').write_text(backup)
print('Backup ready: '+backup,flush=True)
preserved=[t for t in sql('SHOW TABLES;').splitlines() if t not in ['sys_menu','sys_role_menu','zj_contract']]
before=integrity(preserved);original=allrows('zj_contract')
(report/'before-integrity.json').write_text(json.dumps(before,indent=2))
assert 'zj_contract_legacy_20261028' not in sql('SHOW TABLES;').splitlines()
for file in ['zhongji_contract_upgrade.sql','zhongji_contract_menus.sql']:sql((change/'source/zjerp_backend-master/sql'/file).read_text())
after=integrity(preserved);assert before==after,'Existing data mismatch; apps remain stopped'
assert allrows('zj_contract_legacy_20261028')==original
new={x['id']:x for x in allrows('zj_contract')};assert len(new)==len(original)
for old in original:
 record=new[old['id']]
 for k,v in old.items():
  if k=='contract_type':assert record[k]==(v if v in ['WB','JC','PD','PG'] else 'QT')
  elif k=='amount':assert v is None and record[k] is None or v is not None and abs(Decimal(str(v))-Decimal(str(record[k])))<=Decimal('0.000000000001')
  else:assert record[k]==v,(old['id'],k)
 assert record['ownership']=='pending' and record['legacy_type']==old['contract_type']
counts={t:int(sql("SELECT COUNT(*) FROM zj_contract WHERE contract_type='"+t+"';")) for t in ['WB','JC','PD','PG','QT']}
(report/'after-integrity.json').write_text(json.dumps(after,indent=2))
values=dict(x.split('=',1) for x in env.read_text().splitlines() if '=' in x)
values.update(BACKEND_IMAGE=tags['backend'],FRONTEND_IMAGE=tags['frontend'],FRONTEND_CONTEXT=str(change/'source/zjerp_frontend-master'))
env.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));env.chmod(0o600)
override.write_text(override.read_text().replace('/user-materials/source/','/contracts/source/'))
run(['docker','compose','config','--quiet'],cwd=base)
run(['docker','compose','up','-d','--no-build','--wait','--wait-timeout','180','backend','frontend'],cwd=base)
(report/'result.json').write_text(json.dumps(dict(success=True,backup=backup,images=tags,counts=counts,contracts=len(original),preserved_tables=len(after),archive_unchanged=True,opened_at=time.strftime('%Y-%m-%d %H:%M:%S %z')),indent=2))
print('Contract modules deployed; existing data verified',flush=True)
