import hashlib,json,os,time
from pathlib import Path
root=Path('/vol2/zjerp-migration');dest=root/os.environ.get('MIGRATION_STAGE','rehearsal')
manifest=json.loads((dest/'upload-manifest.json').read_text())
cachefile=root/'rehearsal/received-file-hashes.json'
cache=json.loads(cachefile.read_text()) if cachefile.exists() else {}
errors=[];rehashed=0;verified=[]
for item in manifest['files']:
    p=dest/'upload'/item['path']
    if not p.is_file() or p.stat().st_size!=item['size']:
        errors.append(dict(path=item['path'],error='missing or wrong size'));continue
    stat=p.stat();signature=[stat.st_size,stat.st_mtime_ns];previous=cache.get(item['path'])
    if previous and previous['signature']==signature and previous['sha256']==item['sha256']:
        digest=previous['sha256']
    else:
        h=hashlib.sha256()
        with p.open('rb') as f:
            for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
        digest=h.hexdigest();rehashed+=1
    if digest!=item['sha256']:errors.append(dict(path=item['path'],error='hash mismatch'))
    verified.append(dict(path=item['path'],sha256=digest))
report=dict(count=len(verified),expected_count=manifest['count'],bytes=manifest['bytes'],rehashed_files=rehashed,errors=errors,time=time.time(),verified=not errors)
(dest/'original-file-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='errors'}))
if errors:raise SystemExit('Original file verification failed; see private report')
