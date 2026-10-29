import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('db','/vol2/zjerp-migration/migration-db-B.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
result=module.db('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type IN ("varchar","char","text","mediumtext","longtext");')
hits=[]
for row in result.splitlines():
    table,col=row.split('\t')
    if table in ('sys_oper_log','sys_logininfor','sys_job_log'): continue
    count=int(module.db(f'SELECT COUNT(*) FROM `{table}` WHERE `{col}` LIKE "%10.5.250.92%" OR `{col}` LIKE "%192.168.0.115%" OR `{col}` LIKE "%/home/aa/%";').strip())
    if count: hits.append(dict(table=table,column=col,count=count))
Path('/vol2/zjerp-migration/rehearsal/address-audit.json').write_text(json.dumps(hits,indent=2))
print('Non-log old-host references:',json.dumps(hits))
templates=[json.loads(row) for row in module.db('SELECT JSON_OBJECT("id",id,"file",file) FROM templates;').splitlines()]
root=Path('/vol2/zjerp-migration/rehearsal/wbcard')
for row in templates:
    rel=row['file'].removeprefix('./wbcard/').removeprefix('/app/wbcard/')
    print('template',row['id'],'file_exists', (root/rel).is_file())
