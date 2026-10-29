import json,re,subprocess
from pathlib import Path
db=json.loads(Path('/vol2/zjerp-migration/contracts/check/state.json').read_text())['database'];assert re.fullmatch(r'contract_check_\d+',db)
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --raw --skip-column-names "$@"','sh',db],input=s.encode()).decode()
assert sql("SELECT COUNT(*) FROM sys_role WHERE role_key='stage_probe_20261028';").strip()=='1'
sql("DELETE FROM zj_contract_identity WHERE id IN (SELECT id FROM zj_contract WHERE contract_id LIKE 'TEST-%' AND contract_name LIKE 'Validation %'); DELETE FROM zj_intermediary WHERE name LIKE 'Validation %';")
print('Only isolated test contracts and contacts reset')
