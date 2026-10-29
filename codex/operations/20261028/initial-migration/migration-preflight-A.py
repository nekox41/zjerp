import json, subprocess, re, os
from pathlib import Path
from urllib.parse import urlsplit, unquote
root=Path('/home/aa/app/zjerp/upload')
dest=Path.home()/'zjerp-migration'/os.environ.get('MIGRATION_STAGE','rehearsal')
dest.mkdir(mode=0o700,exist_ok=True)
mappings=[('zj_contract','contract_file','contract'),('zj_report','report_file','report'),('zj_report','report_attach','report_attach'),('zj_wbcard','file','wbcard')]
def query(sql):
    return subprocess.check_output(['docker','exec','-i','mysql','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names server'],input=sql.encode()).decode()
groups=[]; errors=[]; refs=0; total=0; addresses={}; resolved=set()
for table,col,kind in mappings:
    result=query(f'SELECT JSON_OBJECT("id",id,"urls",`{col}`) FROM `{table}` WHERE COALESCE(TRIM(`{col}`),"")<>"";')
    for row in result.splitlines():
        item=json.loads(row); item.update(table=table,column=col,kind=kind); groups.append(item)
        for url in dict.fromkeys(re.split('[,;]',item['urls'])):
            url=url.strip()
            if not url: continue
            refs+=1
            try:
                if url.startswith(('http://','https://')):
                    parsed=urlsplit(url); resource=parsed.path
                    addresses[parsed.netloc]=addresses.get(parsed.netloc,0)+1
                else: resource=url
                if not resource.startswith('/profile/'): raise ValueError('non-profile resource')
                rel=resource[len('/profile/'):]
                path=root/rel
                if not path.is_file(): path=root/unquote(rel)
                if not path.resolve().is_relative_to(root.resolve()): raise ValueError('outside root')
                if not path.is_file(): raise ValueError('missing source')
                total+=path.stat().st_size
                resolved.add(str(path.relative_to(root)))
            except Exception as exc:
                errors.append(dict(kind=kind,id=item['id'],url=url,error=str(exc)))
report=dict(groups=len(groups),references=refs,unique_sources=len(resolved),referenced_bytes=total,addresses=addresses,errors=errors)
(dest/'preflight-attachments.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
(dest/'legacy-groups.json').write_text(json.dumps(groups,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='errors'},ensure_ascii=False))
print('source_errors:',len(errors))
