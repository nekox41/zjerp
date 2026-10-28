#!/usr/bin/env python3
# coding: utf-8
"""One-shot, audited project migration. Operate on a stopped application or an isolated copy."""
import argparse,json,re,subprocess
from collections import Counter
from decimal import Decimal
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--mysql-container',required=True);p.add_argument('--apply',action='store_true');p.add_argument('--report',required=True);a=p.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_]+',a.database) and re.fullmatch(r'[A-Za-z0-9_.-]+',a.mysql_container)
base=Path(__file__).resolve().parents[1]
def sql(s):
 return subprocess.check_output(['docker','exec','-i',a.mysql_container,'sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$@"','sh',a.database],input=s.encode()).decode()
def q(v):
 if v is None:return 'NULL'
 if isinstance(v,bool):return '1' if v else '0'
 if isinstance(v,(int,Decimal)):return str(v)
 if isinstance(v,float):return repr(v)
 return "CONVERT(X'"+str(v).encode().hex()+"' USING utf8mb4)"
def rows(t):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM `'+t+'`;').splitlines()]
 return [json.loads(x,parse_float=Decimal) for x in sql('SELECT JSON_OBJECT('+','.join(q(c)+',`'+c+'`' for c in cols)+') FROM `'+t+'`;').splitlines()]
def dump(v):return json.dumps(v,ensure_ascii=False,default=str,separators=(',',':'))
tables=sql('SHOW TABLES;').splitlines();assert 'zj_work_project' not in tables,'Migration already initialized; do not rerun'
codes=rows('zj_project_code');systems=rows('zjerp_project_system');contracts=rows('zj_contract');people=rows('zj_intermediary');users=rows('sys_user')
groups=rows('zj_project_cost') if 'zj_project_cost' in tables else [];expenses=rows('zj_cost') if 'zj_cost' in tables else []
report=dict(source_codes=len(codes),source_systems=len(systems),source_expense_groups=len(groups),source_expenses=len(expenses),pending_contracts=0,pending_numbers=0,pending_types=0,created_intermediaries=0,owner_pending=0,linked_expenses=0,unresolved_expense_users=0)
if not a.apply:Path(a.report).write_text(dump(report),encoding='utf-8');print(dump(report));raise SystemExit(0)
sql((base/'sql/work_projects_schema.sql').read_text(encoding='utf-8'))
statements=['START TRANSACTION;'];newpeople={};nextperson=max([int(x['id']) for x in people]+[0])+1
for c in contracts:
 if c.get('ownership')!='pending':continue # Preserve manually confirmed ownership.
 raw=(c.get('manager') or '').strip()
 if raw=='公司':statements.append("UPDATE "+{'WB':'zj_contract_maintenance','JC':'zj_contract_inspection','PD':'zj_contract_rating','PG':'zj_contract_assessment','QT':'zj_contract_other'}[c['contract_type']]+" SET ownership='company',intermediary_id=NULL WHERE id="+q(c['id'])+';');continue
 if raw in ['', '/', '未知', '不详', '无'] or len(raw)>100:report['owner_pending']+=1;continue
 matches=[x for x in people if x['name'].strip()==raw]
 if len(matches)>1:report['owner_pending']+=1;continue
 if matches:person=matches[0]['id']
 elif raw in newpeople:person=newpeople[raw]
 else:
  person=nextperson;nextperson+=1;newpeople[raw]=person;report['created_intermediaries']+=1
  statements.append('INSERT INTO zj_intermediary(id,name,remark) VALUES ('+','.join(map(q,[person,raw,'由历史合同归属导入，联系方式待补充']))+');')
 table={'WB':'zj_contract_maintenance','JC':'zj_contract_inspection','PD':'zj_contract_rating','PG':'zj_contract_assessment','QT':'zj_contract_other'}[c['contract_type']]
 statements.append('UPDATE '+table+" SET ownership='intermediary',intermediary_id="+q(person)+' WHERE id='+q(c['id'])+';')
