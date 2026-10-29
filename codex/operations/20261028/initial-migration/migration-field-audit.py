import os,json,subprocess,hashlib,sys
from pathlib import Path
host=sys.argv[1]
if host=='A':
    dest=Path.home()/'zjerp-migration/rehearsal'
    command=['docker','exec','-i','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names server'];cwd=None
else:
    dest=Path('/vol2/zjerp-migration/rehearsal');cwd=Path('/vol2/zjerp-migration/source/zjerp_backend-master')
    command=['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"']
def query(sql):return subprocess.check_output(command,cwd=cwd,input=sql.encode()).decode().splitlines()
result={}
for table,pk in [('sys_user','user_id'),('zj_contract','id')]:
    columns=[x.split('\t')[0] for x in query(f'SHOW COLUMNS FROM `{table}`;')]
    rows=[json.loads(x) for x in query('SELECT JSON_ARRAY('+','.join('`'+x+'`' for x in columns)+') FROM `'+table+'`;')]
    result[table]={str(row[columns.index(pk)]):{col:hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True).encode()).hexdigest() for col,value in zip(columns,row)} for row in rows}
target=dest/('field-audit-'+host+'.json');target.write_text(json.dumps(result));target.chmod(0o600)
print('Saved field hashes without plaintext row values')
