import subprocess,json,re,time
from pathlib import Path
root=Path('/vol2/zjerp-migration/projects');db='work_project_seed_'+str(int(time.time()));assert re.fullmatch(r'work_project_seed_\d+',db)
def sql(s,d=None):
 args=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh']
 if d:args.append(d)
 return subprocess.check_output(args,input=s.encode()).decode()
sql('CREATE DATABASE `'+db+'` CHARACTER SET utf8mb4;')
try:
 for f in ['ry_20250417.sql','quartz.sql','business_schema.sql','sys_file.sql','zhongji_contract_upgrade.sql','zhongji_contract_menus.sql','work_projects_schema.sql','work_projects_menus.sql']:sql((root/'source/zjerp_backend-master/sql'/f).read_text(),db)
 assert sql('SELECT COUNT(*) FROM zj_work_project;',db).strip()=='0'
 assert sql("SELECT COUNT(*) FROM sys_menu WHERE component LIKE 'zhongji/projects/%';",db).strip()=='5'
 assert sql("SELECT COUNT(*) FROM sys_menu WHERE perms='management:project:expenses';",db).strip()=='1'
 assert sql("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=1 AND r.role_key='admin';",db).strip()=='1'
 (root/'check/fresh-install-validation.json').write_text(json.dumps(dict(success=True,five_project_modules=True,expenses_permission=True)))
 print('Fresh install project schema and role menus verified')
finally:sql('DROP DATABASE `'+db+'`;')
