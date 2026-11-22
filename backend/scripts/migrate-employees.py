#!/usr/bin/env python3
"""Preserve original users/materials and attach independent employees to business data."""
import argparse,subprocess,json,re
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--mysql-container',required=True);p.add_argument('--apply',action='store_true');p.add_argument('--report',required=True);a=p.parse_args();assert a.apply and re.fullmatch('[A-Za-z0-9_]+',a.database) and re.fullmatch('[A-Za-z0-9_-]+',a.mysql_container)
def sql(s):return subprocess.check_output(['docker','exec','-i',a.mysql_container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N --batch --raw "$1"','sh',a.database],input=s.encode()).decode().strip()
def lit(v):return 'NULL' if v is None else "CONVERT(X'"+str(v).encode().hex()+"' USING utf8mb4)"
def rows(t):
 cols=[x.split('\t')[0] for x in sql('SHOW COLUMNS FROM '+t).splitlines()];return [json.loads(s) for s in sql('SELECT JSON_OBJECT('+','.join("'"+c+"',`"+c+'`' for c in cols)+') FROM '+t).splitlines()]
version='employees-20261105';old=sql('SELECT report FROM zj_employee_migration WHERE BINARY version=BINARY '+lit(version))
if old:Path(a.report).write_text(old,encoding='utf8');print('Employee migration already applied');raise SystemExit(0)
users=rows('sys_user');materials=rows('sys_user_material');credentials=rows('zj_report_credential');employees=rows('zj_employee');files=rows('sys_file');nextid=max([u['user_id'] for u in users]+[e['id'] for e in employees]+[0]);statements=[];mapping={};byname={};stats=dict(users=0,materials=0,credentials=0,provisional=0)
for u in users:
 admin=u['user_id']==1 or u['user_name'].lower()=='admin';owned=any(m['user_id']==u['user_id'] for m in materials)
 if admin and not owned:continue
 key=u['user_id'];name=(u.get('nick_name') or u['user_name']).strip();active=not admin and u.get('status')=='0' and u.get('del_flag')=='0';remark='由用户资料迁入；账号与员工档案分别维护'+('，开发账号资料归档，不可用于业务选择' if admin else '')
 statements.append('INSERT INTO zj_employee(id,name,employee_type,active,remark,source_user_id,created_by) VALUES('+','.join(map(lit,[key,name,'待确认' if not admin else '归档',int(active),remark,key,'employee-migration']))+');');mapping[key]=key;byname.setdefault(name,[]).append(key);stats['users']+=1
for c in credentials:
 if c['kind']!='person':continue
 holder=(c.get('holder') or '').strip();matches=byname.get(holder,[])
 if len(matches)==1:key=matches[0]
 else:
  nextid+=1;key=nextid;stats['provisional']+=1;statements.append('INSERT INTO zj_employee(id,name,employee_type,remark,source_credential_id,created_by) VALUES('+','.join(map(lit,[key,holder or '历史持证人员待确认','待确认','由资质记录建立，身份与重名关系待核对',c['id'],'employee-migration']))+');')
 statements.append('UPDATE zj_report_credential SET employee_id='+str(key)+' WHERE id='+str(c['id'])+';');stats['credentials']+=1
for m in materials:
 if m['user_id'] not in mapping:raise RuntimeError('Material owner missing')
 isfile=m['value_type']!='string';statements.append('INSERT INTO zj_report_credential(kind,title,holder,certificate_no,files,employee_id,value_type,content,legacy_user_material_id,created_by) VALUES('+','.join(map(lit,['person',m['material_key'],next(u.get('nick_name') or u['user_name'] for u in users if u['user_id']==m['user_id']),'',m['content'] if isfile else '',mapping[m['user_id']],'file' if isfile else 'string',m['content'],m['id'],'employee-migration']))+'); SET @employee_credential_id=LAST_INSERT_ID();')
 if isfile:
  for f in files:
   if f.get('biz_type')=='user_material' and f.get('biz_id')==m['user_id'] and f.get('file_url')==m['content'] and f.get('del_flag')=='0':
    cols=['file_name','file_path','file_url','file_hash','file_size','file_type','file_ext','del_flag','create_by','remark'];statements.append('INSERT INTO sys_file('+','.join(cols)+',biz_type,biz_id) VALUES('+','.join(lit(f.get(k)) for k in cols)+",'report_credential',@employee_credential_id);")
 stats['materials']+=1
for table,column,source in [('zj_work_project','leader_employee_id','leader_user_id'),('zj_work_project','technical_employee_id','technical_user_id'),('zj_work_project_plan','assignee_employee_id','assignee_id'),('zj_reimbursement','employee_id','user_id')]:
 statements.append('UPDATE '+table+' b JOIN zj_employee e ON e.source_user_id=b.'+source+' SET b.'+column+'=e.id WHERE b.'+column+' IS NULL;')
statements.append('INSERT INTO zj_employee_migration(version,report) VALUES('+lit(version)+','+lit(json.dumps(stats))+');');sql('START TRANSACTION;\n'+'\n'.join(statements)+'\nCOMMIT;');Path(a.report).write_text(json.dumps(stats),encoding='utf8');print(json.dumps(stats))