projects=[];links=[];reserved=set();sequences={};maxid=max([int(x['id']) for x in codes]+[0])
def add(source,t,pid,number,kind,type_pending=False,details=None):
 name=(source.get('name') or source.get('project_name') or '').strip() or '历史未命名项目'
 oldnum=number;number_pending=False
 if not number or number in reserved:number='LEGACY-'+kind.upper()+'-'+str(source['id']);number_pending=True
 match=re.fullmatch(r'ZJXF(\d{4})(\d{2})(WB|JC|PD|PG|QT)(\d+)',number)
 if not match:number_pending=True
 else:
  key=(int(match[1]),match[3]);sequences[key]=max(sequences.get(key,0),int(match[4]))
 reserved.add(number)
 candidates=[c for c in contracts if c['contract_type']==t and (c.get('contract_name') or '').strip()==name]
 contract=candidates[0]['id'] if len(candidates)==1 else None
 if contract is None:report['pending_contracts']+=1
 if number_pending:report['pending_numbers']+=1
 if type_pending:report['pending_types']+=1
 project=dict(id=pid,project_type=t,project_no=number,project_name=name,contract_id=contract,details_json=dump(details) if details else None,remark=source.get('remark'),legacy_number=oldnum,number_pending=number_pending,classification_pending=type_pending,created_by=source.get('create_by') or 'migration',created_at=source.get('create_time'),updated_by='migration')
 projects.append(project);links.append((pid,kind,source['id'],dump(source)));return project
for source in codes:
 raw=source.get('type');t=raw if raw in ['WB','JC','PD','PG','QT'] else 'QT';add(source,t,source['id'],source.get('code'),'zj_project_code',raw not in ['WB','JC','PD','PG','QT'])
for source in systems:
 matches=[x for x in projects if x['project_name']==(source.get('project_name') or '').strip()]
 payload={'legacySystem':source.get('project_system'),'legacyQrcode':source.get('qrcode')}
 if len(matches)==1:
  target=matches[0];target['details_json']=dump(payload);links.append((target['id'],'zjerp_project_system',source['id'],dump(source)))
 else:
  maxid+=1;t='WB' if '维护保养' in (source.get('project_name') or '') else 'QT';add(source,t,maxid,None,'zjerp_project_system',t=='QT',payload)
for group in groups:
 matches=[x for x in projects if x['project_name']==(group.get('project_name') or '').strip()]
 if len(matches)==1:target=matches[0];links.append((target['id'],'zj_project_cost',group['id'],dump(group)))
 else:maxid+=1;target=add(group,'QT',maxid,None,'zj_project_cost',True)
 for expense in [x for x in expenses if x.get('project_id')==group['id']]:
  candidates=[u for u in users if u.get('del_flag')=='0' and (expense.get('participant') or '').strip() in [u.get('user_name'),u.get('nick_name')]]
  uid=candidates[0]['user_id'] if len(candidates)==1 else None
  if uid is None:report['unresolved_expense_users']+=1
  report['linked_expenses']+=1;statements.append('INSERT INTO zj_work_project_expense_link(project_id,source_cost_id,user_id) VALUES ('+','.join(map(q,[target['id'],expense['id'],uid]))+');') # moved after project inserts below
expense_statements=[x for x in statements if x.startswith('INSERT INTO zj_work_project_expense_link')];statements=[x for x in statements if x not in expense_statements]
for project in projects:
 if project['created_at'] is None:project.pop('created_at')
 statements.append('INSERT INTO zj_work_project ('+','.join(project)+') VALUES ('+','.join(q(v) for v in project.values())+');')
 statements.append('INSERT INTO zj_work_project_number(project_no,project_id) VALUES ('+q(project['project_no'])+','+q(project['id'])+');')
for pid,kind,sid,snapshot in links:statements.append('INSERT INTO zj_work_project_legacy(project_id,source_table,source_id,snapshot_json) VALUES ('+','.join(map(q,[pid,kind,sid,snapshot]))+');')
for (year,t),value in sequences.items():statements.append('INSERT INTO zj_work_project_sequence(number_year,project_type,sequence_value) VALUES ('+','.join(map(q,[year,t,value]))+');')
statements+=expense_statements;report['projects']=len(projects);report['counts']=dict(Counter(x['project_type'] for x in projects))
statements.append("INSERT INTO zj_work_project_migration(version,report_json) VALUES ('work-projects-20261028',"+q(dump(report))+');');statements.append('COMMIT;');sql('\n'.join(statements))
assert int(sql('SELECT COUNT(*) FROM zj_work_project;'))==len(projects)
assert int(sql('SELECT COUNT(*) FROM zj_work_project_legacy;'))==len(links)
Path(a.report).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(dump(report))
