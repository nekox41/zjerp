import subprocess,time,json,re
from pathlib import Path
root=Path('/vol2/zjerp-migration/contracts');db='contract_seed_'+str(int(time.time()));assert re.fullmatch(r'contract_seed_\d+',db)
def sql(s,name=None):
 cmd=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh']
 if name:cmd.append(name)
 return subprocess.check_output(cmd,input=s.encode()).decode()
sql('CREATE DATABASE `'+db+'` CHARACTER SET utf8mb4;')
try:
 for f in ['ry_20250417.sql','quartz.sql','business_schema.sql','sys_file.sql','zhongji_contract_upgrade.sql','zhongji_contract_menus.sql']:
  sql((root/'source/zjerp_backend-master/sql'/f).read_text(),db)
 assert sql('SELECT COUNT(*) FROM zj_contract;',db).strip()=='0'
 assert sql("SELECT COUNT(*) FROM sys_menu WHERE component LIKE 'zhongji/contracts/%';",db).strip()=='5'
 assert sql("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=1 AND r.role_key='admin';",db).strip()=='1'
 (root/'check/fresh-install-validation.json').write_text(json.dumps(dict(success=True,five_modules=True,admin_preserved=True)))
 print('Fresh install schema and five contract menus verified')
finally:sql('DROP DATABASE `'+db+'`;')
