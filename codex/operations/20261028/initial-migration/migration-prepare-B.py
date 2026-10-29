from pathlib import Path
import hashlib
root = Path('/vol2/zjerp-migration/source')
backend = root / 'zjerp_backend-master'
for tree in [backend, root / 'zjerp_frontend-master']:
    for path in tree.rglob('*'):
        if path.is_dir(): path.chmod(0o755)
        elif path.is_file() and path.name != '.env': path.chmod(0o644)
    for path in tree.rglob('*.sh'):
        path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
        path.chmod(0o755)
env = backend / '.env'
values = dict(line.split('=', 1) for line in env.read_text().splitlines() if '=' in line)
if values.get('COMPOSE_PROJECT_NAME') not in (None, 'zjerp-rehearsal'):
    raise SystemExit('Refusing to change an existing non-rehearsal deployment')
values.update(HTTP_PORT='8088', PUBLIC_API_URL='http://10.5.250.181:8088/prod-api', FRONTEND_CONTEXT='../zjerp_frontend-master', BOOTSTRAP_DATABASE='false', COMPOSE_PROJECT_NAME='zjerp-rehearsal')
env.write_text(''.join(f'{k}={v}\n' for k,v in values.items()))
env.chmod(0o600)
for directory in [backend, root / 'zjerp_frontend-master']:
    text = (directory / 'Dockerfile').read_text()
    text = text.replace('# syntax=docker/dockerfile:1\n','')
    for image in ['maven:3.9-eclipse-temurin-17','eclipse-temurin:17-jre-jammy','node:22-alpine','nginx:stable-alpine']:
        text = text.replace('FROM '+image, 'FROM public.ecr.aws/docker/library/'+image)
    (directory / 'Dockerfile.migration').write_text(text)
(backend / 'compose.override.yaml').write_text('''services:
  mysql:
    image: public.ecr.aws/docker/library/mysql:9.6.0
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--event-scheduler=OFF"]
  redis:
    image: public.ecr.aws/docker/library/redis:7-alpine
  backend:
    build:
      dockerfile: Dockerfile.migration
    volumes:
      - /vol2/zjerp-migration/rehearsal/wbcard:/app/wbcard
      - /vol2/zjerp-migration/rehearsal/upload:/app/upload
  frontend:
    ports: !override
      - "127.0.0.1:8088:80"
    build:
      dockerfile: Dockerfile.migration
''')
archive = root / 'migration-source-20261028.tar.gz'
(root / 'SOURCE_SHA256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  '+archive.name+'\n')
print('Prepared isolated rehearsal configuration; secrets not displayed.')
