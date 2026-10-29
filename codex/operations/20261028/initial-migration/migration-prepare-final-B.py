import json,subprocess
from pathlib import Path
root=Path('/vol2/zjerp-migration')
base=root/'source/zjerp_backend-master'
dest=root/'final'
dest.mkdir(mode=0o700,exist_ok=True)
env=base/'.env'
values=dict(line.split('=',1) for line in env.read_text().splitlines() if '=' in line)
if values['COMPOSE_PROJECT_NAME']!='zjerp-rehearsal': raise SystemExit('Expected rehearsal project before preparing production')
images={
    'BACKEND_IMAGE':('sha256:777488d55a867904b5cbabfb9a66d9423126c54170e517b4f1c78c0dbb9f8fea','zjerp-backend:20261028-777488d55a86'),
    'FRONTEND_IMAGE':('sha256:f90c074e2030eab9be9f1bf0e8d30c8d9faf1c1f4a350fe4f4e03b030d3888b1','zjerp-frontend:20261028-f90c074e2030'),
}
for key,(identifier,tag) in images.items():
    subprocess.run(['docker','image','inspect',identifier],stdout=subprocess.DEVNULL,check=True)
    subprocess.run(['docker','tag',identifier,tag],check=True)
    values[key]=tag
subprocess.run(['docker','compose','stop','frontend','backend'],cwd=base,check=True)
(root/'rehearsal/env.rehearsal').write_bytes(env.read_bytes()); (root/'rehearsal/env.rehearsal').chmod(0o600)
(root/'rehearsal/compose.override.rehearsal.yaml').write_bytes((base/'compose.override.yaml').read_bytes())
inventory=json.loads((root/'rehearsal/upload-inventory.json').read_text())
target=dest/'upload'
if target.exists(): raise SystemExit('Refusing existing production upload target')
target.mkdir()
paths=[x['path'] for x in inventory['files']]
subprocess.run(['docker','run','--rm','--user','root','--entrypoint','sh','-v',str(root)+':/migration','-w','/migration/rehearsal/upload','zjerp-backend:local','-c','cp -a --reflink=always --parents -- "$@" /migration/final/upload && chown -R 1000:1001 /migration/final/upload','sh',*paths],check=True)
values['COMPOSE_PROJECT_NAME']='zjerp-production'
env.write_text(''.join(f'{k}={v}\n' for k,v in values.items()));env.chmod(0o600)
override=(base/'compose.override.yaml').read_text().replace('/rehearsal/','/final/')
for image in ['public.ecr.aws/docker/library/mysql:9.6.0','public.ecr.aws/docker/library/redis:7-alpine']:
    details=json.loads(subprocess.check_output(['docker','image','inspect',image]))[0]
    digest=next(x for x in details['RepoDigests'] if x.startswith('public.ecr.aws/'))
    override=override.replace('image: '+image,'image: '+digest)
(base/'compose.override.yaml').write_text(override)
print('Prepared independent production volumes and reflink file snapshot, HTTP remains loopback-only')
