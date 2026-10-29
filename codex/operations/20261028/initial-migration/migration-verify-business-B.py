import collections,hashlib,importlib.util,json,os,re,urllib.parse,urllib.request
from pathlib import Path
root=Path('/vol2/zjerp-migration');stage=os.environ.get('MIGRATION_STAGE','rehearsal');dest=root/stage
spec=importlib.util.spec_from_file_location('db',str(root/'migration-db-B.py'));db=importlib.util.module_from_spec(spec);spec.loader.exec_module(db)
groups=json.loads((dest/'legacy-groups.json').read_text())
manifest={x['path']:x for x in json.loads((dest/'upload-manifest.json').read_text())['files']}
missing=json.loads((dest/'preflight-attachments.json').read_text())['errors']
allowed={(x['kind'],x['id'],x['url']) for x in missing}
assert allowed=={('wbcard',164,'/profile/wbcard/伊川县蓝月亮娱乐服务有限公司/attachment.pdf'),('wbcard',213,'/profile/wbcard/兰考糖果歌城/attachment.pdf')}
rows=[json.loads(x) for x in db.db('SELECT JSON_OBJECT("id",file_id,"name",file_name,"path",file_path,"url",file_url,"hash",file_hash,"size",file_size,"kind",biz_type,"biz",biz_id,"remark",remark) FROM sys_file WHERE del_flag="0";').splitlines()]
bybiz=collections.defaultdict(list)
for row in rows:bybiz[(row['kind'],row['biz'])].append(row)
checked=0;sample={}
for group in groups:
    key=(group['kind'],group['id']);expected=[]
    for url in dict.fromkeys(re.split('[,;]',group['urls'])):
        url=url.strip()
        if not url:continue
        if (*key,url) in allowed:expected.append(('missing',url));continue
        path=urllib.parse.urlsplit(url).path if url.startswith(('http://','https://')) else url
        path=path.removeprefix('/profile/')
        if path not in manifest:path=urllib.parse.unquote(path)
        item=manifest[path]
        expected.append((item['sha256'],item['size'],Path(path).name))
    actual=[]
    for row in bybiz.pop(key,[]):
        if row['hash'] is None:
            assert (*key,row['url']) in allowed and 'legacy_source_missing' in row['remark'];actual.append(('missing',row['url']));continue
        file=dest/'upload'/row['path']
        assert file.is_file() and file.stat().st_size==row['size'],row['id']
        assert row['path'].startswith('files/'+row['hash'][:2]+'/')
        actual.append((row['hash'],row['size'],row['name']))
        if key[0] not in sample or sample[key[0]]['size']>row['size']:sample[key[0]]=row
    assert collections.Counter(expected)==collections.Counter(actual),key
    checked+=len(expected)
assert not bybiz, 'Unexpected active business associations'
legacy=db.db('SELECT COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach),"")<>"" UNION ALL SELECT COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file),"")<>"";')
assert legacy.split()==['0']*4
for kind,row in sample.items():
    for prefix in ['http://127.0.0.1:8088','http://127.0.0.1:8088/prod-api']:
        digest=hashlib.sha256()
        with urllib.request.urlopen(prefix+row['url'],timeout=120) as response:
            for block in iter(lambda:response.read(1024*1024),b''):digest.update(block)
        assert digest.hexdigest()==row['hash']
report=dict(groups=len(groups),references=checked,active_records=len(rows),missing_sources=len(allowed),http_download_categories=sorted(sample),legacy_fields_remaining=[0,0,0,0],verified=True)
(dest/'business-file-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False))
