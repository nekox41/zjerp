import subprocess,json,time,re
from pathlib import Path
root=Path('/vol2/zjerp-migration/contracts');work=root/'check';state=json.loads((work/'state.json').read_text());db=state['database'];assert re.fullmatch(r'contract_check_\d+',db)
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh',db],input=s.encode()).decode()
assert sql("SELECT COUNT(*) FROM sys_role WHERE role_key='stage_probe_20261028';").strip()=='1'
sql("DELETE FROM zj_contract_identity WHERE id IN (SELECT id FROM zj_contract WHERE contract_id LIKE 'TEST-%' AND contract_name LIKE 'Validation %'); DELETE FROM zj_intermediary WHERE name LIKE 'Validation %';")
subprocess.run(['docker','rm','-f','zjerp-contract-check-backend'],check=True)
subprocess.run(['docker','run','-d','--name','zjerp-contract-check-backend','--network','zjerp-production_default','--env-file',str(work/'backend.env'),'zjerp-backend:contracts-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
 status=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-contract-check-backend']).decode().strip()
 if status=='healthy':break
 if status=='unhealthy':raise RuntimeError('Stage backend unhealthy')
 time.sleep(2)
else:raise RuntimeError('Stage timeout')
# Nginx resolves the backend address at startup.
subprocess.run(['docker','rm','-f','zjerp-contract-check-frontend'],check=True)
subprocess.run(['docker','run','-d','--name','zjerp-contract-check-frontend','--network','zjerp-production_default','-p','127.0.0.1:18089:80','-v',str(work/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','zjerp-frontend:contracts-20261028'],check=True,stdout=subprocess.DEVNULL)
print('Updated isolated backend ready')
