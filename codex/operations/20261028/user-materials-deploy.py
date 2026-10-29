import subprocess,json,hashlib,time
from pathlib import Path
root=Path('/vol2/zjerp-migration'); change=root/'user-materials'; base=root/'source/zjerp_backend-master'; report=change/'production-update';report.mkdir(exist_ok=True)
assert (change/'check/verified.json').exists(),'Isolated verification required'
assert not (report/'result.json').exists(),'Already deployed'
def run(args,**kw):return subprocess.run(args,check=True,**kw)
def sql(s):
 return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],input=s.encode()).decode()
def integrity():
 result={}
 for table in sql('SHOW TABLES;').splitlines():
  if table=='sys_user_material':continue
  cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;').splitlines()]
  rows=sql('SELECT JSON_ARRAY('+','.join('`'+x+'`' for x in cols)+') FROM `'+table+'`;').splitlines()
  result[table]=dict(rows=len(rows),hash=hashlib.sha256('\n'.join(sorted(rows)).encode()).hexdigest())
 return result
env=base/'.env'; override=base/'compose.override.yaml'
(report/'before.env').write_bytes(env.read_bytes());(report/'before.env').chmod(0o600)
(report/'before.override.yaml').write_bytes(override.read_bytes())
run(['docker','compose','stop','frontend','backend'],cwd=base)
try:
 run(['python3',str(root/'migration-post-backup-B.py')])
 backup=(root/'final/post-migration-backup-path.txt').read_text().strip()
 before=integrity();sql((change/'source/zjerp_backend-master/sql/user_material_upgrade.sql').read_text());after=integrity();assert before==after
 (report/'integrity.json').write_text(json.dumps(after,indent=2))
 values=dict(x.split('=',1) for x in env.read_text().splitlines() if '=' in x)
 tags={}
 for kind in ['backend','frontend']:
  image=json.loads(subprocess.check_output(['docker','image','inspect','zjerp-'+kind+':user-materials-20261028']))[0]['Id']
  tags[kind]='zjerp-'+kind+':20261028-materials-'+image.split(':')[1][:12];run(['docker','tag',image,tags[kind]])
 values.update(BACKEND_IMAGE=tags['backend'],FRONTEND_IMAGE=tags['frontend'],FRONTEND_CONTEXT=str(change/'source/zjerp_frontend-master'))
 env.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));env.chmod(0o600)
 override.write_text(override.read_text().replace('/role-only/source/','/user-materials/source/'))
 run(['docker','compose','config','--quiet'],cwd=base)
 run(['docker','compose','up','-d','--no-build','--wait','--wait-timeout','180','backend','frontend'],cwd=base)
 (report/'result.json').write_text(json.dumps(dict(success=True,backup=backup,images=tags,preserved_tables=len(after),opened_at=time.strftime('%Y-%m-%d %H:%M:%S %z')),indent=2))
 print('Production deployed; backup='+backup,flush=True)
except Exception:
 env.write_bytes((report/'before.env').read_bytes());override.write_bytes((report/'before.override.yaml').read_bytes())
 run(['docker','compose','up','-d','--no-build','--wait','backend','frontend'],cwd=base)
 raise
