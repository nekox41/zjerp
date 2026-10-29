import subprocess,json,re,secrets,time,hashlib
from decimal import Decimal
from pathlib import Path
root=Path('/vol2/zjerp-migration/projects');work=root/'check';work.mkdir(mode=0o700,exist_ok=True);statefile=work/'state.json';assert not statefile.exists()
name='work_project_check_'+str(int(time.time()));pw=secrets.token_hex(24)
state=dict(database=name,username=name,password=pw);statefile.write_text(json.dumps(state));statefile.chmod(0o600)
def command(exe='mysql',db=None):
 args=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec '+exe+' --default-character-set=utf8mb4 -uroot "$@"','sh']
 if exe=='mysql':args+=['--batch','--raw','--skip-column-names']
 if db:args.append(db)
 return args
def sql(s,db='server'):return subprocess.check_output(command(db=db),input=s.encode()).decode()
def rows(table,db):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;',db).splitlines()]
 return sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in cols)+') FROM `'+table+'`;',db).splitlines()
sql('CREATE DATABASE `'+name+'` CHARACTER SET utf8mb4; CREATE USER \''+name+'\'@\'%\' IDENTIFIED BY \''+pw+'\'; GRANT ALL ON `'+name+'`.* TO \''+name+'\'@\'%\';',db=None)
dump=subprocess.check_output(command('mysqldump')+['--single-transaction','--quick','--no-tablespaces','--set-gtid-purged=OFF','--hex-blob','server']).decode()
assert not re.search(r'^\s*(USE |CREATE DATABASE|DROP DATABASE|GRANT |CREATE USER)',dump,re.I|re.M)
lines=[]
for line in dump.splitlines():
 if re.match(r'^/\*!\d+ (?:VIEW |CREATE )',line):line=line.replace('`server`.','`'+name+'`.')
 lines.append(line)
snapshot=work/'source.sql';snapshot.write_text('\n'.join(lines));snapshot.chmod(0o600);sql(snapshot.read_text(),name)
# mysqldump rounds FLOAT values. Restore the full JSON numeric representation from the live source.
floating=sql("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='server' AND data_type IN ('float','double') ORDER BY table_name,ordinal_position;").splitlines()
groups={}
for line in floating:
 table,col=line.split('\t');groups.setdefault(table,[]).append(col)
for table,cols in groups.items():
 if sql("SELECT table_type FROM information_schema.tables WHERE table_schema='server' AND table_name='"+table+"';").strip()!='BASE TABLE':continue
 pk=sql("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema='server' AND table_name='"+table+"' AND constraint_name='PRIMARY' ORDER BY ordinal_position;").splitlines()
 assert pk
 fields=pk+cols;values=sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in fields)+') FROM `'+table+'`;').splitlines();updates=[]
 for value in values:
  item=json.loads(value,parse_float=Decimal)
  literal=lambda x:'NULL' if x is None else str(x) if isinstance(x,(int,Decimal)) else "CONVERT(X'"+str(x).encode().hex()+"' USING utf8mb4)"
  updates.append('UPDATE `'+table+'` SET '+','.join('`'+c+'`='+literal(item[c]) for c in cols)+' WHERE '+' AND '.join('`'+c+'`='+literal(item[c]) for c in pk)+';')
 if updates:sql('START TRANSACTION;\n'+'\n'.join(updates)+'\nCOMMIT;',name)
views=sql("SELECT view_definition FROM information_schema.views WHERE table_schema=DATABASE();",name);assert '`server`.' not in views,'Stage view points at production'
before={t:sorted(rows(t,name)) for t in sql('SHOW TABLES;',name).splitlines()}
report=work/'migration-report.json'
subprocess.run(['python3',str(root/'source/zjerp_backend-master/scripts/migrate-work-projects.py'),'--database',name,'--mysql-container','zjerp-production-mysql-1','--apply','--report',str(report)],check=True)
sql((root/'source/zjerp_backend-master/sql/work_projects_menus.sql').read_text(),name)
owner_tables={'zj_contract','zj_contract_maintenance','zj_contract_inspection','zj_contract_rating','zj_contract_assessment','zj_contract_other'}
preserved=[]
for t,old in before.items():
 if t in ['sys_menu','sys_role_menu','zj_intermediary']:continue
 new=sorted(rows(t,name))
 if t in owner_tables:
  def cleaned(values):
   out=[]
   for v in values:
    d=json.loads(v,parse_float=Decimal);d.pop('ownership',None);d.pop('intermediary_id',None);out.append(json.dumps(d,sort_keys=True,default=str))
   return sorted(out)
  assert cleaned(old)==cleaned(new),t
 else:assert old==new,t
 preserved.append(t)
stats=json.loads(report.read_text());assert stats['source_codes']==198 and stats['projects']==200,stats
assert stats['linked_expenses']==1
assert int(sql("SELECT COUNT(*) FROM zj_work_project_legacy WHERE source_table='zj_project_code';",name))==198
assert int(sql('SELECT COUNT(*) FROM zj_work_project_expense_link;',name))==1
(work/'schema-validation.json').write_text(json.dumps(dict(success=True,preserved_tables=preserved,stats=stats),indent=2))
sql("INSERT INTO sys_role(role_name,role_key,role_sort,status,del_flag) VALUES ('Stage probe','work_project_stage_20261028',999,'0','0'); INSERT INTO sys_user_role(user_id,role_id) VALUES (1,LAST_INSERT_ID());",name)
rp=secrets.token_hex(24);env=work/'redis.env';env.write_text('REDIS_PASSWORD='+rp+'\n');env.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-work-project-check-redis','--network','zjerp-production_default','--env-file',str(env),'public.ecr.aws/docker/library/redis:7-alpine','sh','-c','exec redis-server --requirepass "$REDIS_PASSWORD"'],check=True,stdout=subprocess.DEVNULL)
values=dict(SPRING_PROFILES_ACTIVE='prod',SPRING_DATASOURCE_DRUID_MASTER_URL='jdbc:mysql://mysql:3306/'+name+'?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia%2FShanghai',SPRING_DATASOURCE_DRUID_MASTER_USERNAME=name,SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=pw,SPRING_DATA_REDIS_HOST='zjerp-work-project-check-redis',SPRING_DATA_REDIS_PASSWORD=rp,RUOYI_PROFILE='/app/upload',TOKEN_SECRET=secrets.token_hex(64),UPLOAD_CRON='-',TZ='Asia/Shanghai')
env=work/'backend.env';env.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));env.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-work-project-check-backend','--network','zjerp-production_default','--env-file',str(env),'zjerp-backend:projects-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
 status=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-work-project-check-backend']).decode().strip()
 if status=='healthy':break
 if status=='unhealthy':raise RuntimeError('Stage backend unhealthy')
 time.sleep(2)
else:raise RuntimeError('Stage backend readiness timeout')
conf=work/'nginx.conf';conf.write_text((root/'source/zjerp_frontend-master/zjerp.conf').read_text().replace('http://backend:8080','http://zjerp-work-project-check-backend:8080'));conf.chmod(0o644)
subprocess.run(['docker','run','-d','--name','zjerp-work-project-check-frontend','--network','zjerp-production_default','-p','127.0.0.1:18089:80','-v',str(conf)+':/etc/nginx/conf.d/default.conf:ro','zjerp-frontend:projects-20261028'],check=True,stdout=subprocess.DEVNULL)
print('Isolated project stage ready; legacy records, ownership migration and expense links verified',flush=True)
