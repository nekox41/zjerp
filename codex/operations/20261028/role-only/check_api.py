import json,time,secrets,getpass,urllib.request,urllib.error,urllib.parse
from pathlib import Path

root=Path('/vol2/zjerp-migration/role-only/check')
base='http://127.0.0.1:18089/prod-api'
passed=[]
def req(path,data=None,token=None,method=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=30) as response:return json.load(response)
    except urllib.error.HTTPError as error:
        try:return json.load(error)
        except ValueError:return {'code':error.code}
def good(result):assert result.get('code')==200,result.get('msg')
def mark(text):passed.append(text);print('PASS',text,flush=True)
def login(username,password):
    result=req('/login',dict(username=username,password=password));good(result);return result['token']
def auth(user,roles,token):
    query=urllib.parse.urlencode([('userId',user)]+[('roleIds',r) for r in roles])
    good(req('/system/user/authRole?'+query,{},token,method='PUT'))

good(req('/health'))
password=getpass.getpass('Stage administrator password: ')
admin=login('admin',password)
info=req('/getInfo',token=admin);good(info)
# Stop before creating data if nginx ever points at the live production instance.
assert 'stage_probe_20261028' in info['roles'],'Not the isolated stage instance'
mark('minimal username/password login; isolated instance identity verified')
assert req('/login',dict(username='admin',password=secrets.token_hex(8)))['code']!=200
admin=login('admin',password)
mark('wrong password rejected without requesting a captcha')
for path in ['/captchaImage','/system/dept/list','/system/post/list','/system/user/deptTree','/system/role/deptTree/1']:
    result=req(path,token=admin)
    assert result.get('code') in (404,405), (path,result)
mark('retired captcha, department and post routes absent')
detail=req('/system/user/1',token=admin);good(detail)
assert not {'deptId','dept','postIds'} & detail['data'].keys()
assert not {'postIds','posts'} & detail.keys()
assert all(not {'dataScope','deptIds','deptCheckStrictly'} & r.keys() for r in detail['roles'])
original_roles=detail['roleIds']
mark('user and role DTOs contain no organization/data-scope fields')
menus=req('/system/menu/list',token=admin);good(menus)
rows=menus['data']; byid={r['menuId']:r for r in rows}
assert not any((r.get('perms') or '').startswith(('system:dept:','system:post:')) for r in rows)
leaf=next(r for r in rows if r.get('perms')=='system:user:list')
menuids=[leaf['menuId']]; parent=leaf['parentId']
while parent:menuids.append(parent);parent=byid[parent]['parentId']
suffix=str(int(time.time()))[-7:]
rolekey='role_check_'+suffix
rolebody=dict(roleName='Role check '+suffix,roleKey=rolekey,roleSort=50,status='0',menuCheckStrictly=True,menuIds=menuids)
good(req('/system/role',rolebody,admin))
roles=req('/system/role/list?roleKey='+rolekey,token=admin);good(roles)
roleid=next(r['roleId'] for r in roles['rows'] if r['roleKey']==rolekey)
rolebody['roleId']=roleid
testpassword=secrets.token_hex(8)
def createuser(name,roleids):
    good(req('/system/user',dict(userName=name,nickName='Role check',password=testpassword,status='0',roleIds=roleids),admin))
    rows=req('/system/user/list?userName='+name,token=admin)['rows']
    return next(r['userId'] for r in rows if r['userName']==name)
normalname='role_user_'+suffix
normalid=createuser(normalname,[roleid]);normal=login(normalname,testpassword)
good(req('/system/user/list',token=normal))
assert req('/system/config/list',token=normal)['code']==403
assert 'roles' in req('/system/user/list?userName='+normalname,token=admin)['rows'][0]
mark('normal account receives only its assigned role menu/button permissions')
rolebody['menuIds']=[];good(req('/system/role',rolebody,admin,method='PUT'))
assert req('/system/user/list',token=normal)['code']==403
rolebody['menuIds']=menuids;good(req('/system/role',rolebody,admin,method='PUT'))
good(req('/system/user/list',token=normal))
good(req('/system/role/changeStatus',dict(roleId=roleid,status='1'),admin,method='PUT'))
assert req('/system/user/list',token=normal)['code']==403
good(req('/system/role/changeStatus',dict(roleId=roleid,status='0'),admin,method='PUT'))
good(req('/system/user/list',token=normal))
mark('role menu revocation/grant and role disable/enable take effect on the existing token')
adminrole=next(r['roleId'] for r in detail['roles'] if r['roleKey']=='admin')
othername='role_admin_'+suffix
otherid=createuser(othername,[adminrole]);other=login(othername,testpassword)
assert otherid!=1
good(req('/system/config/list',token=other))
mark('administrator role works on a non-1 account')
try:
    auth(1,[],other)
    assert req('/system/user/list',token=admin)['code']==403
    assert req('/getInfo',token=admin)['roles']==[]
    mark('account ID 1 loses privileges when its roles are removed')
finally:auth(1,original_roles,other)
good(req('/system/user/list',token=admin))
stats=req('/system/dashboard/userStat',token=admin);good(stats)
assert 'roles' in stats['data'] and 'posts' not in stats['data']
mark('employee statistics use roles')
for token in [normal,other,admin]:good(req('/logout',{},token))
(root/'api-validation.json').write_text(json.dumps(dict(passed=passed,checks=len(passed),success=True),ensure_ascii=False,indent=2))
print('All isolated API checks passed',flush=True)
