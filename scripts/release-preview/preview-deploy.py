#!/usr/bin/env python3
"""Disposable ERP Next preview: persistent private PostgreSQL cluster, recreated erp_next database."""
import http.client,json,os,pwd,secrets,shutil,signal,socket,subprocess,sys,time
from pathlib import Path
from urllib.request import Request,build_opener,ProxyHandler
from urllib.error import URLError

ROOT=Path(__file__).resolve().parent
STATE=Path.home()/".local/share/zjerp-native-preview"
PG=Path("/usr/lib/postgresql/17/bin")
UID=os.getuid()
action=sys.argv[1] if len(sys.argv)>1 else "start"
opener=build_opener(ProxyHandler({}))
os.umask(0o077)
if UID==0: sys.exit("请以普通用户运行，不要 sudo bash deploy.sh")

def terminate_deploy(signum,frame):
    # Do not orphan a foreground psql/pg_ctl command when a second deployment
    # replaces this controller. The inherited lock remains held until it exits.
    raise SystemExit("部署任务被新的部署请求终止")

signal.signal(signal.SIGTERM,terminate_deploy)

def run(args,**kw): return subprocess.run([str(x) for x in args],check=True,**kw)
def child(name):
    p=STATE/name
    if p.is_symlink() or (p.exists() and (p.stat().st_uid!=UID or os.path.ismount(p))):
        raise RuntimeError("拒绝非专用路径 "+str(p))
    return p
if STATE.is_symlink() or (STATE.exists() and STATE.resolve()!=STATE.absolute()): sys.exit("拒绝符号链接状态目录")
STATE.mkdir(parents=True,exist_ok=True)
if STATE.stat().st_uid!=UID or os.path.ismount(STATE): sys.exit("状态目录归属无效")
marker=child("owner")
if not marker.exists():
    if action not in ("start","fresh") or any(p.name!="lock" for p in STATE.iterdir()):
        sys.exit("非本工具建立的测试目录，拒绝操作")
    marker.write_text("zjerp-native-preview-v1\n")
if marker.read_text()!="zjerp-native-preview-v1\n": sys.exit("测试目录标记不匹配")

data=child("pgdata"); files=child("files"); app=child("app"); sock=child("socket")
ports_file=child("ports.json")
if ports_file.exists(): ports=json.loads(ports_file.read_text())
else:
    ports={k:int(os.environ.get(e,v)) for k,e,v in [("web","ERP_PREVIEW_WEB_PORT","8089"),("api","ERP_PREVIEW_API_PORT","8090"),("db","ERP_PREVIEW_DB_PORT","55439")]}
    if len(set(ports.values()))!=3 or not all(1024<=p<=65535 for p in ports.values()): sys.exit("端口必须不同且在1024–65535")
    ports_file.write_text(json.dumps(ports))
web,api,db=[ports[k] for k in ("web","api","db")]
JAVA=os.environ.get("ERP_PREVIEW_JAVA") or shutil.which("java")

def pg(name,*args,**kw):
    env={k:v for k,v in os.environ.items() if not k.startswith("PG")}
    return run([PG/name,*args],env=env,**kw)

def info(pid):
    p=Path(f"/proc/{pid}")
    stat=(p/"stat").read_text().rsplit(")",1)[1].split()
    if stat[0]=="Z": return None
    return {"pid":pid,"start":stat[19],"uid":p.stat().st_uid,"exe":os.readlink(p/"exe"),"cmd":(p/"cmdline").read_bytes()}

def save_record(name,x):
    tmp=child(name+".process.json.tmp")
    tmp.write_text(json.dumps({"pid":x["pid"],"start":x["start"],"exe":x["exe"]}))
    os.replace(tmp,child(name+".process.json"))

def api_process():
    f=child("api.process.json")
    if not f.exists(): return None
    try:
        r=json.loads(f.read_text()); x=info(int(r["pid"]))
        if x is None or x["start"]!=str(r["start"]): return None
        if x["uid"]!=UID: raise RuntimeError("API 进程归属不符，拒绝操作")
        jar=os.fsencode(str(app/"app.jar"))
        if not Path(x["exe"]).name.startswith("java") or b"-jar" not in x["cmd"] or jar not in x["cmd"]:
            raise RuntimeError("API 进程身份不符，拒绝操作")
        if r.get("exe") and r["exe"]!=x["exe"]: raise RuntimeError("API 可执行文件不一致")
        return x["pid"]
    except FileNotFoundError: return None
    except (KeyError,ValueError,IndexError,json.JSONDecodeError) as e: raise RuntimeError("API 进程记录损坏") from e

