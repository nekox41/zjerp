import json, subprocess, urllib.request, sys, time, hashlib, os, getpass
from pathlib import Path
base=Path('/vol2/zjerp-migration/source/zjerp_backend-master')
dest=Path('/vol2/zjerp-migration')/os.environ.get('MIGRATION_STAGE','rehearsal')
url='http://127.0.0.1:8088/prod-api'
def request(path, data=None, token=None, timeout=120):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    body=None if data is None else json.dumps(data).encode()
    endpoint=url
    if path=='/system/file/migrate':
        container=subprocess.check_output(['docker','compose','ps','-q','backend'],cwd=base).decode().strip()
        address=subprocess.check_output(['docker','inspect','--format','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}',container]).decode().strip()
        endpoint='http://'+address+':8080'
    with urllib.request.urlopen(urllib.request.Request(endpoint+path,data=body,headers=headers),timeout=timeout) as response:
        return json.load(response)
payload={'username':os.environ.get('MIGRATION_ADMIN_USERNAME','admin'),'password':os.environ.get('MIGRATION_ADMIN_PASSWORD') or getpass.getpass('Administrator password: ')}
login=request('/login',payload)
if login.get('code')!=200: raise SystemExit('Login failed: '+str({k:v for k,v in login.items() if k!='token'}))
token=login['token']
print('Original administrator login verified')
info=request('/getInfo',token=token)
routers=request('/getRouters',token=token)
assert info.get('code')==200 and routers.get('code')==200
print('User info and routes verified')
if sys.argv[1]=='migrate':
    start=time.time()
    result=request('/system/file/migrate',{},token,timeout=7200)
    report={'duration_seconds':time.time()-start,'response':result}
    (dest/'file-migration-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False))
else:
    listing=request('/system/file/list?pageNum=1&pageSize=5',token=token)
    print('File listing:',listing.get('code'),'total:',listing.get('total'))
    for path in ['/management/contract/list?pageNum=1&pageSize=2','/management/report/list?pageNum=1&pageSize=2','/management/wbcard/list?pageNum=1&pageSize=2','/wbcard/gen/listtemplate']:
        result=request(path,token=token)
        assert result.get('code')==200, (path,result.get('msg'))
        print(path.split('?')[0],result.get('total'))
    if sys.argv[1]=='verify' and (dest/'business-file-verification.json').exists():
        from collections import Counter
        groups=json.loads((dest/'legacy-groups.json').read_text())
        mappings=[('contract','/management/contract/','contractFile'),('report','/management/report/','reportFile'),('report_attach','/management/report/','reportAttach'),('wbcard','/management/wbcard/','file')]
        for kind,path,field in mappings:
            group=next(x for x in groups if x['kind']==kind)
            business=request(path+str(group['id']),token=token)['data']
            files=request('/system/file/listByBiz?bizType='+kind+'&bizId='+str(group['id']),token=token)['data']
            assert files and Counter((business.get(field) or '').split(','))==Counter(x['fileUrl'] for x in files),(kind,group['id'])
            print('Business detail attachment display verified:',kind)
    if sys.argv[1]=='smoke-generation':
        templates=request('/wbcard/gen/listtemplate',token=token)['rows']
        payload={'name':'__migration_smoke_20261028','company':'迁移演练验证','leader':templates[0]['name'],'phone':'00000000000','system1':'演练系统一','system2':'演练系统二','time':'2026-10-28','no':'MIGRATION-SMOKE-20261028'}
        result=request('/wbcard/gen/create',payload,token)
        assert result.get('code')==200, result
        records=request('/management/wbcard/list?code=MIGRATION-SMOKE-20261028',token=token)['rows']
        records=[row for row in records if row.get('code')=='MIGRATION-SMOKE-20261028']
        assert len(records)==1, 'Expected exactly one test card'
        identifier=records[0]['id']
        files=request('/system/file/listByBiz?bizType=wbcard&bizId='+str(identifier),token=token)['data']
        assert len(files)==2, 'Expected card and attachment'
        for record in files:
            assert record['fileHash'] and record['fileSize']>0
            with urllib.request.urlopen('http://127.0.0.1:8088'+record['fileUrl']) as response:
                content=response.read()
            assert content.startswith(b'%PDF-') and hashlib.sha256(content).hexdigest()==record['fileHash']
        req=urllib.request.Request(url+'/management/wbcard/'+str(identifier),method='DELETE',headers={'Authorization':'Bearer '+token})
        with urllib.request.urlopen(req) as response: assert json.load(response)['code']==200
        print('Production template/font, PDF generation, both generated attachments, hashes and business deletion verified')
    if sys.argv[1]=='smoke-upload':
        for name in ['中文 空格.txt','逗号,分号;.txt','百分%加+井#.txt']:
            content=('Migration upload verification: '+name).encode()
            boundary='MigrationBoundary20261028'
            body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: text/plain\r\n\r\n').encode()+content+f'\r\n--{boundary}--\r\n'.encode()
            req=urllib.request.Request(url+'/system/file/upload',data=body,headers={'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary})
            with urllib.request.urlopen(req) as response: result=json.load(response)
            assert result.get('code')==200, result
            record=result['data']
            assert record['fileName']==name and record['fileHash']==hashlib.sha256(content).hexdigest()
            assert record['fileUrl'].startswith('/profile/files/')
            for prefix in ['http://127.0.0.1:8088','http://127.0.0.1:8088/prod-api']:
                with urllib.request.urlopen(prefix+record['fileUrl']) as response: assert response.read()==content
            req=urllib.request.Request(url+'/system/file/'+str(record['fileId']),method='DELETE',headers={'Authorization':'Bearer '+token})
            with urllib.request.urlopen(req) as response: assert json.load(response)['code']==200
            print('Special-name upload, both download routes, metadata and deletion verified:',name)
request('/logout',{},token)
