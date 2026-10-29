import json,re,subprocess
from pathlib import Path
root=Path('/vol2/zjerp-migration/projects');work=root/'check';assert json.loads((work/'api-validation.json').read_text())['success'];assert json.loads((root/'production-update/api-verification.json').read_text())['success']
state=json.loads((work/'state.json').read_text());db=state['database'];assert re.fullmatch(r'work_project_check_\d+',db) and db==state['username']
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names'],input=s.encode()).decode()
assert sql("SELECT COUNT(*) FROM `"+db+"`.sys_role WHERE role_key='work_project_stage_20261028';").strip()=='1'
subprocess.run(['docker','rm','-f','zjerp-work-project-check-frontend','zjerp-work-project-check-backend','zjerp-work-project-check-redis'],check=True)
sql("DROP DATABASE `"+db+"`; DROP USER '"+db+"'@'%';");state['password']='<retired>';state['cleaned_up']=True;(work/'state.json').write_text(json.dumps(state));(work/'state.json').chmod(0o600)
print('Isolated project test environment removed; production data untouched')
