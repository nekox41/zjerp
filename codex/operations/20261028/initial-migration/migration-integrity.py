import os,json,subprocess,hashlib,sys
from pathlib import Path
host=sys.argv[1]
stage=os.environ.get('MIGRATION_STAGE','rehearsal')
if host=='A':
    dest=Path.home()/'zjerp-migration'/stage
    command=['docker','exec','-i','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names server']
    cwd=None
else:
    dest=Path('/vol2/zjerp-migration')/stage
    cwd=Path('/vol2/zjerp-migration/source/zjerp_backend-master')
    command=['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"']
dest.mkdir(mode=0o700,exist_ok=True)
def query(sql):return subprocess.check_output(command,cwd=cwd,input=sql.encode()).decode().splitlines()
def ident(value):return '`'+value.replace('`','``')+'`'
def digest(rows):return hashlib.sha256(('\n'.join(sorted(rows))+'\n').encode()).hexdigest()
tables=[x.split('\t')[0] for x in query('SHOW FULL TABLES WHERE Table_type="BASE TABLE";')]
report={}
for table in tables:
    columns=[x.split('\t')[0] for x in query('SHOW COLUMNS FROM '+ident(table)+';')]
    rows=query('SELECT JSON_ARRAY('+','.join(map(ident,columns))+') FROM '+ident(table)+';')
    excluded={'zj_contract':{'contract_file'},'zj_report':{'report_file','report_attach'},'zj_wbcard':{'file'},'sys_user':{'login_ip','login_date','update_time'}}.get(table,set())
    stablecols=[x for x in columns if x not in excluded]
    stable=rows if not excluded else query('SELECT JSON_ARRAY('+','.join(map(ident,stablecols))+') FROM '+ident(table)+';')
    report[table]=dict(count=len(rows),sha256=digest(rows),stable_sha256=digest(stable),excluded_columns=sorted(excluded))
filename=dest/('table-integrity-'+host+'-'+(sys.argv[2] if len(sys.argv)>2 else 'baseline')+'.json')
filename.write_text(json.dumps(report,indent=2));filename.chmod(0o600)
print('Saved table integrity:',filename,'tables:',len(report))
