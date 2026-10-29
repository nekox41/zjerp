import subprocess, json, sys, os
from pathlib import Path
base=Path('/vol2/zjerp-migration/source/zjerp_backend-master')
dest=Path('/vol2/zjerp-migration')/os.environ.get('MIGRATION_STAGE','rehearsal')
def db(sql):
    return subprocess.check_output(['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],cwd=base,input=sql.encode()).decode()
def restore():
    tables=db('SHOW TABLES;').splitlines()
    if tables: raise SystemExit('Refusing restore: target is not empty')
    with (dest/'database.sql').open('rb') as f:
        subprocess.run(['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"'],cwd=base,stdin=f,check=True)
    print('Restored database successfully')
def baseline():
    tables=db('SHOW FULL TABLES WHERE Table_type="BASE TABLE";').splitlines()
    counts={}
    for row in tables:
        table=row.split('\t')[0]
        counts[table]=int(db('SELECT COUNT(*) FROM `'+table.replace('`','``')+'`;').strip())
    (dest/'table-counts-restored.json').write_text(json.dumps(counts,indent=2))
    print(json.dumps(counts))
    print('menu route_name columns:',db('SHOW COLUMNS FROM sys_menu LIKE "route_name";'))
    print('legacy attachment groups:',db('SELECT "contract", COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file),"")<>"" UNION ALL SELECT "report", COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file),"")<>"" UNION ALL SELECT "report_attach", COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach),"")<>"" UNION ALL SELECT "wbcard", COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file),"")<>"";'))
    print('template schema:',db('SHOW COLUMNS FROM templates;'))
    print('scheduler:',db('SELECT @@event_scheduler; SELECT job_id,status FROM sys_job;'))
if __name__=='__main__':
    if sys.argv[1]=='restore': restore(); baseline()
    elif sys.argv[1]=='query': print(db(sys.stdin.read()))
