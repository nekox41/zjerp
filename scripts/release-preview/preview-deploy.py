#!/usr/bin/env python3
"""Disposable native preview only. No external PostgreSQL or system nginx configuration."""
import fcntl, http.client, json, os, pwd, secrets, shutil, signal, socket, subprocess, sys, time
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import URLError

ROOT=Path(__file__).resolve().parent
STATE=Path.home()/".local/share/zjerp-native-preview"
MARKER="zjerp-native-preview-v1\n"
PG=Path("/usr/lib/postgresql/17/bin")
action=sys.argv[1]
opener=build_opener(ProxyHandler({}))
os.umask(0o077)
if os.getuid()==0: sys.exit("请以普通用户运行")
def run(args, **kw):
    return subprocess.run([str(x) for x in args], check=True, **kw)
def child(name):
    p=STATE/name
    if p.is_symlink() or (p.exists() and (p.stat().st_uid!=os.getuid() or os.path.ismount(p))):
        raise RuntimeError("拒绝非专用路径 "+str(p))
    return p
if STATE.is_symlink() or (STATE.exists() and STATE.resolve()!=STATE.absolute()):
    sys.exit("拒绝符号链接状态目录")
STATE.mkdir(parents=True,exist_ok=True)
if STATE.stat().st_uid!=os.getuid() or os.path.ismount(STATE): sys.exit("状态目录归属无效")
lockpath=child("lock")
lock=lockpath.open("a")
fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
marker=child("owner")
if not marker.exists():
    if action not in ("start","fresh") or any(p.name!="lock" for p in STATE.iterdir()): sys.exit("非本工具建立的测试目录，拒绝操作")
    marker.write_text(MARKER)
if marker.read_text()!=MARKER: sys.exit("测试目录标记不匹配")
data=child("pgdata"); files=child("files"); app=child("app"); sock=child("socket")
config=child("ports.json")
if config.exists():
    ports=json.loads(config.read_text())
else:
    ports={k:int(os.environ.get(e,v)) for k,e,v in [("web","ERP_PREVIEW_WEB_PORT","8089"),("api","ERP_PREVIEW_API_PORT","8090"),("db","ERP_PREVIEW_DB_PORT","55439")]}
    if len(set(ports.values()))!=3 or not all(1024<=p<=65535 for p in ports.values()): sys.exit("端口必须不同且在1024–65535")
    config.write_text(json.dumps(ports))
web,api,db=[ports[x] for x in ("web","api","db")]
def pg(name,*args,**kwargs):
    env={k:v for k,v in os.environ.items() if not k.startswith("PG")}
    return run([PG/name,*args],env=env,**kwargs)
def process(name):
    """Only signal a process started by this deployment, including PID reuse check."""
    p=child(name+".process.json")
    if not p.exists(): return None
    r=json.loads(p.read_text()); pid=int(r["pid"])
    try:
        stat=Path(f"/proc/{pid}/stat").read_text().rsplit(")",1)[1].split()
        if stat[0]=="Z" or stat[19]!=r["start"]: return None
        if Path(f"/proc/{pid}").stat().st_uid!=os.getuid(): raise RuntimeError("进程归属不符")
        # Nginx rewrites argv[0]/cmdline with its process title. Use the
        # per-process random environment token instead; it survives title changes.
        environ=Path(f"/proc/{pid}/environ").read_bytes().split(bytes([0]))
        expected=("ZJERP_PREVIEW_PROCESS="+str(r["token"])).encode()
        if expected not in environ: raise RuntimeError("进程标记不符")
        return pid
    except FileNotFoundError: return None
def stop_proc(name):
    pid=process(name)
    if pid:
        os.kill(pid,signal.SIGTERM)
        for _ in range(100):
            if process(name) is None: break
            time.sleep(.1)
        else: raise RuntimeError(name+" 未停止，拒绝清理")
    child(name+".process.json").unlink(missing_ok=True)
def start_proc(name,args,env=None):
    token=secrets.token_hex(24)
    base={k:v for k,v in os.environ.items() if k in ("PATH","HOME","LANG","TZ","TMPDIR","USER","LOGNAME") or k.startswith("LC_")}
    base.update(env or {});base["ZJERP_PREVIEW_PROCESS"]=token
    with child(name+".log").open("wb") as log:
        p=subprocess.Popen([str(x) for x in args],env=base,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True,close_fds=True)
    stat=Path(f"/proc/{p.pid}/stat").read_text().rsplit(")",1)[1].split()
    child(name+".process.json").write_text(json.dumps({"pid":p.pid,"start":stat[19],"token":token}))