def nginx_process(adopt=False):
    f=child("nginx.pid")
    if not f.is_file(): return None
    try: pid=int(f.read_text().strip()); x=info(pid)
    except (ValueError,FileNotFoundError,PermissionError,OSError): return None
    if x is None: return None
    if x["uid"]!=UID or Path(x["exe"]).name!="nginx": return None
    rfile=child("web.process.json")
    if rfile.exists():
        try:
            r=json.loads(rfile.read_text())
            if int(r["pid"])!=pid or str(r["start"])!=x["start"]:
                raise RuntimeError("Nginx PID 文件与进程记录不一致")
        except (KeyError,ValueError,json.JSONDecodeError) as e: raise RuntimeError("Nginx 进程记录损坏") from e
    elif adopt:
        # If a crash happened after Nginx wrote its PID but before our record,
        # adopt only when its rewritten master command line still identifies
        # this preview's private config and prefix.
        if os.fsencode(str(child("nginx.conf"))) not in x["cmd"] or os.fsencode(str(STATE)+"/") not in x["cmd"]:
            return None
        save_record("web",x)
    return pid

def wait_stop(name,getter,timeout=10):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if getter() is None:
            child(name+".process.json").unlink(missing_ok=True); return
        time.sleep(.1)
    pid=getter()
    if pid:
        os.kill(pid,signal.SIGKILL)
        time.sleep(.2)
        if getter() is not None: raise RuntimeError(name+" 无法停止")
    child(name+".process.json").unlink(missing_ok=True)

def stop_api():
    pid=api_process()
    if pid: os.kill(pid,signal.SIGTERM); wait_stop("api",api_process)
    else: child("api.process.json").unlink(missing_ok=True)

