import hashlib,json,os,subprocess,time
from pathlib import Path
root=Path('/vol2/zjerp-migration')
dest=root/'rehearsal'
source=dest/'upload'
inventory=json.loads((dest/'upload-inventory.json').read_text())
cachefile=dest/'received-file-hashes.json'
cache=json.loads(cachefile.read_text()) if cachefile.exists() else {}
while True:
    ready=0; copied=0
    for item in inventory['files']:
        p=source/item['path']
        if not p.is_file() or p.stat().st_size!=item['size']: continue
        ready+=1;copied+=item['size']
        stat=p.stat(); key=item['path']
        signature=[stat.st_size,stat.st_mtime_ns]
        if key in cache and cache[key]['signature']==signature:continue
        h=hashlib.sha256()
        with p.open('rb') as f:
            for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
        after=p.stat()
        if signature==[after.st_size,after.st_mtime_ns]:cache[key]={'signature':signature,'sha256':h.hexdigest()}
    tmp=cachefile.with_suffix('.tmp');tmp.write_text(json.dumps(cache));tmp.replace(cachefile)
    status=dict(ready_files=ready,total_files=inventory['count'],ready_bytes=copied,total_bytes=inventory['bytes'],hashed_files=len(cache),time=time.time(),phase='copy-and-verify')
    (dest/'worker-status.json').write_text(json.dumps(status))
    print(json.dumps(status),flush=True)
    manifestfile=dest/'upload-manifest.json'
    if ready==inventory['count'] and manifestfile.exists():
        manifest=json.loads(manifestfile.read_text())
        mismatches=[x['path'] for x in manifest['files'] if x['path'] not in cache or cache[x['path']]['sha256']!=x['sha256']]
        if mismatches:
            (dest/'hash-mismatches.json').write_text(json.dumps(mismatches,ensure_ascii=False));raise SystemExit('Hash mismatches; refusing migration')
        running=subprocess.run(['pgrep','-x','rsync'],stdout=subprocess.DEVNULL).returncode==0
        if not running:break
    time.sleep(30)
(dest/'original-files-verified.json').write_text(json.dumps(dict(count=inventory['count'],bytes=inventory['bytes'],verified=time.time())))
base=root/'source/zjerp_backend-master'
with (dest/'before-file-migration.sql').open('wb') as f:
    subprocess.run(['docker','compose','exec','-T','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --triggers --events "$MYSQL_DATABASE"'],cwd=base,stdout=f,check=True)
subprocess.run(['docker','run','--rm','--user','root','--entrypoint','sh','-v',str(source)+':/data','zjerp-backend:local','-c','chown -R 10001:10001 /data'],check=True)
(dest/'worker-status.json').write_text(json.dumps(dict(phase='file-migration',time=time.time())))
subprocess.run(['python3',str(root/'migration-api-B.py'),'migrate'],check=True)
result=json.loads((dest/'file-migration-result.json').read_text())
response=result['response']
if response.get('code')!=200 or response.get('data',{}).get('failed')!=0:raise SystemExit('Migration requires error review')
(dest/'worker-status.json').write_text(json.dumps(dict(phase='migration-complete',time=time.time(),result=response['data'])))
print('Verified original files and completed rehearsal file migration',flush=True)
