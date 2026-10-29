import hashlib,json,subprocess,time,os
from pathlib import Path
root=Path('/vol2/zjerp-migration');base=root/'source/zjerp_backend-master'
values=dict(line.split('=',1) for line in (base/'.env').read_text().splitlines() if '=' in line)
if values.get('COMPOSE_PROJECT_NAME')!='zjerp-production':raise SystemExit('Expected production deployment')
dest=root/'backups'/time.strftime('post-migration-%Y%m%d-%H%M%S')
dest.mkdir(mode=0o700,parents=True)
with (dest/'database.sql').open('wb') as f:
    subprocess.run(['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --triggers --events "$MYSQL_DATABASE"'],cwd=base,stdout=f,check=True)
(dest/'database.sql').chmod(0o600)
subprocess.run(['python3',str(root/'migration-export-floats-A.py'),'B'],env={**os.environ,'MIGRATION_BACKUP_DIR':str(dest)},check=True)
target='/migration/'+str(dest.relative_to(root))
subprocess.run(['docker','run','--rm','--user','root','--entrypoint','sh','-v',str(root)+':/migration','zjerp-backend:local','-c','cp -a --reflink=always /migration/final/upload "$1/upload" && cp -a --reflink=always /migration/final/wbcard "$1/wbcard"','sh',target],check=True)
for filename in ['.env','compose.yml','compose.override.yaml']:
    (dest/filename).write_bytes((base/filename).read_bytes());(dest/filename).chmod(0o600)
images=subprocess.check_output(['docker','compose','images','--format','json'],cwd=base)
(dest/'images.json').write_bytes(images)
checksum=hashlib.sha256((dest/'database.sql').read_bytes()).hexdigest()
(dest/'database.sha256').write_text(checksum+'  database.sql\n'+hashlib.sha256((dest/'exact-floating-values.sql').read_bytes()).hexdigest()+'  exact-floating-values.sql\n')
print('Saved matched database, upload/template reflink snapshots and configuration:',dest)
(root/'final/post-migration-backup-path.txt').write_text(str(dest)+'\n')