def pg_running():
    if not (data/"PG_VERSION").is_file(): return False
    return subprocess.run([str(PG/"pg_ctl"),"-D",str(data),"status"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
def postmaster_pid_state():
    """Return (pid, state): state is absent, stale, ours, or unknown."""
    pidfile=data/"postmaster.pid"
    if not pidfile.exists(): return None,"absent"
    try:
        raw=pidfile.read_text().splitlines()
        pid=int(raw[0].strip())
        os.kill(pid,0)
    except ProcessLookupError:
        return pid if "pid" in locals() else None,"stale"
    except (ValueError,PermissionError,IndexError,OSError):
        # A malformed PID file is not safe to remove automatically.
        return None,"unknown"
    proc=Path(f"/proc/{pid}")
    try:
        if proc.stat().st_uid!=os.getuid(): return pid,"unknown"
        exe=os.readlink(proc/"exe")
        comm=(proc/"comm").read_text().strip()
        cwd=os.readlink(proc/"cwd")
    except FileNotFoundError:
        return pid,"stale"
    except OSError:
        return pid,"unknown"
    # PostgreSQL changes its process title, so use executable and working
    # directory rather than cmdline. The dedicated cluster cwd must match.
    if Path(exe).name.startswith("postgres") and Path(cwd)==data.resolve():
        return pid,"ours"
    if comm.startswith("postgres") and Path(cwd)==data.resolve():
        return pid,"ours"
    return pid,"stale"
def stop_all():
    stop_proc("web");stop_proc("api")
    pid,state=postmaster_pid_state()
    if state=="ours":
        pg("pg_ctl","-D",data,"-m","fast","-w","stop")
    elif state=="stale":
        # The recorded PID is gone or belongs to another process; never signal it.
        (data/"postmaster.pid").unlink()
    elif state=="unknown":
        raise RuntimeError("无法确认 PostgreSQL PID 文件归属，拒绝清理数据库")
    if (data/"postmaster.pid").exists():
        raise RuntimeError("PostgreSQL PID 文件仍存在，拒绝删除数据库")
if action=="status":
    print("API:",process("api"),"Web:",process("web"),"PostgreSQL:",pg_running())
    print(f"http://localhost:{web}/next.html");sys.exit()
if action=="logs":
    for name in ("postgres","api","web"):
        p=child(name+".log")
        if p.is_file(): print(f"\n=== {name} ===",flush=True);run(["tail","-n","80",p])
    sys.exit()
if action=="stop":
    stop_all();print("已停止独立预览服务");sys.exit()
for p in (ROOT/"app.jar",ROOT/"web/next.html",ROOT/"db/schema.sql"):
    if not p.is_file(): sys.exit("缺少 Release 产物 "+str(p))
if any(c in str(STATE) for c in "'\"\\\n\r$;") or len(str(sock).encode())>85: sys.exit("状态路径不支持")
# Stop only our own processes. A previous Docker stack or another service is never killed.
stop_all()
for port in (web,api,db):
    with socket.socket() as s:
        s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        try: s.bind(("127.0.0.1",port))
        except OSError: sys.exit(f"端口 {port} 已占用；请先停止旧测试 Docker 或其他占用程序。未清空数据库。")
# All paths are dedicated and checked; do not traverse nested mounts.
def clear(path):
    if path.exists():
        if not path.is_dir(): raise RuntimeError("非目录 "+str(path))
        root_dev=path.stat().st_dev
        for dirpath,dirs,names in os.walk(path,followlinks=False):
            for n in dirs+names:
                p=Path(dirpath)/n
                if not p.is_symlink() and (os.path.ismount(p) or p.stat().st_dev!=root_dev): raise RuntimeError("拒绝清除挂载目录 "+str(p))
        shutil.rmtree(path)
    path.mkdir(mode=0o700)
print("清空独立预览数据库及附件；不会操作系统 PostgreSQL 或旧生产库",flush=True)
for p in (data,files,app,sock): clear(p)
shutil.copy2(ROOT/"app.jar",app/"app.jar")
shutil.copytree(ROOT/"web",app/"web")
shutil.copytree(ROOT/"db",app/"db")
user=pwd.getpwuid(os.getuid()).pw_name
if user=="erp_next": sys.exit("请使用非 erp_next 的普通 Linux 账号运行")
password=secrets.token_hex(32);token=secrets.token_hex(32)
pg("initdb","-D",data,"-U",user,"--encoding=UTF8","--locale=C.UTF-8","--auth-local=peer","--auth-host=scram-sha-256")
with (data/"postgresql.conf").open("a") as f:
    f.write(f"\nlisten_addresses = '127.0.0.1'\nport = {db}\nunix_socket_directories = '{sock}'\n")
try:
    pg("pg_ctl","-D",data,"-l",child("postgres.log"),"-w","start")
    pg("psql","-h",sock,"-p",db,"-U",user,"-d","postgres","-v","ON_ERROR_STOP=1","-v","app_password="+password,input="CREATE ROLE erp_next LOGIN PASSWORD :'app_password';\nCREATE DATABASE erp_next OWNER erp_next;\n",text=True)
    env={"ERP_NEXT_PORT":str(api),"ERP_NEXT_DB_URL":f"jdbc:postgresql://127.0.0.1:{db}/erp_next?stringtype=unspecified","ERP_NEXT_DB_USER":"erp_next","ERP_NEXT_DB_PASSWORD":password,"ERP_BOOTSTRAP_TOKEN":token,"ERP_LOCAL_TEST_BOOTSTRAP":"true","ERP_LEGACY_SCAN_ENABLED":"false","ERP_FILES_ROOT":str(files),"TZ":"Asia/Shanghai"}
    # A fresh preview database is initialized directly from the single canonical schema.
    pg("psql","-h",sock,"-p",db,"-U",user,"-d","erp_next","-v","ON_ERROR_STOP=1","-f",app/"db/schema.sql")
    start_proc("api",[os.environ["ERP_PREVIEW_JAVA"],"-XX:MaxRAMPercentage=50.0","-jar",app/"app.jar","--server.address=127.0.0.1"],env)
    def request(url,body=None,headers=None):
        req=Request(url,data=None if body is None else json.dumps(body).encode(),headers={"Content-Type":"application/json",**(headers or {})})
        with opener.open(req,timeout=5) as r: return r.status,json.load(r)
    for _ in range(120):
        if process("api") is None: raise RuntimeError("API 启动失败，查看 api.log")
        try:
            if request(f"http://127.0.0.1:{api}/actuator/health")[1].get("status")=="UP": break
        except (URLError,TimeoutError,http.client.HTTPException,ConnectionError,OSError):
            # During Spring Boot startup the TCP listener may accept and close
            # a connection before the health endpoint is ready. Keep polling;
            # the process check above still detects an actual JVM exit.
            pass
        time.sleep(1)
    else: raise RuntimeError("API 健康检查超时；请查看 api.log")
    nginx=child("nginx.conf")
    nginx.write_text(f"""pid "{STATE}/nginx.pid";
error_log "{STATE}/nginx-error.log";
worker_processes 1;
events {{ worker_connections 256; }}
http {{
 include /etc/nginx/mime.types;
 access_log "{STATE}/nginx-access.log";
 client_body_temp_path "{STATE}/nginx-body";
 proxy_temp_path "{STATE}/nginx-proxy";
 fastcgi_temp_path "{STATE}/nginx-fastcgi";
 uwsgi_temp_path "{STATE}/nginx-uwsgi";
 scgi_temp_path "{STATE}/nginx-scgi";
 server {{
  listen 127.0.0.1:{web};
  root "{app}/web";
  index next.html;
  location = / {{ return 302 /next.html; }}
  location = /next.html {{ add_header Cache-Control "no-store"; try_files $uri =404; }}
  location /next-api/ {{
   client_max_body_size 250m;
   proxy_read_timeout 180s;
   proxy_pass http://127.0.0.1:{api}/api/;
   proxy_http_version 1.1;
   proxy_set_header Host $http_host;
  }}
  location / {{ try_files $uri $uri/ /next.html; }}
 }}
}}
""")
    run(["nginx","-t","-c",nginx,"-p",str(STATE)+"/"])
    start_proc("web",["nginx","-c",nginx,"-p",str(STATE)+"/","-g","daemon off;"])
    url=f"http://127.0.0.1:{web}/next-api"
    for _ in range(30):
        if process("web") is None: raise RuntimeError("Nginx 启动失败")
        try:
            if request(url+"/bootstrap/status")[1].get("initialized") is False: break
        except (URLError,TimeoutError): pass
        time.sleep(1)
    else: raise RuntimeError("预览初始化状态不正确")
    status,_=request(url+"/bootstrap/admin",{"username":"admin","displayName":"管理员","password":"123456"},{"X-Bootstrap-Token":token})
    if status!=201: raise RuntimeError("管理员初始化失败")
    status,login=request(url+"/auth/login",{"username":"admin","password":"123456"})
    if status!=200 or not login.get("token"): raise RuntimeError("登录验证失败")
    print(f"\n部署成功：http://localhost:{web}/next.html\nadmin / 123456（仅本机测试）")
except BaseException:
    print("部署失败，诊断日志位于 "+str(STATE),file=sys.stderr)
    try:
        stop_all()
    except BaseException as cleanup_error:
        # Preserve the original startup failure; cleanup problems are secondary.
        print("清理预览服务时出现附加错误："+str(cleanup_error),file=sys.stderr)
    raise
