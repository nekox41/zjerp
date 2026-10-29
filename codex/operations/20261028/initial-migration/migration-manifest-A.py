import hashlib, json, os, time
from pathlib import Path
root=Path('/home/aa/app/zjerp/upload')
dest=Path.home()/'zjerp-migration'/os.environ.get('MIGRATION_STAGE','rehearsal')
dest.mkdir(mode=0o700,exist_ok=True)
cachefile=Path.home()/'zjerp-migration/rehearsal/upload-manifest.json'
cache={x['path']:x for x in json.loads(cachefile.read_text())['files']} if cachefile.exists() else {}
entries=[]; total=0
for path in sorted(root.rglob('*')):
    if not path.is_file(): continue
    stat=path.stat()
    key=str(path.relative_to(root))
    previous=cache.get(key)
    if previous and (previous['size'],previous['mtime_ns'])==(stat.st_size,stat.st_mtime_ns):
        entries.append(previous);total+=stat.st_size;continue
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(4*1024*1024),b''): h.update(block)
    after=path.stat()
    if (stat.st_size,stat.st_mtime_ns)!=(after.st_size,after.st_mtime_ns): raise RuntimeError('File changed during hashing')
    entries.append(dict(path=str(path.relative_to(root)),size=stat.st_size,mtime_ns=stat.st_mtime_ns,sha256=h.hexdigest()))
    total+=stat.st_size
    if len(entries)%100==0: print(len(entries),total,flush=True)
temp=dest/'upload-manifest.json.tmp'
temp.write_text(json.dumps(dict(files=entries,count=len(entries),bytes=total,created=time.time()),ensure_ascii=False))
temp.replace(dest/'upload-manifest.json')
print('Manifest complete',len(entries),total,flush=True)
