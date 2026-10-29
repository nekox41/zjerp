import subprocess,json,hashlib,time
from pathlib import Path
root=Path('/vol2/zjerp-migration');change=root/'projects';base=root/'source/zjerp_backend-master';report=change/'production-update';report.mkdir(mode=0o700,exist_ok=True)
for name in ['api-validation.json','schema-validation.json','fresh-install-validation.json']:assert json.loads((change/'check'/name).read_text())['success'],name
assert not (report/'result.json').exists(),'Release already applied'
def run(a,**kw):return subprocess.run(a,check=True,**kw)
def sql(s):return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],input=s.encode()).decode()
owners={'zj_contract','zj_contract_maintenance','zj_contract_inspection','zj_contract_rating','zj_contract_assessment','zj_contract_other'}
def columns(t):return [x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+t+'`;').splitlines()]
def allrows(t):return [json.loads(x) for x in sql('SELECT JSON_OBJECT('+','.join("'"+x+"',`"+x+'`' for x in columns(t))+') FROM `'+t+'`;').splitlines()]
def integrity(tables):
 out={}
 for t in tables:
  cols=[x for x in columns(t) if not(t in owners and x in ['ownership','intermediary_id'])]
  values=sql('SELECT JSON_ARRAY('+','.join('`'+x+'`' for x in cols)+') FROM `'+t+'`;').splitlines()
  out[t]=dict(rows=len(values),sha256=hashlib.sha256('\n'.join(sorted(values)).encode()).hexdigest())
 return out
tags={}
for kind in ['backend','frontend']:
 image=json.loads(subprocess.check_output(['docker','image','inspect','zjerp-'+kind+':projects-20261028']))[0]['Id']
 assert image==json.loads(subprocess.check_output(['docker','inspect','zjerp-work-project-check-'+kind]))[0]['Image'],'Untested candidate image'
 tags[kind]='zjerp-'+kind+':20261028-projects-'+image.split(':')[1][:12];run(['docker','tag',image,tags[kind]])
env=base/'.env';override=base/'compose.override.yaml';(report/'before.env').write_bytes(env.read_bytes());(report/'before.env').chmod(0o600);(report/'before.override.yaml').write_bytes(override.read_bytes())
run(['docker','compose','stop','frontend','backend'],cwd=base)
run(['python3',str(root/'migration-post-backup-B.py')]);backup=(root/'final/post-migration-backup-path.txt').read_text().strip();(report/'backup-path.txt').write_text(backup)
print('Matched pre-upgrade backup ready: '+backup,flush=True)
tables=[x for x in sql('SHOW TABLES;').splitlines() if x not in ['sys_menu','sys_role_menu','zj_intermediary']];assert 'zj_work_project' not in tables
before=integrity(tables);people=allrows('zj_intermediary');original_contracts=allrows('zj_contract')
(report/'before-integrity.json').write_text(json.dumps(before,indent=2))
run(['python3',str(change/'source/zjerp_backend-master/scripts/migrate-work-projects.py'),'--database','server','--mysql-container','zjerp-production-mysql-1','--apply','--report',str(report/'migration-report.json')])
sql((change/'source/zjerp_backend-master/sql/work_projects_menus.sql').read_text())
after=integrity(tables);assert before==after,'Preserved data changed; application stays stopped for inspection'
newpeople={x['id']:x for x in allrows('zj_intermediary')};assert all(newpeople[x['id']]==x for x in people),'Existing intermediary changed'
now={x['id']:x for x in allrows('zj_contract')}
for old in original_contracts:
 current=now[old['id']]
 if old['ownership']!='pending':assert current['ownership']==old['ownership'] and current['intermediary_id']==old['intermediary_id']
 elif current['ownership']=='intermediary':assert newpeople[current['intermediary_id']]['name'].strip()==(old.get('manager') or '').strip()
 elif current['ownership']=='company':assert (old.get('manager') or '').strip()=='公司'
stats=json.loads((report/'migration-report.json').read_text());assert stats['linked_expenses']==stats['source_expenses'];assert stats['projects']==int(sql('SELECT COUNT(*) FROM zj_work_project;'))
(report/'after-integrity.json').write_text(json.dumps(after,indent=2))
values=dict(x.split('=',1) for x in env.read_text().splitlines() if '=' in x);values.update(BACKEND_IMAGE=tags['backend'],FRONTEND_IMAGE=tags['frontend'],FRONTEND_CONTEXT=str(change/'source/zjerp_frontend-master'))
env.write_text(''.join(k+'='+v+'\n' for k,v in values.items()));env.chmod(0o600);override.write_text(override.read_text().replace('/contracts/source/','/projects/source/'))
run(['docker','compose','config','--quiet'],cwd=base);run(['docker','compose','up','-d','--no-build','--wait','--wait-timeout','180','backend','frontend'],cwd=base)
(report/'result.json').write_text(json.dumps(dict(success=True,backup=backup,images=tags,stats=stats,preserved_tables=len(after),opened_at=time.strftime('%Y-%m-%d %H:%M:%S %z')),indent=2));print('Project workspace deployed; legacy business data preserved',flush=True)
