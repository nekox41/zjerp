import json,os,subprocess
from pathlib import Path
sql='SELECT JSON_OBJECT("user",USER,"host",HOST,"database",DB,"command",COMMAND) FROM information_schema.PROCESSLIST WHERE USER<>"event_scheduler";'
result=subprocess.check_output(['docker','exec','-i','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names server'],input=sql.encode()).decode()
rows=[json.loads(x) for x in result.splitlines()]
dest=Path.home()/'zjerp-migration'/os.environ.get('MIGRATION_STAGE','rehearsal');dest.mkdir(mode=0o700,exist_ok=True)
(dest/'database-connections.json').write_text(json.dumps(rows,indent=2))
external=[x for x in rows if x['command']!='Daemon' and not x['host'].startswith(('localhost','127.0.0.1','::1'))]
print('Non-local database connections:',len(external))
if os.environ.get('REQUIRE_NO_EXTERNAL_WRITERS')=='true' and external:raise SystemExit('External database sessions remain; refusing final backup')
