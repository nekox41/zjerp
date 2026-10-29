import json,re,secrets,subprocess,time,hashlib
from pathlib import Path
from decimal import Decimal
root=Path('/vol2/zjerp-migration/contracts');work=root/'check';work.mkdir(mode=0o700,exist_ok=True)
assert not (work/'state.json').exists()
name='contract_check_'+str(int(time.time()));password=secrets.token_hex(24)
state=dict(database=name,username=name,password=password);(work/'state.json').write_text(json.dumps(state));(work/'state.json').chmod(0o600)
def sql(s,db=None):
 cmd=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh']
 if db:cmd.append(db)
 return subprocess.check_output(cmd,input=s.encode()).decode()
sql(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4; CREATE USER '{name}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON `{name}`.* TO '{name}'@'%';")
snapshot=Path('/vol2/zjerp-migration/backups/post-migration-20261028-192618')
for f in ['database.sql','exact-floating-values.sql']:
 text=(snapshot/f).read_text();assert not re.search(r'^\s*(USE |CREATE DATABASE|DROP DATABASE|GRANT |CREATE USER)',text,re.I|re.M);sql(text,name)
sql((root/'source/zjerp_backend-master/sql/user_material_upgrade.sql').read_text(),name)
def allrows(table):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;',name).splitlines()]
 return [json.loads(x,parse_float=Decimal) for x in sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in cols)+') FROM `'+table+'`;',name).splitlines()]
original=allrows('zj_contract');preserve={}
for table in sql('SHOW TABLES;',name).splitlines():
 if table not in ['zj_contract','sys_menu','sys_role_menu']:preserve[table]=allrows(table)
for f in ['zhongji_contract_upgrade.sql','zhongji_contract_menus.sql']:sql((root/'source/zjerp_backend-master/sql'/f).read_text(),name)
after={x['id']:x for x in allrows('zj_contract')};assert len(after)==len(original)
for old in original:
 new=after[old['id']]
 for k,v in old.items():
  if k=='contract_type':assert new[k]==(v if v in ['WB','JC','PD','PG'] else 'QT')
  elif k=='amount':assert v is None and new[k] is None or v is not None and abs(Decimal(str(v))-Decimal(str(new[k])))<=Decimal('0.000000000001')
  else:assert new[k]==v,(old['id'],k)
 assert new['legacy_type']==old['contract_type'] and new['ownership']=='pending' and new['intermediary_id'] is None
assert allrows('zj_contract_legacy_20261028')==original
for table,rows in preserve.items():assert allrows(table)==rows,table
counts={t:int(sql("SELECT COUNT(*) FROM zj_contract WHERE contract_type='"+t+"';",name)) for t in ['WB','JC','PD','PG','QT']}
assert counts=={'WB':301,'JC':47,'PD':25,'PG':2,'QT':5}
assert sql('SELECT COUNT(*) FROM zj_contract WHERE classification_pending=1;',name).strip()=='3'
(work/'schema-validation.json').write_text(json.dumps(dict(success=True,counts=counts,contracts=len(after),preserved_tables=len(preserve),legacy_archive_unchanged=True),indent=2))
sql("INSERT INTO sys_role(role_name,role_key,role_sort,status,del_flag) VALUES ('Stage probe','stage_probe_20261028',999,'0','0'); INSERT INTO sys_user_role(user_id,role_id) VALUES (1,LAST_INSERT_ID());",name)
rp=secrets.token_hex(24);env=work/'redis.env';env.write_text('REDIS_PASSWORD='+rp+'\n');env.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-contract-check-redis','--network','zjerp-production_default','--env-file',str(env),'public.ecr.aws/docker/library/redis:7-alpine','sh','-c','exec redis-server --requirepass "$REDIS_PASSWORD"'],check=True,stdout=subprocess.DEVNULL)
values=dict(SPRING_PROFILES_ACTIVE='prod',SPRING_DATASOURCE_DRUID_MASTER_URL='jdbc:mysql://mysql:3306/'+name+'?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia%2FShanghai',SPRING_DATASOURCE_DRUID_MASTER_USERNAME=name,SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=password,SPRING_DATA_REDIS_HOST='zjerp-contract-check-redis',SPRING_DATA_REDIS_PASSWORD=rp,RUOYI_PROFILE='/app/upload',TOKEN_SECRET=secrets.token_hex(64),TZ='Asia/Shanghai')
env=work/'backend.env';env.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));env.chmod(0o600)
subprocess.run(['docker','run','-d','--name','zjerp-contract-check-backend','--network','zjerp-production_default','--env-file',str(env),'zjerp-backend:contracts-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
 status=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-contract-check-backend']).decode().strip()
 if status=='healthy':break
 if status=='unhealthy':raise RuntimeError('Unhealthy stage backend')
 time.sleep(2)
else:raise RuntimeError('Stage backend timeout')
conf=work/'nginx.conf';conf.write_text((root/'source/zjerp_frontend-master/zjerp.conf').read_text().replace('http://backend:8080','http://zjerp-contract-check-backend:8080'));conf.chmod(0o644)
subprocess.run(['docker','run','-d','--name','zjerp-contract-check-frontend','--network','zjerp-production_default','-p','127.0.0.1:18089:80','-v',str(conf)+':/etc/nginx/conf.d/default.conf:ro','zjerp-frontend:contracts-20261028'],check=True,stdout=subprocess.DEVNULL)
print('Isolated contracts stage ready; migration and preserved data verified',flush=True)
