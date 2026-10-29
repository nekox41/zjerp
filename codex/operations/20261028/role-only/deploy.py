import base64,hashlib,json,os,re,secrets,subprocess,time
from pathlib import Path

root=Path('/vol2/zjerp-migration');change=root/'role-only';base=root/'source/zjerp_backend-master'
report=change/'production-update';report.mkdir(mode=0o700,exist_ok=True)
if (report/'result.json').exists() and json.loads((report/'result.json').read_text()).get('success'):
    raise SystemExit('This release is already applied; refusing to repeat the production update')
assert json.loads((change/'check/api-validation.json').read_text())['success']
assert json.loads((change/'check/fresh-install-validation.json').read_text())['success']
assert json.loads((change/'check/schema-validation.json').read_text())['idempotent']
envfile=base/'.env';values=dict(line.split('=',1) for line in envfile.read_text().splitlines() if '=' in line)
assert values['COMPOSE_PROJECT_NAME']=='zjerp-production'
def run(args,**kwargs):return subprocess.run(args,check=True,**kwargs)
def sql(text):
    return subprocess.check_output(['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],cwd=base,input=text.encode()).decode()
assert sql('SELECT DATABASE();').strip()=='server'
backend=json.loads(subprocess.check_output(['docker','image','inspect','zjerp-backend:role-only-20261028']))[0]
tested=json.loads(subprocess.check_output(['docker','inspect','zjerp-role-check-backend']))[0]
assert backend['Id']==tested['Image'],'Candidate differs from tested image'
frontend=json.loads(subprocess.check_output(['docker','image','inspect','zjerp-frontend:role-only-20261028']))[0]
assert frontend['Id']==json.loads(subprocess.check_output(['docker','inspect','zjerp-role-check-frontend']))[0]['Image']
bt='zjerp-backend:20261028-roles-'+backend['Id'].split(':')[1][:12]
ft='zjerp-frontend:20261028-roles-'+frontend['Id'].split(':')[1][:12]
run(['docker','tag',backend['Id'],bt]);run(['docker','tag',frontend['Id'],ft])
(report/'before.env').write_bytes(envfile.read_bytes());(report/'before.env').chmod(0o600)
(report/'before.override.yaml').write_bytes((base/'compose.override.yaml').read_bytes())

run(['docker','compose','stop','frontend','backend'],cwd=base)
(report/'stopped-at.txt').write_text(time.strftime('%Y-%m-%d %H:%M:%S %z'))
run(['python3',str(root/'migration-post-backup-B.py')])
backup=(root/'final/post-migration-backup-path.txt').read_text().strip()
(report/'backup-path.txt').write_text(backup+'\n')
print('Pre-upgrade matched backup saved:',backup,flush=True)

menus=[json.loads(x) for x in sql('SELECT JSON_OBJECT("id",menu_id,"parent",parent_id,"perms",perms,"component",component) FROM sys_menu;').splitlines()]
retired={x['id'] for x in menus if (x['perms'] or '').startswith(('system:dept:','system:post:')) or x['component'] in ('system/dept/index','system/post/index')}
while True:
    children={x['id'] for x in menus if x['parent'] in retired}
    if children<=retired:break
    retired|=children
retired_sql=','.join(str(x) for x in sorted(retired)) or '-1'
def integrity():
    result={}
    for table in sql('SHOW TABLES;').splitlines():
        if table in {'sys_dept','sys_post','sys_user_post','sys_role_dept'}:continue
        cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;').splitlines()]
        cols=[c for c in cols if (table,c) not in {('sys_user','dept_id'),('sys_role','data_scope'),('sys_role','dept_check_strictly'),('sys_oper_log','dept_name')}]
        where=''
        if table in ('sys_menu','sys_role_menu'):where=' WHERE menu_id NOT IN ('+retired_sql+')'
        if table=='sys_config':where=" WHERE config_key<>'sys.account.captchaEnabled'"
        rows=sql('SELECT JSON_ARRAY('+','.join('`'+c+'`' for c in cols)+') FROM `'+table+'`'+where+';').splitlines()
        result[table]=dict(rows=len(rows),sha256=hashlib.sha256('\n'.join(sorted(rows)).encode()).hexdigest())
    return result
before=integrity()
(report/'before-integrity.json').write_text(json.dumps(before,indent=2))
sql((change/'source/zjerp_backend-master/sql/role_only_upgrade.sql').read_text())
after=integrity()
(report/'after-integrity.json').write_text(json.dumps(after,indent=2))
assert before==after,'Preserved data mismatch; application remains stopped for review'
print('All preserved user, role, business and file data match the pre-upgrade snapshot',flush=True)

values.update(BACKEND_IMAGE=bt,FRONTEND_IMAGE=ft,FRONTEND_CONTEXT=str(change/'source/zjerp_frontend-master'),TOKEN_SECRET=base64.b64encode(secrets.token_bytes(64)).decode())
envfile.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));envfile.chmod(0o600)
override=base/'compose.override.yaml';text=override.read_text();assert text.count('dockerfile: Dockerfile.migration')==2
assert text.count('  mysql:\n')==1
text=text.replace('  mysql:\n','  mysql:\n    volumes:\n      - '+str(change/'source/zjerp_backend-master/sql')+':/opt/zjerp/sql:ro\n      - '+str(change/'source/zjerp_backend-master/deploy/mysql/10-init.sh')+':/docker-entrypoint-initdb.d/10-init.sh:ro\n',1)
text=text.replace('dockerfile: Dockerfile.migration','context: '+str(change/'source/zjerp_backend-master')+'\n      dockerfile: Dockerfile.role-only',1)
text=text.replace('dockerfile: Dockerfile.migration','context: '+str(change/'source/zjerp_frontend-master')+'\n      dockerfile: Dockerfile.role-only',1)
override.write_text(text)
def redis(args):
    return subprocess.check_output(['docker','compose','exec','-T','redis','sh','-c','export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --raw "$@"','sh',*args],cwd=base).decode()
keys=[]
for pattern in ['login_tokens:*','captcha_codes:*']:keys+=redis(['--scan','--pattern',pattern]).splitlines()
keys.append('sys_config:sys.account.captchaEnabled')
redis(['DEL',*keys])
run(['docker','compose','config','--quiet'],cwd=base)
run(['docker','compose','up','-d','--no-build','--wait','--wait-timeout','180','backend','frontend'],cwd=base)
(report/'result.json').write_text(json.dumps(dict(success=True,backend=bt,frontend=ft,backup=backup,removed_menu_ids=sorted(retired),preserved_tables=len(after),opened_at=time.strftime('%Y-%m-%d %H:%M:%S %z')),indent=2))
print('Production role-only update completed; existing sessions require a fresh login',flush=True)
