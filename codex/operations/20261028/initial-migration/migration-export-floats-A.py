import json,os,subprocess,math,sys
from pathlib import Path
dest=Path.home()/'zjerp-migration'/os.environ.get('MIGRATION_STAGE','rehearsal')
if len(sys.argv)>1 and sys.argv[1]=='B':
    root=Path('/vol2/zjerp-migration')
    dest=Path(os.environ.get('MIGRATION_BACKUP_DIR',str(root/os.environ.get('MIGRATION_STAGE','rehearsal'))))
    assert dest.resolve().is_relative_to(root.resolve())
    command=['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"']
    cwd=root/'source/zjerp_backend-master'
else:
    command=['docker','exec','-i','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names server'];cwd=None
dest.mkdir(mode=0o700,exist_ok=True)
def query(sql):return subprocess.check_output(command,cwd=cwd,input=sql.encode()).decode().splitlines()
def ident(name):return '`'+name.replace('`','``')+'`'
def number(value):
    if value is None:return 'NULL'
    if isinstance(value,int):return str(value)
    assert isinstance(value,float) and math.isfinite(value)
    return format(value,'.17g')
columns=query('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type IN ("float","double");')
tables={}
for row in columns:
    table,col=row.split('\t');tables.setdefault(table,[]).append(col)
statements=['-- Restore exact floating point values serialized through MySQL JSON (SQL dump text rounds FLOAT).','START TRANSACTION;']
summary={}
for table,cols in tables.items():
    pk=[x.split('\t')[0] for x in query('SHOW KEYS FROM '+ident(table)+' WHERE Key_name="PRIMARY";')]
    keyrows=query('SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=CONVERT(0x'+table.encode().hex()+' USING utf8mb4) AND constraint_name="PRIMARY" ORDER BY ordinal_position;')
    assert keyrows, 'No primary key for floating table '+table
    assert query('SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table=CONVERT(0x'+table.encode().hex()+' USING utf8mb4);')==['0'], 'Review triggers before precision restore'
    keys=keyrows
    rows=[json.loads(x) for x in query('SELECT JSON_ARRAY(JSON_ARRAY('+','.join(map(ident,keys))+'),JSON_ARRAY('+','.join(map(ident,cols))+')) FROM '+ident(table)+';')]
    for ids,values in rows:
        sets=','.join(ident(col)+'='+number(value) for col,value in zip(cols,values))
        predicates=[]
        for col,value in zip(keys,ids):
            if isinstance(value,str):literal='CONVERT(0x'+value.encode().hex()+' USING utf8mb4)'
            else:literal=number(value)
            predicates.append(ident(col)+'='+literal)
        statements.append('UPDATE '+ident(table)+' SET '+sets+' WHERE '+' AND '.join(predicates)+';')
    summary[table]=dict(columns=cols,rows=len(rows))
statements.append('COMMIT;')
file=dest/'exact-floating-values.sql';file.write_text('\n'.join(statements)+'\n');file.chmod(0o600)
(dest/'exact-floating-values-summary.json').write_text(json.dumps(summary,indent=2))
print('Exported exact floating values:',json.dumps(summary))
