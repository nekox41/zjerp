import json,subprocess
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],input=s.encode()).decode()
tables=sql('SHOW TABLES;').splitlines();print('tables',','.join(tables))
for t in tables:
 if any(w in t for w in ['project','maintain','cost','expense','agent']):
  print('SCHEMA',t,sql('SHOW CREATE TABLE `'+t+'`;'));print('COUNT',sql('SELECT COUNT(*) FROM `'+t+'`;'))
print('managers',sql("SELECT COALESCE(manager,'<NULL>'),COUNT(*) FROM zj_contract GROUP BY manager;"))
print('menu',sql("SELECT menu_id,parent_id,menu_name,component,perms FROM sys_menu WHERE component LIKE '%project%' OR component LIKE '%cost%' OR perms LIKE '%project:%';"))
print('code formats',sql("SELECT type,COUNT(*),MIN(code),MAX(code),MIN(`index`),MAX(`index`) FROM zj_project_code GROUP BY type;"))
print('project name overlaps',sql("SELECT COUNT(*) FROM zj_project_code c JOIN zjerp_project_system p ON TRIM(c.name)=TRIM(p.project_name);"))
