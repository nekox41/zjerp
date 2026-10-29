import subprocess,time
from pathlib import Path
root=Path('/vol2/zjerp-migration/role-only/check')
subprocess.run(['docker','stop','zjerp-role-check-frontend','zjerp-role-check-backend'],check=True,stdout=subprocess.DEVNULL)
subprocess.run(['docker','rm','zjerp-role-check-backend'],check=True,stdout=subprocess.DEVNULL)
subprocess.run(['docker','run','-d','--name','zjerp-role-check-backend','--network','zjerp-production_default','--env-file',str(root/'backend.env'),'zjerp-backend:role-only-20261028'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
    value=subprocess.check_output(['docker','inspect','--format','{{.State.Health.Status}}','zjerp-role-check-backend']).decode().strip()
    if value=='healthy':break
    time.sleep(2)
else:raise SystemExit('Stage readiness timed out')
subprocess.run(['docker','start','zjerp-role-check-frontend'],check=True,stdout=subprocess.DEVNULL)
print('Stage backend updated and healthy')
