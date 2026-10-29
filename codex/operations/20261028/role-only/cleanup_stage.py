import json,re,subprocess
from pathlib import Path
root=Path('/vol2/zjerp-migration/role-only/check')
assert json.loads((root/'api-validation.json').read_text())['success']
assert json.loads((root.parent/'production-update/api-verification.json').read_text())['success']
state=json.loads((root/'state.json').read_text())
name=state['database'];assert re.fullmatch(r'role_check_\d+',name) and state['username']==name
def sql(text):
    return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names'],input=text.encode()).decode()
assert sql('SELECT COUNT(*) FROM `'+name+'`.sys_role WHERE role_key="stage_probe_20261028";').strip()=='1'
subprocess.run(['docker','rm','-f','zjerp-role-check-frontend','zjerp-role-check-backend','zjerp-role-check-redis'],check=True)
sql('DROP DATABASE `'+name+'`; DROP USER \''+name+'\'@\'%\';')
state['password']='<retired>';state['cleaned_up']=True
(root/'state.json').write_text(json.dumps(state));(root/'state.json').chmod(0o600)
print('Removed only the validation containers, marked validation database and its dedicated account')
