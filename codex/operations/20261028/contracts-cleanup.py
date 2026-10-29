import json,re,subprocess
from pathlib import Path
root=Path('/vol2/zjerp-migration/contracts');work=root/'check'
assert json.loads((work/'api-validation.json').read_text())['success']
assert json.loads((root/'production-update/api-verification.json').read_text())['success']
state=json.loads((work/'state.json').read_text());name=state['database'];assert re.fullmatch(r'contract_check_\d+',name) and state['username']==name
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names'],input=s.encode()).decode()
assert sql("SELECT COUNT(*) FROM `"+name+"`.sys_role WHERE role_key='stage_probe_20261028';").strip()=='1'
subprocess.run(['docker','rm','-f','zjerp-contract-check-frontend','zjerp-contract-check-backend','zjerp-contract-check-redis'],check=True)
sql("DROP DATABASE `"+name+"`; DROP USER '"+name+"'@'%';")
state['password']='<retired>';state['cleaned_up']=True;(work/'state.json').write_text(json.dumps(state));(work/'state.json').chmod(0o600)
print('Removed only isolated contract test containers, database and test database user')
