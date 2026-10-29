import importlib.util,json
from pathlib import Path
root=Path('/vol2/zjerp-migration')
spec=importlib.util.spec_from_file_location('db',str(root/'migration-db-B.py'));db=importlib.util.module_from_spec(spec);spec.loader.exec_module(db)
counts=[int(x) for x in db.db('SELECT COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file),"")<>"";').splitlines()]
files=db.db('SELECT COUNT(*),SUM(file_hash IS NULL OR file_size IS NULL),COALESCE(SUM(file_size),0) FROM sys_file WHERE del_flag="0";').strip().split('\t')
project=next(x.split('=',1)[1] for x in (root/'source/zjerp_backend-master/.env').read_text().splitlines() if x.startswith('COMPOSE_PROJECT_NAME='))
print(json.dumps(dict(project=project,remaining_groups=counts,total_remaining_groups=sum(counts),active_file_records=int(files[0]),unverified_records=int(files[1] or 0),verified_content_bytes=int(files[2]))))
