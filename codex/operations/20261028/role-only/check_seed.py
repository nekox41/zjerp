import subprocess,time,json,re
from pathlib import Path
root=Path('/vol2/zjerp-migration/role-only')
name='role_seed_'+str(int(time.time()));assert re.fullmatch(r'role_seed_\d+',name)
def sql(text,db=None):
    cmd=['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh']
    if db:cmd.append(db)
    return subprocess.check_output(cmd,input=text.encode()).decode()
sql('CREATE DATABASE `'+name+'` CHARACTER SET utf8mb4;')
try:
    base=root/'source/zjerp_backend-master/sql'
    for file in ['ry_20250417.sql','quartz.sql','business_schema.sql','sys_file.sql']:sql((base/file).read_text(),name)
    assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('sys_dept','sys_post','sys_user_post','sys_role_dept');",name).strip()=='0'
    assert sql("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=1 AND r.role_key='admin' AND r.status='0';",name).strip()=='1'
    assert sql("SELECT COUNT(*) FROM sys_config WHERE config_key='sys.account.captchaEnabled';",name).strip()=='0'
    (root/'check/fresh-install-validation.json').write_text(json.dumps(dict(success=True,admin_role_preserved=True,retired_tables=0)))
    print('Fresh install schema and admin role seeds verified')
finally:sql('DROP DATABASE `'+name+'`;')
