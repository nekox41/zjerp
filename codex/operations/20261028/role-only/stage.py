import os,json,secrets,time,re,hashlib,subprocess
from pathlib import Path

root=Path('/vol2/zjerp-migration/role-only')
work=root/'check'; work.mkdir(mode=0o700,exist_ok=True)
statefile=work/'state.json'
if statefile.exists():raise SystemExit('Validation state already exists; refusing to overwrite')
name='role_check_'+str(int(time.time()))
assert re.fullmatch(r'role_check_\d+',name)
password=secrets.token_hex(24)
state=dict(database=name,username=name,password=password)
statefile.write_text(json.dumps(state));statefile.chmod(0o600)
def sql(statement,database=None):
    command=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh']
    if database:command.append(database)
    return subprocess.check_output(command,input=statement.encode()).decode()
sql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4; CREATE USER \'{name}\'@\'%\' IDENTIFIED BY \'{password}\'; GRANT ALL ON `{name}`.* TO \'{name}\'@\'%\';')
snapshot=Path('/vol2/zjerp-migration/backups/post-migration-20261028-151434')
for file in ['database.sql','exact-floating-values.sql']:
    text=(snapshot/file).read_text()
    assert not re.search(r'^\s*(?:USE\s|CREATE\s+DATABASE|DROP\s+DATABASE|GRANT\s|CREATE\s+USER)',text,re.I|re.M)
    sql(text,name)
def integrity():
    report={}
    retired={'sys_dept','sys_post','sys_user_post','sys_role_dept','sys_menu','sys_role_menu','sys_config'}
    for table in sql('SHOW TABLES;',name).splitlines():
        if table in retired:continue
        columns=[r.split('\t')[0] for r in sql('SHOW COLUMNS FROM `'+table+'`;',name).splitlines()]
        columns=[c for c in columns if (table,c) not in {('sys_user','dept_id'),('sys_role','data_scope'),('sys_role','dept_check_strictly'),('sys_oper_log','dept_name')}]
        rows=sql('SELECT JSON_ARRAY('+','.join('`'+c+'`' for c in columns)+') FROM `'+table+'`;',name).splitlines()
        report[table]=dict(count=len(rows),sha256=hashlib.sha256('\n'.join(sorted(rows)).encode()).hexdigest())
    return report
before=integrity()
upgrade=(root/'source/zjerp_backend-master/sql/role_only_upgrade.sql').read_text()
sql(upgrade,name)
after=integrity()
assert before==after,'Migration changed preserved business/user/role/file data'
sql(upgrade,name) # Reapplication must be safe too.
assert after==integrity()
(work/'schema-validation.json').write_text(json.dumps(dict(unchanged_tables=before,idempotent=True),indent=2))
sql("INSERT INTO sys_role(role_name,role_key,role_sort,status,del_flag) VALUES ('Stage probe','stage_probe_20261028',999,'0','0'); INSERT INTO sys_user_role(user_id,role_id) VALUES (1,LAST_INSERT_ID());",name)
redis_password=secrets.token_hex(24)
redis_env=work/'redis.env';redis_env.write_text('REDIS_PASSWORD='+redis_password+'\n');redis_env.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-role-check-redis','--network','zjerp-production_default','--env-file',str(redis_env),'public.ecr.aws/docker/library/redis:7-alpine','sh','-c','exec redis-server --requirepass "$REDIS_PASSWORD"'],check=True,stdout=subprocess.DEVNULL)
env=dict(SPRING_PROFILES_ACTIVE='prod',SPRING_DATASOURCE_DRUID_MASTER_URL='jdbc:mysql://mysql:3306/'+name+'?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia%2FShanghai',SPRING_DATASOURCE_DRUID_MASTER_USERNAME=name,SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=password,SPRING_DATA_REDIS_HOST='zjerp-role-check-redis',SPRING_DATA_REDIS_PASSWORD=redis_password,RUOYI_PROFILE='/app/upload',TOKEN_SECRET=secrets.token_hex(64),TZ='Asia/Shanghai')
envfile=work/'backend.env';envfile.write_text(''.join(k+'='+v+'\n' for k,v in env.items()));envfile.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-role-check-backend','--network','zjerp-production_default','--env-file',str(envfile),'zjerp-backend:role-only-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
    value=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-role-check-backend']).decode().strip()
    if value=='healthy':break
    if value=='unhealthy':raise SystemExit('Stage backend unhealthy')
    time.sleep(2)
else:raise SystemExit('Stage backend readiness timeout')
config=(root/'source/zjerp_frontend-master/zjerp.conf').read_text().replace('http://backend:8080','http://zjerp-role-check-backend:8080')
nginx=work/'nginx.conf';nginx.write_text(config);nginx.chmod(0o644)
subprocess.run(['docker','run','-d','--name','zjerp-role-check-frontend','--network','zjerp-production_default','-p','127.0.0.1:18089:80','-v',str(nginx)+':/etc/nginx/conf.d/default.conf:ro','zjerp-frontend:role-only-20261028'],check=True,stdout=subprocess.DEVNULL)
print('Isolated validation ready at 127.0.0.1:18089; schema invariants and repeated upgrade passed')
