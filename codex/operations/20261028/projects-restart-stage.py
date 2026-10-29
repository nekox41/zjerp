import subprocess,json,re,time
from pathlib import Path
root=Path('/vol2/zjerp-migration/projects');work=root/'check';db=json.loads((work/'state.json').read_text())['database'];assert re.fullmatch(r'work_project_check_\d+',db)
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$@"','sh',db],input=s.encode()).decode()
assert sql("SELECT COUNT(*) FROM sys_role WHERE role_key='work_project_stage_20261028';").strip()=='1'
subprocess.run(['docker','rm','-f','zjerp-work-project-check-backend','zjerp-work-project-check-frontend'],check=True)
subprocess.run(['docker','run','-d','--name','zjerp-work-project-check-backend','--network','zjerp-production_default','--env-file',str(work/'backend.env'),'zjerp-backend:projects-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
 status=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-work-project-check-backend']).decode().strip()
 if status=='healthy':break
 if status=='unhealthy':raise RuntimeError('Stage backend unhealthy')
 time.sleep(2)
else:raise RuntimeError('Stage timeout')
subprocess.run(['docker','run','-d','--name','zjerp-work-project-check-frontend','--network','zjerp-production_default','-p','127.0.0.1:18089:80','-v',str(work/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','zjerp-frontend:projects-20261028'],check=True,stdout=subprocess.DEVNULL)
print('Updated project stage ready')