def stop_nginx():
    pid=nginx_process(adopt=True)
    if pid:
        subprocess.run(["nginx","-s","quit","-c",child("nginx.conf"),"-p",str(STATE)+"/"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        wait_stop("web",lambda:nginx_process(adopt=True))
    else:
        child("web.process.json").unlink(missing_ok=True)
        child("nginx.pid").unlink(missing_ok=True)

def pg_running():
    return (data/"PG_VERSION").is_file() and subprocess.run([str(PG/"pg_ctl"),"-D",str(data),"status"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0

def postmaster_state():
    pf=data/"postmaster.pid"
    if not pf.exists(): return None,"absent"
    try: pid=int(pf.read_text().splitlines()[0]); os.kill(pid,0)
    except ProcessLookupError: return pid if "pid" in locals() else None,"stale"
    except (ValueError,PermissionError,IndexError,OSError): return None,"unknown"
    try:
        p=Path(f"/proc/{pid}")
        if p.stat().st_uid!=UID: return pid,"unknown"
        exe=os.readlink(p/"exe"); cwd=os.readlink(p/"cwd"); comm=(p/"comm").read_text().strip()
    except FileNotFoundError: return pid,"stale"
    except OSError: return pid,"unknown"
    if Path(cwd)==data.resolve() and (Path(exe).name.startswith("postgres") or comm.startswith("postgres")): return pid,"ours"
    return pid,"unknown"

def ensure_postgres():
    data.mkdir(mode=0o700,parents=True,exist_ok=True); sock.mkdir(mode=0o700,parents=True,exist_ok=True)
    if not (data/"PG_VERSION").is_file():
        if any(data.iterdir()): raise RuntimeError("PostgreSQL 数据目录不完整，拒绝自动删除")
        pg("initdb","-D",data,"-U",pwd.getpwuid(UID).pw_name,"--encoding=UTF8","--locale=C.UTF-8","--auth-local=peer","--auth-host=scram-sha-256")
    if not pg_running():
        pid,state=postmaster_state()
        if state=="stale": (data/"postmaster.pid").unlink(missing_ok=True)
        elif state=="unknown": raise RuntimeError("无法确认 PostgreSQL PID 文件归属，拒绝启动")
        conf=data/"postgresql.conf"
        if "# ZJERP PREVIEW SETTINGS" not in conf.read_text():
            sp=str(sock).replace("\\","\\\\").replace("'","\\'")
            with conf.open("a") as out: out.write(f"\n# ZJERP PREVIEW SETTINGS\nlisten_addresses = '127.0.0.1'\nport = {db}\nunix_socket_directories = '{sp}'\n")
        pg("pg_ctl","-D",data,"-l",child("postgres.log"),"-w","start")
    pg("pg_isready","-h",sock,"-p",db,"-d","postgres")

def psql(database,*args,**kw):
    return pg("psql","-h",sock,"-p",db,"-U",pwd.getpwuid(UID).pw_name,"-d",database,"-v","ON_ERROR_STOP=1",*args,**kw)

def recreate_db():
    password=secrets.token_urlsafe(36)
    sql=("SELECT format('CREATE ROLE erp_next LOGIN PASSWORD %L', :'app_password') "
         "WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='erp_next') \\gexec\n"
         "ALTER ROLE erp_next LOGIN PASSWORD :'app_password';\n"
         "DROP DATABASE IF EXISTS erp_next WITH (FORCE);\n"
         "CREATE DATABASE erp_next OWNER erp_next;\n")
    psql("postgres","-v","app_password="+password,input=sql,text=True)
    # Apply objects under the application role so it owns its tables and indexes.
    schema=child("app/db/schema-as-app.sql")
    schema.write_text("SET ROLE erp_next;\n"+(app/"db"/"schema.sql").read_text())
    try:
        psql("erp_next","-f",schema)
    finally:
        schema.unlink(missing_ok=True)
    return password

def available(port):
    with socket.socket() as s:
        try: s.bind(("127.0.0.1",port)); return True
        except OSError: return False

def clear_dir(path):
    if path.is_symlink() or (path.exists() and (path.stat().st_uid!=UID or os.path.ismount(path))): raise RuntimeError("拒绝清理非专用目录 "+str(path))
    if path.exists():
        dev=path.stat().st_dev
        for root,dirs,names in os.walk(path,followlinks=False):
            for n in dirs+names:
                p=Path(root)/n
                if not p.is_symlink() and (os.path.ismount(p) or p.stat().st_dev!=dev): raise RuntimeError("拒绝清理挂载目录 "+str(p))
        shutil.rmtree(path)
    path.mkdir(mode=0o700,parents=True)

def copy_release():
    for p in (ROOT/"app.jar",ROOT/"web"/"next.html",ROOT/"db"/"schema.sql"):
        if not p.is_file(): raise RuntimeError("缺少 Release 产物 "+str(p))
    app.mkdir(mode=0o700,parents=True,exist_ok=True)
    tmp=child("app/app.jar.new"); shutil.copy2(ROOT/"app.jar",tmp); os.replace(tmp,child("app/app.jar"))
    webroot=child("app/web"); clear_dir(webroot); shutil.copytree(ROOT/"web",webroot,dirs_exist_ok=True)
    dbdir=child("app/db"); clear_dir(dbdir); shutil.copytree(ROOT/"db",dbdir,dirs_exist_ok=True)

def nginx_config():
    conf=child("nginx.conf")
    conf.write_text(f"""pid "{STATE}/nginx.pid";
error_log "{STATE}/nginx-error.log";
worker_processes 1;
events {{ worker_connections 512; }}
http {{
 include /etc/nginx/mime.types;
 default_type application/octet-stream;
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
  location /next-api/ {{ client_max_body_size 250m; proxy_read_timeout 180s; proxy_pass http://127.0.0.1:{api}/api/; proxy_http_version 1.1; proxy_set_header Host $http_host; }}
  location / {{ try_files $uri $uri/ /next.html; }}
 }}
}}
""")
    err=child("nginx-error.log")
    probe=subprocess.run(["nginx","-e",str(err),"-v"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    return ["-e",str(err)] if probe.returncode==0 else []

def reload_or_start_nginx():
    args=nginx_config()
    run(["nginx",*args,"-t","-c",child("nginx.conf"),"-p",str(STATE)+"/"])
    pid=nginx_process(adopt=True)
    if pid:
        run(["nginx",*args,"-s","reload","-c",child("nginx.conf"),"-p",str(STATE)+"/"])
    else:
        child("nginx.pid").unlink(missing_ok=True)
        run(["nginx",*args,"-c",child("nginx.conf"),"-p",str(STATE)+"/"])
        end=time.monotonic()+10
        while time.monotonic()<end and nginx_process(adopt=True) is None: time.sleep(.1)
    if nginx_process(adopt=True) is None: raise RuntimeError("Nginx 启动或 reload 失败；查看 nginx-error.log")

def start_api(password,token):
    env={"ERP_NEXT_PORT":str(api),"ERP_NEXT_DB_URL":f"jdbc:postgresql://127.0.0.1:{db}/erp_next?stringtype=unspecified",
        "ERP_NEXT_DB_USER":"erp_next","ERP_NEXT_DB_PASSWORD":password,"ERP_BOOTSTRAP_TOKEN":token,
        "ERP_LOCAL_TEST_BOOTSTRAP":"true","ERP_LEGACY_SCAN_ENABLED":"false","ERP_FILES_ROOT":str(files),"TZ":"Asia/Shanghai"}
    base={k:v for k,v in os.environ.items() if k in ("PATH","HOME","LANG","TZ","TMPDIR","USER","LOGNAME") or k.startswith("LC_")}
    base.update(env)
    with child("api.log").open("wb") as log:
        p=subprocess.Popen(["zjerp-preview-api","-XX:MaxRAMPercentage=50.0","-jar",str(app/"app.jar"),"--server.address=127.0.0.1"],
            executable=JAVA,env=base,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True,close_fds=True)
    end=time.monotonic()+5
    while time.monotonic()<end:
        try:
            x=info(p.pid)
            if x: save_record("api",x); return
        except FileNotFoundError: pass
        if p.poll() is not None: break
        time.sleep(.05)
    raise RuntimeError("API 进程启动失败；查看 api.log")

def request(url,body=None,headers=None):
    req=Request(url,data=None if body is None else json.dumps(body).encode(),headers={"Content-Type":"application/json",**(headers or {})})
    with opener.open(req,timeout=8) as r: return r.status,json.load(r)

def wait_api():
    for _ in range(120):
        if api_process() is None: raise RuntimeError("API 启动失败；查看 api.log")
        try:
            if request(f"http://127.0.0.1:{api}/actuator/health")[1].get("status")=="UP": return
        except (URLError,TimeoutError,http.client.HTTPException,ConnectionError,OSError): pass
        time.sleep(1)
    raise RuntimeError("API 健康检查超时；查看 api.log")

def check_ports():
    if not pg_running() and not available(db): raise RuntimeError(f"数据库端口 {db} 被其他服务占用，拒绝删除数据库")
    if nginx_process(adopt=True) is None and not available(web): raise RuntimeError(f"Web 端口 {web} 被其他服务占用")
    stop_api()
    if not available(api): raise RuntimeError(f"API 端口 {api} 被其他服务占用")

if action=="status":
    print("API:",api_process(),"Web:",nginx_process(adopt=True),"PostgreSQL:",pg_running())
    print(f"http://localhost:{web}/next.html"); sys.exit(0)
if action=="logs":
    for name in ("postgres.log","api.log","nginx-error.log","nginx-access.log"):
        p=child(name)
        if p.is_file(): print(f"\n=== {name} ===",flush=True); run(["tail","-n","100",p])
    sys.exit(0)
if action=="stop":
    stop_nginx(); stop_api()
    if pg_running(): pg("pg_ctl","-D",data,"-m","fast","-w","stop")
    print("已停止预览服务；PostgreSQL 集群文件保留，下次部署只重建 erp_next。"); sys.exit(0)
if action not in ("start","fresh"): sys.exit("未知操作："+action)

try:
    for p in (ROOT/"app.jar",ROOT/"web"/"next.html",ROOT/"db"/"schema.sql"):
        if not p.is_file(): raise RuntimeError("缺少 Release 产物 "+str(p))
    if any(c in str(STATE) for c in "'\"\\\n\r$;") or len(str(sock).encode())>85: raise RuntimeError("状态路径不支持")
    print("保留 PostgreSQL 集群；仅删除并重建同名 erp_next 数据库。",flush=True)
    ensure_postgres()
    check_ports()
    copy_release()
    password=recreate_db()
    clear_dir(files)
    token=secrets.token_urlsafe(36)
    start_api(password,token); wait_api()
    reload_or_start_nginx()
    url=f"http://127.0.0.1:{web}/next-api"
    for _ in range(30):
        if nginx_process(adopt=True) is None: raise RuntimeError("Nginx 主进程退出")
        try:
            if request(url+"/bootstrap/status")[1].get("initialized") is False: break
        except (URLError,TimeoutError,http.client.HTTPException,ConnectionError,OSError): pass
        time.sleep(1)
    else: raise RuntimeError("预览初始化状态不正确")
    status,_=request(url+"/bootstrap/admin",{"username":"admin","displayName":"管理员","password":"123456"},{"X-Bootstrap-Token":token})
    if status!=201: raise RuntimeError("管理员初始化失败")
    status,login=request(url+"/auth/login",{"username":"admin","password":"123456"})
    if status!=200 or not login.get("token"): raise RuntimeError("登录验证失败")
    print(f"\n部署成功：http://localhost:{web}/next.html\nadmin / 123456（仅本机测试）",flush=True)
except BaseException:
    print("部署失败，诊断日志位于 "+str(STATE),file=sys.stderr)
    try: stop_api()
    except BaseException as e: print("停止失败 API 时出现附加错误："+str(e),file=sys.stderr)
    raise
