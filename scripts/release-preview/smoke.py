import json,sys
from urllib.request import Request,urlopen
base="http://127.0.0.1:8089/next-api"
def call(path,data=None,token=None):
    h={"Content-Type":"application/json"}
    if token:h["Authorization"]="Bearer "+token
    with urlopen(Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=15) as r:return json.load(r)
assert b"next-app" in urlopen("http://127.0.0.1:8089/next.html",timeout=15).read()
token=call("/auth/login",{"username":"admin","password":"123456"})["token"]
rows=call("/business/employees",token=token)
assert rows==[],rows
if sys.argv[1]=="first":
    call("/business/employees",{"fullName":"CI Preview","employeeType":"operator"},token)
    assert len(call("/business/employees",token=token))==1
print("Native preview smoke passed:",sys.argv[1])
