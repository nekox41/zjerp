import json,subprocess,urllib.parse,re,zipfile
from pathlib import Path
data=json.loads(subprocess.check_output(['docker','inspect','backend']))[0]
env=dict(x.split('=',1) for x in data['Config']['Env'] if '=' in x)
print('Runtime configuration variable names:',sorted(k for k in env if k.startswith(('SPRING_','RUOYI_'))))
for key,value in env.items():
    if 'DATASOURCE' in key and key.endswith('URL'):
        parsed=urllib.parse.urlsplit(value.removeprefix('jdbc:'))
        print('Datasource target:',json.dumps(dict(key=key,host=parsed.hostname,port=parsed.port,database=parsed.path)))
    elif key in ('RUOYI_PROFILE','SPRING_PROFILES_ACTIVE'):
        print(key,value)
print('Network mode:',data['HostConfig']['NetworkMode'])
jar=Path.home()/'zjerp-migration/rehearsal/backend-running.jar'
if jar.exists():
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.startswith('BOOT-INF/classes/application') or not name.endswith(('.yml','.yaml','.properties')):continue
            text=archive.read(name).decode()
            for line in text.splitlines():
                if 'jdbc:mysql://' in line:
                    value=line.split('jdbc:',1)[1].split()[0].strip('\"\'')
                    parsed=urllib.parse.urlsplit(value)
                    print('JAR datasource:',json.dumps(dict(config=name,host=parsed.hostname,port=parsed.port,database=parsed.path)))
                elif re.match(r'^\s*(profile|active)\s*:',line):
                    print('JAR profile:',name,line.strip())
