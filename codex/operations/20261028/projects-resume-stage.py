import json,re,subprocess,secrets,time,hashlib
from pathlib import Path
from decimal import Decimal
root=Path('/vol2/zjerp-migration/projects');work=root/'check';state=json.loads((work/'state.json').read_text());name=state['database'];pw=state['password'];assert re.fullmatch(r'work_project_check_\d+',name) and name==state['username']
def sql(s,db=name):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh',db],input=s.encode()).decode()
def rows(table,db):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+table+'`;',db).splitlines()]
 return sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in cols)+') FROM `'+table+'`;',db).splitlines()
assert sql('SELECT COUNT(*) FROM zj_project_code;').strip()=='198'
tables=sql("SHOW TABLES LIKE 'zj_work_project%';").splitlines()
assert tables and all(re.fullmatch(r'zj_work_project(?:_[a-z]+)?',x) for x in tables)
for table in tables:assert sql('SELECT COUNT(*) FROM `'+table+'`;').strip()=='0','Stage contains data, refusing reset'
sql('SET FOREIGN_KEY_CHECKS=0;\n'+'\n'.join('DROP TABLE `'+x+'`;' for x in tables)+'\nSET FOREIGN_KEY_CHECKS=1;')
source=(root/'stage.py').read_text();exec(source[source.index('before={'):])
