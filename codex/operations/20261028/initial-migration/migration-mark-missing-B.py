import importlib.util,json,os
from pathlib import Path
spec=importlib.util.spec_from_file_location('db','/vol2/zjerp-migration/migration-db-B.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
report=json.loads((Path('/vol2/zjerp-migration')/os.environ.get('MIGRATION_STAGE','rehearsal')/'preflight-attachments.json').read_text())
errors=report['errors']
assert len(errors)==2 and {(x['kind'],x['id']) for x in errors}=={('wbcard',164),('wbcard',213)}
def literal(value): return 'CONVERT(0x'+value.encode().hex()+' USING utf8mb4)'
for item in errors:
    assert item['error']=='missing source'
    url=item['url']; relative=url.removeprefix('/profile/'); identifier=int(item['id'])
    sql=f'''INSERT INTO sys_file(file_name,file_path,file_url,file_hash,file_size,file_type,file_ext,biz_type,biz_id,remark,create_by)
SELECT 'attachment.pdf',{literal(relative)},{literal(url)},NULL,NULL,'application/pdf','pdf','wbcard',{identifier},'legacy_source_missing; content_not_verified; confirmed_preserve_20261028','admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_file WHERE biz_type='wbcard' AND biz_id={identifier} AND file_url={literal(url)} AND del_flag='0');'''
    module.db(sql)
print('Preserved two historically missing references with NULL content metadata')
