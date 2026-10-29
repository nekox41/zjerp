import getpass,json,urllib.request,urllib.error,sys,base64
from pathlib import Path
port=int(sys.argv[1]) if len(sys.argv)>1 else 18089
base=f'http://127.0.0.1:{port}/prod-api'
def req(path,method='GET',data=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 r=urllib.request.Request(base+path,method=method,headers=headers,data=json.dumps(data).encode() if data is not None else None)
 try:
  with urllib.request.urlopen(r,timeout=30) as response:return json.load(response)
 except urllib.error.HTTPError as e:return json.load(e)
assert req('/health')['code']==200
login=req('/login','POST',{'username':'admin','password':getpass.getpass('Administrator password: ')})
assert login['code']==200,login
t=login['token']; path='/system/user/1/materials'
assert req(path)['code']!=200
if port==18089:
 assert 'stage_probe_20261028' in req('/getInfo',token=t)['roles']
 key='validation_material'
 for value in ['CERT-001','CERT-002']:
  assert req(path,'PUT',dict(materialKey=key,valueType='string',content=value),t)['code']==200
  result=req(path+'?key='+key,token=t)['data']
  assert len(result)==1 and result[0]['userId']==1 and result[0]['content']==value and result[0]['materialKey']==key,result
 assert req(path,'PUT',dict(materialKey='bad',valueType='invalid',content='x'),t)['code']!=200
 assert req(path,'PUT',dict(materialKey='bad',valueType='png',content='https://example.com/file.png'),t)['code']!=200
 png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
 boundary='materialValidationBoundary'
 body=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="certificate.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+('\r\n--'+boundary+'--\r\n').encode()
 upload=urllib.request.Request(base+'/common/upload',data=body,headers={'Authorization':'Bearer '+t,'Content-Type':'multipart/form-data; boundary='+boundary})
 with urllib.request.urlopen(upload,timeout=30) as response: stored=json.load(response)
 assert stored['code']==200,stored
 file_url=stored['fileName']
 assert req(path,'PUT',dict(materialKey='file_probe',valueType='pdf',content=file_url),t)['code']!=200
 assert req(path,'PUT',dict(materialKey='file_probe',valueType='png',content=file_url),t)['code']==200
 assert req(path+'?key=file_probe',token=t)['data'][0]['content']==file_url
 with urllib.request.urlopen(f'http://127.0.0.1:{port}'+file_url) as response:assert response.read()==png
 assert req(path+'?key=file_probe','DELETE',token=t)['code']==200
 assert req(path+'?key='+key,'DELETE',token=t)['code']==200
 assert req(path+'?key='+key,token=t)['data']==[]
else:
 assert 'stage_probe_20261028' not in req('/getInfo',token=t)['roles']
 assert req(path,token=t)['code']==200
assert req('/logout','POST',{},t)['code']==200
print(json.dumps(dict(success=True,port=port,text_upsert_delete_verified=port==18089,invalid_type_and_file_rejected=port==18089)))
if port==18089:Path('/vol2/zjerp-migration/user-materials/check/verified.json').write_text(json.dumps({'success':True,'text_crud':True,'file_upload_download':True,'type_validation':True}))
