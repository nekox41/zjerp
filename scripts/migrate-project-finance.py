#!/usr/bin/env python3
"""One-time audited report and legacy-expense migration; preserve all source rows."""
import argparse,subprocess,json,re,uuid
from pathlib import Path
from decimal import Decimal
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--mysql-container',required=True);p.add_argument('--apply',action='store_true');p.add_argument('--report',required=True);a=p.parse_args()
assert a.apply and re.fullmatch(r'[A-Za-z0-9_]+',a.database) and re.fullmatch(r'[A-Za-z0-9_-]+',a.mysql_container)
def sql(s):return subprocess.check_output(['docker','exec','-i',a.mysql_container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 --batch --raw -uroot -N "$1"','sh',a.database],input=s.encode()).decode().strip()
def literal(v):
 if v is None:return 'NULL'
 return "CONVERT(X'"+str(v).encode().hex()+"' USING utf8mb4)"
def rows(t):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM '+t).splitlines()]
 return [json.loads(line,parse_float=Decimal) for line in sql('SELECT JSON_OBJECT('+','.join("'"+c+"',`"+c+'`' for c in cols)+') FROM '+t).splitlines()]
def dump(v):return json.dumps(v,ensure_ascii=False,default=str)
version='project-finance-20261029'
old=sql('SELECT report FROM zj_project_finance_migration WHERE CAST(version AS BINARY)=CAST('+literal(version)+' AS BINARY)')
if old:Path(a.report).write_text(old,encoding='utf8');print('Migration already applied');raise SystemExit(0)
projects=rows('zj_work_project');reports=rows('zj_report');source_files=rows('sys_file');costs=rows('zj_cost') if sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zj_cost'")=='1' else []
index_no={};index_name={}
def index(project,no=None,name=None):
 typ=project['project_type'];no=no or project['project_no'];name=name or project['project_name']
 if no:index_no.setdefault((typ,str(no).strip()),set()).add(project['id'])
 if name:index_name.setdefault((typ,str(name).strip()),set()).add(project['id'])
for project in projects:index(project)
stats=dict(reports=len(reports),matched_number=0,matched_name=0,created_projects=0,report_documents=0,expenses=len(costs),unknown_report_types=0)
# Actual writes are sent in one transaction below; source rows remain untouched.
statements=[];next_project=int(sql('SELECT COALESCE(MAX(id),0) FROM zj_work_project'))
next_doc=int(sql('SELECT COALESCE(MAX(id),0) FROM zj_project_document'))
files_by={}
for f in source_files:files_by.setdefault((f.get('biz_type'),f.get('biz_id')),[]).append(f)
for report in reports:
 raw=report.get('report_type');typ=raw if raw in ['WB','JC','PD','PG','QT'] else 'QT';unknown=typ!=raw;stats['unknown_report_types']+=unknown
 no=str(report.get('report_id') or '').strip();name=str(report.get('report_name') or '').strip();candidates=index_no.get((typ,no),set()) if no else set()
 if len(candidates)==1:pid=next(iter(candidates));stats['matched_number']+=1
 else:
  candidates=index_name.get((typ,name),set()) if name else set()
  if len(candidates)==1:pid=next(iter(candidates));stats['matched_name']+=1
  else:
   next_project+=1;pid=next_project;code='LEGACY-REPORT-'+str(report['id']);title=name or '历史报告项目 '+str(report['id'])
   statements.append('INSERT INTO zj_work_project(id,project_type,project_no,project_name,legacy_number,number_pending,classification_pending,created_by) VALUES('+','.join(map(literal,[pid,typ,code,title,no or None,1,1 if unknown else 0,'legacy-report-migration']))+');')
   statements.append('INSERT INTO zj_work_project_number(project_no,project_id) VALUES('+literal(code)+','+str(pid)+');')
   index(dict(id=pid,project_type=typ,project_no=code,project_name=title),no,name);stats['created_projects']+=1
 for role,field in [('report','report_file'),('report_attach','report_attach')]:
  linked=[f for f in files_by.get((role,report['id']),[]) if f.get('del_flag')=='0'];urls=','.join(f['file_url'] for f in linked) or (report.get(field) or '')
  if role=='report_attach' and not urls:continue
  next_doc+=1;kind='report' if role=='report' else 'report_attachment';title=(name or no or '历史报告')+(' / 报告' if role=='report' else ' / 附件')
  statements.append('INSERT INTO zj_project_document(id,project_id,name,kind,files,legacy_report_id,legacy_role,legacy_snapshot,created_by) VALUES('+','.join(map(literal,[next_doc,pid,title[:255],kind,urls,report['id'],role,dump(report),'legacy-report-migration']))+');')
  for f in linked:
   cols=['file_name','file_path','file_url','file_hash','file_size','file_type','file_ext','biz_type','biz_id','del_flag','create_by','remark'];copy=dict(f,biz_type='project_document',biz_id=next_doc)
   statements.append('INSERT INTO sys_file('+','.join(cols)+') VALUES('+','.join(literal(copy.get(c)) for c in cols)+');')
  stats['report_documents']+=1
 statements.append('INSERT INTO zj_work_project_legacy(project_id,source_table,source_id,snapshot_json) VALUES('+str(pid)+",'zj_report',"+str(report['id'])+','+literal(dump(report))+');')
links={int(x.split('\t')[0]):x.split('\t')[1:] for x in sql('SELECT source_cost_id,project_id,COALESCE(user_id,0) FROM zj_work_project_expense_link').splitlines()}
users={u['user_id']:u for u in rows('sys_user')}
for c in costs:
 link=links.get(c['id']);pid=int(link[0]) if link else None;uid=int(link[1]) if link and int(link[1]) in users else None;amount=Decimal(str(c.get('cost') or 0)).quantize(Decimal('0.01'));snapshot=dump(c)
 values=[str(uuid.uuid4()),str(uuid.uuid4()),amount,c.get('remark') or '历史报账',pid,uid,users[uid]['nick_name'] if uid else c.get('participant'),str(c['date'])[:10] if c.get('date') else None,c.get('remark'),c.get('attachment'),'legacy','pending',c['id'],snapshot,'legacy-finance-migration']
 statements.append('INSERT INTO zj_reimbursement(receipt,client_token,amount,purpose,project_id,user_id,claimant_name,expense_date,remark,invoices,source,status,legacy_cost_id,legacy_snapshot,created_by) VALUES('+','.join(map(literal,values))+');')
statements.append("INSERT IGNORE INTO zj_wbcard_number(code,card_id) SELECT code,MIN(id) FROM zj_wbcard WHERE code IS NOT NULL AND code<>'' AND CHAR_LENGTH(code)<=100 GROUP BY code;")
statements.append('INSERT INTO zj_project_finance_migration(version,report) VALUES('+literal(version)+','+literal(dump(stats))+');')
sql('START TRANSACTION;\n'+'\n'.join(statements)+'\nCOMMIT;')
assert int(sql('SELECT COUNT(DISTINCT legacy_report_id) FROM zj_project_document'))==len(reports)
Path(a.report).write_text(dump(stats),encoding='utf8');print(dump(stats))
