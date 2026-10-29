import getpass,hashlib,json,urllib.request,urllib.error
from pathlib import Path
base='http://127.0.0.1:8088/prod-api'
def req(path,data=None,token=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    try:
        with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers),timeout=30) as response:return json.load(response)
    except urllib.error.HTTPError as error:return json.load(error)
assert req('/health')['code']==200
login=req('/login',dict(username='admin',password=getpass.getpass('Production administrator password: ')))
assert login['code']==200;token=login['token']
info=req('/getInfo',token=token);assert 'admin' in info['roles'] and 'stage_probe_20261028' not in info['roles']
assert not {'deptId','dept','postIds'} & info['user'].keys()
users=req('/system/user/list',token=token);assert users['code']==200
assert not any(x['userName'].startswith(('role_user_','role_admin_')) for x in users['rows'])
roles=req('/system/role/list',token=token);assert roles['code']==200
assert all(not {'dataScope','deptIds','deptCheckStrictly'} & x.keys() for x in roles['rows'])
menus=req('/system/menu/list',token=token);assert menus['code']==200
assert not any((x.get('perms') or '').startswith(('system:dept:','system:post:')) for x in menus['data'])
for path in ['/captchaImage','/system/dept/list','/system/post/list']:
    assert req(path,token=token)['code']==404
files=req('/system/file/list?pageNum=1&pageSize=10',token=token);assert files['code']==200
item=next(x for x in files['rows'] if x['fileHash'])
h=hashlib.sha256()
with urllib.request.urlopen('http://127.0.0.1:8088'+item['fileUrl']) as response:
    for block in iter(lambda:response.read(1024*1024),b''):h.update(block)
assert h.hexdigest()==item['fileHash']
assert req('/logout',{},token)['code']==200
result=dict(success=True,users=users['total'],roles=roles['total'],file_records=files['total'],login_without_captcha=True,retired_routes_absent=True,file_download_hash_verified=True)
Path('/vol2/zjerp-migration/role-only/production-update/api-verification.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
