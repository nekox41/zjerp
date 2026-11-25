#!/usr/bin/env python3
"""Explicit, transactional ERP V1 trial migration. Never connects to the legacy database."""
import argparse
import datetime as dt
import decimal
import getpass
import hashlib
import importlib.util
import json
import mimetypes
import os
from pathlib import Path
import re
import shutil
import sys
import uuid

spec = importlib.util.spec_from_file_location("legacy_archive", Path(__file__).with_name("erp-next-legacy-export.py"))
archive_lib = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive_lib)
canonical, digest, safe_path = archive_lib.canonical, archive_lib.digest, archive_lib.safe_path
TYPES = {"WB":"maintenance", "JC":"fire_inspection", "PD":"fire_assessment", "PG":"fire_evaluation", "QT":"other"}
CONTRACTS = {"zj_contract_maintenance":"WB","zj_contract_inspection":"JC","zj_contract_rating":"PD","zj_contract_assessment":"PG","zj_contract_other":"QT"}
SECTIONS = ("customers","accounts","employees","qualifications","partners","contracts","projects","contractLinks","customerContacts","contacts","files","rawFiles","contractFiles","materials","materialArchives","cards","maintenanceStates","projectIssues","projectDocuments")
TARGETS = {"erp_customer","erp_identity","erp_employee","erp_employee_qualification","erp_partner","erp_contract","erp_project","erp_maintenance_contact","erp_file_object","erp_contract_file","erp_maintenance_material","erp_maintenance_card","erp_customer_contact","erp_maintenance_material_archive","erp_project_issue","erp_legacy_project_document"}
SOURCE_TABLES = {"sys_user","zj_employee","zj_report_credential","zj_intermediary","zj_work_project","zjerp_wbcard_phone","sys_file","zj_wbcard_material","zj_wbcard","zj_work_project_issue","zj_report","zj_inspection_report","zj_project_document",*CONTRACTS}

def sha(obj):
    return hashlib.sha256(canonical(obj).encode("utf-8")).hexdigest()

def value(obj):
    if isinstance(obj,dict) and obj.get("$type") in ("decimal","date","datetime","time"):
        return obj["value"]
    return obj

def required(obj,key,limit):
    s = value(obj.get(key))
    if s is None or not isinstance(s,(str,int)) or not str(s).strip() or len(str(s))>limit:
        raise ValueError("Missing or invalid " + key)
    return str(s).strip()

def optional(obj,key,limit):
    v=value(obj.get(key))
    if v is None or v=="":
        return None
    if not isinstance(v,(str,int)) or len(str(v))>limit:
        raise ValueError("Invalid or overlong " + key)
    return str(v)

def money(v):
    if v is None or isinstance(v,bool):
        raise ValueError("Amount requires explicit confirmation; NULL is not zero")
    v=decimal.Decimal(str(value(v)))
    if not v.is_finite() or v<0 or v>=decimal.Decimal("10000000000000000") or v!=v.quantize(decimal.Decimal(".01")):
        raise ValueError("Amount cannot be represented exactly as DECIMAL(18,2); review amount")
    return v

def percent(v):
    x=money(v)
    if x>100:
        raise ValueError("Share percent outside 0–100")
    return x

def date(obj,key):
    raw=value(obj.get(key))
    if raw is None or raw=="":
        return None
    return dt.date.fromisoformat(str(raw)).isoformat()

def dates(obj,first,last):
    a,b=date(obj,first),date(obj,last)
    if a and b and a>b:
        raise ValueError("Start date is after end date")
    return a,b

def boolean(v):
    if type(v) is not bool:
        raise ValueError("Explicit boolean required")
    return v

def ident(v):
    if isinstance(v,bool) or not re.fullmatch(r"[1-9][0-9]{0,18}",str(v)):
        raise ValueError("Invalid legacy ID")
    return str(v)

def key(v):
    if not isinstance(v,str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,120}",v):
        raise ValueError("Invalid manual mapping key")
    return v

def enum(v,choices):
    if v not in choices:
        raise ValueError("Unsupported or unconfirmed classification: " + str(v))
    return v

def source_index(root,manifest):
    data={}
    for t in manifest["tables"]:
        table=t["table"]
        if t["kind"]!="BASE TABLE" or table not in SOURCE_TABLES:
            continue
        id_col="user_id" if table=="sys_user" else "file_id" if table=="sys_file" else "id"
        rows={}
        with safe_path(root,t["data"]).open("r",encoding="utf-8") as f:
            for line in f:
                row=json.loads(line)
                row_id=ident(row.get(id_col))
                if row_id in rows:
                    raise ValueError("Duplicate source ID: "+table)
                rows[row_id]=row
        data[table]=rows
    return data

def read_plan(path,archive_hash):
    def unique_pairs(pairs):
        result={}
        for k,v in pairs:
            if k in result:raise ValueError("Duplicate JSON key in reviewed plan: "+k)
            result[k]=v
        return result
    p=json.loads(Path(path).read_text(encoding="utf-8"),object_pairs_hook=unique_pairs)
    if p.get("format")!="erp-legacy-review-v1" or p.get("archiveSha256")!=archive_hash:
        raise ValueError("Review plan does not refer to this verified archive")
    if set(p)-{"format","archiveSha256","note",*SECTIONS}:
        raise ValueError("Unknown review-plan section")
    for section in SECTIONS:
        if not isinstance(p.get(section,[]),list) or any(not isinstance(x,dict) for x in p.get(section,[])):
            raise ValueError("Invalid section "+section)
    return p

class Migration:
    def __init__(self,connection,root,manifest,archive_hash,plan,storage,apply):
        self.conn=connection
        self.cur=connection.cursor(dictionary=True)
        self.root=root
        self.archive=archive_hash
        self.ns=manifest["sourceNamespace"]
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}",self.ns):
            raise ValueError("Invalid archive namespace")
        self.source=source_index(root,manifest)
        self.manifest=manifest
        self.new=set()
        self.new_file_ids=set()
        self.file_manifest={f["path"]:f for f in manifest["files"]}
        self.plan=plan
        self.storage=storage
        self.apply=apply
        self.created_files=[]
        self.mapped={}
        self.inserted={}
        self.skipped={}
        self.touched=set()
        self.summary={"archiveSha256":archive_hash,"sourceNamespace":self.ns,"decisionSha256":sha(plan)}
    def execute(self,sql,args=()):
        self.cur.execute(sql,args)
        return self.cur.lastrowid
    def one(self,sql,args=()):
        self.cur.execute(sql,args)
        rows=self.cur.fetchall()
        if len(rows)!=1:
            raise ValueError("Expected one target row; review mapping")
        return rows[0]
    def legacy(self,table,id):
        id=ident(id)
        if id not in self.source.get(table,{}):
            raise ValueError("Missing archived source: "+table+"/"+id)
        return self.source[table][id]
    def verify_target_file(self,file_id):
        saved=self.target("erp_file_object",file_id)
        storage_key=str(uuid.UUID(saved["storage_key"]))
        if storage_key!=saved["storage_key"]:
            raise ValueError("Invalid private storage key")
        path=safe_path(self.storage,storage_key)
        if not path.is_file() or path.stat().st_size!=saved["byte_size"] or digest(path)!=saved["sha256"]:
            raise ValueError("Target attachment missing or differs from metadata: "+str(file_id))
        return saved
    def target(self,table,id):
        if table not in TARGETS:
            raise ValueError("Unknown target table")
        return self.one("SELECT * FROM "+table+" WHERE id=%s",(id,))
    def ref(self,table,id,expected):
        id=ident(id) if table in SOURCE_TABLES else key(id)
        pair=(table,id)
        if pair not in self.mapped:
            self.cur.execute("SELECT target_table,target_id,source_sha256 FROM erp_legacy_migration_map WHERE source_namespace=%s AND source_table=%s AND source_key=%s",(self.ns,table,id))
            found=self.cur.fetchall()
            if len(found)!=1:
                raise ValueError("Required source reference has not been migrated: "+table+"/"+id)
            if table in SOURCE_TABLES and found[0]["source_sha256"]!=sha(self.legacy(table,id)):
                raise ValueError("Referenced source changed since earlier import: "+table+"/"+id)
            self.mapped[pair]=(found[0]["target_table"],found[0]["target_id"])
        t,n=self.mapped[pair]
        if t!=expected:
            raise ValueError("Target type mismatch for "+table+"/"+id)
        self.target(t,n)
        if t=="erp_file_object" and (n not in self.new_file_ids or self.apply):
            self.verify_target_file(n)
        return n
    def map(self,table,id,source,decision,target,create):
        id=ident(id) if table in SOURCE_TABLES else key(id)
        pair=(table,id)
        if pair in self.touched:
            raise ValueError("Duplicate reviewed source: "+table+"/"+id)
        self.touched.add(pair)
        src,dec=sha(source),sha(decision)
        self.cur.execute("SELECT source_sha256,decision_sha256,target_table,target_id FROM erp_legacy_migration_map WHERE source_namespace=%s AND source_table=%s AND source_key=%s",(self.ns,table,id))
        rows=self.cur.fetchall()
        if rows:
            old=rows[0]
            if (old["source_sha256"],old["decision_sha256"],old["target_table"])!=(src,dec,target):
                raise ValueError("Previously migrated source/decision changed; no overwrite: "+table+"/"+id)
            n=old["target_id"]
            self.target(target,n)
            self.skipped[table]=self.skipped.get(table,0)+1
        else:
            n=create()
            self.target(target,n)
            self.execute("INSERT INTO erp_legacy_migration_map(source_namespace,source_table,source_key,source_sha256,decision_sha256,target_table,target_id,archive_sha256) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",(self.ns,table,id,src,dec,target,n,self.archive))
            self.inserted[table]=self.inserted.get(table,0)+1
            self.new.add(pair)
            if target=="erp_file_object":
                self.new_file_ids.add(n)
        self.mapped[pair]=(target,n)
        return n
    def file_ref(self,d):
        if ("fileSourceId" in d)==("rawFileKey" in d):
            raise ValueError("Choose exactly one fileSourceId or rawFileKey")
        return self.ref("sys_file",d["fileSourceId"],"erp_file_object") if "fileSourceId" in d else self.ref("manual_raw_file",d["rawFileKey"],"erp_file_object")
    def import_file(self,table,sid,row,d):
        path=d.get("archivePath")
        if path not in self.file_manifest:
            raise ValueError("Reviewed file path missing from archive")
        info=self.file_manifest[path];source=safe_path(self.root,"files/"+path)
        if row.get("file_hash") and re.fullmatch(r"[0-9a-fA-F]{64}",str(row["file_hash"])) and str(row["file_hash"]).lower()!=info["sha256"]:
            raise ValueError("Legacy file SHA256 differs from archived original")
        if row.get("file_size") is not None and int(row["file_size"])!=info["size"]:
            raise ValueError("Legacy file size differs from archived original")
        filename=required(d,"filename",240) if "filename" in d else required(row,"file_name",240)
        if "/" in filename or "\\" in filename or any(ord(c)<32 for c in filename):
            raise ValueError("File name must not contain path separators or control characters")
        media=enum(d.get("mediaType"),{"application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document"})
        with source.open("rb") as f: head=f.read(8)
        valid=({"application/pdf":head.startswith(b"%PDF-"),"image/jpeg":head.startswith(b"\xff\xd8\xff"),
                "image/png":head.startswith(b"\x89PNG\r\n\x1a\n"),"text/plain":b"\x00" not in head,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document":head.startswith(b"PK\x03\x04")})[media]
        if not valid or info["size"]<=0:
            raise ValueError("Reviewed file content does not match declared media type")
        if media.endswith("wordprocessingml.document"):
            from zipfile import ZipFile
            with ZipFile(source) as z:
                if "[Content_Types].xml" not in z.namelist() or "word/document.xml" not in z.namelist():
                    raise ValueError("Archive file is not a valid DOCX package")
        def create_file():
            name=str(uuid.uuid4())
            if self.apply:
                dst=self.storage/name
                with source.open("rb") as src,dst.open("xb") as dest:
                    self.created_files.append(dst)
                    shutil.copyfileobj(src,dest,1024*1024)
                    dest.flush()
                    os.fsync(dest.fileno())
                if dst.stat().st_size!=info["size"] or digest(dst)!=info["sha256"]:
                    raise ValueError("Copied original differs from archive")
            return self.execute("INSERT INTO erp_file_object(storage_key,sha256,byte_size,media_type,original_name,uploaded_by) VALUES(%s,%s,%s,%s,%s,%s)",(name,info["sha256"],info["size"],media,filename,self.actor))
        file_id=self.map(table,sid,row,{**d,"archiveFileSha256":info["sha256"]},"erp_file_object",create_file)
        saved=self.target("erp_file_object",file_id)
        if saved["sha256"]!=info["sha256"] or saved["byte_size"]!=info["size"] or saved["media_type"]!=media:
            raise ValueError("Target file metadata differs from archive")
        if file_id not in self.new_file_ids or self.apply:
            storage_key=str(uuid.UUID(saved["storage_key"]))
            if storage_key!=saved["storage_key"] or digest(safe_path(self.storage,storage_key))!=info["sha256"]:
                raise ValueError("Existing target original missing or changed")
        return file_id
    def reserve_card_numbers(self):
        codes=set(); maximum=0
        for t in self.manifest["tables"]:
            if t["kind"]!="BASE TABLE" or t["table"] not in ("zj_wbcard","zj_wbcard_number","zj_wbcard_sequence"):
                continue
            with safe_path(self.root,t["data"]).open("r",encoding="utf-8") as f:
                for line in f:
                    row=json.loads(line)
                    if t["table"]=="zj_wbcard_sequence":
                        n=value(row.get("sequence_value"))
                        if n is None or not re.fullmatch(r"[0-9]{1,18}",str(n)):
                            raise ValueError("Invalid legacy maintenance card sequence")
                        maximum=max(maximum,int(n))
                    elif row.get("code") not in (None,""):
                        code=required(row,"code",100)
                        codes.add(code)
                        if re.fullmatch(r"[0-9]{1,18}",code):
                            maximum=max(maximum,int(code))
        for code in sorted(codes):
            self.cur.execute("SELECT source_namespace FROM erp_maintenance_card_legacy_number WHERE code=%s",(code,))
            previous=self.cur.fetchall()
            if previous and previous[0]["source_namespace"]!=self.ns:
                raise ValueError("Reserved legacy card number belongs to another installation: "+code)
            self.cur.execute("SELECT id FROM erp_maintenance_card WHERE code=%s",(code,))
            existing=self.cur.fetchall()
            if existing:
                known=self.one("SELECT COUNT(*) AS n FROM erp_legacy_migration_map WHERE source_namespace=%s AND source_table='zj_wbcard' AND target_table='erp_maintenance_card' AND target_id=%s",(self.ns,existing[0]["id"]))
                if known["n"]!=1:
                    raise ValueError("Legacy card number collides with an existing target card: "+code)
            if not previous:
                self.execute("INSERT INTO erp_maintenance_card_legacy_number(code,source_namespace) VALUES(%s,%s)",(code,self.ns))
        self.execute("UPDATE erp_maintenance_card_sequence SET last_number=GREATEST(last_number,%s) WHERE id=1",(maximum,))
        self.summary["reservedLegacyCardNumbers"]=len(codes)
        self.summary["legacyCardSequenceMaximum"]=maximum
    def run(self):
        self.reserve_card_numbers()
        for d in self.plan.get("customers",[]):
            k=key(d["key"]); code=required(d,"code",64); name=required(d,"name",240)
            self.map("manual_customer",k,{"key":k},d,"erp_customer",
                lambda:self.execute("INSERT INTO erp_customer(customer_code,name,phone,email,address,notes) VALUES(%s,%s,%s,%s,%s,%s)",
                    (code,name,optional(d,"phone",40),optional(d,"email",190),optional(d,"address",500),optional(d,"notes",20000))))
        for d in self.plan.get("customerContacts",[]):
            k=key(d["key"]); customer=self.ref("manual_customer",d["customerKey"],"erp_customer")
            self.map("manual_customer_contact",k,{"key":k},d,"erp_customer_contact",
                lambda:self.execute("INSERT INTO erp_customer_contact(customer_id,name,phone,email,position_name,is_primary,notes) VALUES(%s,%s,%s,%s,%s,%s,%s)",(customer,required(d,"name",160),optional(d,"phone",40),optional(d,"email",190),optional(d,"positionName",120),boolean(d.get("primary",False)),optional(d,"notes",1000))))
        for d in self.plan.get("accounts",[]):
            sid=ident(d["sourceId"]); row=self.legacy("sys_user",sid)
            username=required(d,"username",100) if "username" in d else required(row,"user_name",100)
            display=required(row,"nick_name",200)
            def create_account():
                if d.get("existingId") is not None:
                    existing=self.target("erp_identity",int(ident(d["existingId"])))
                    if existing["username"]!=username:
                        raise ValueError("Existing account username does not match explicit decision")
                    return existing["id"]
                if username.casefold()=="admin":
                    raise ValueError("Legacy admin must be explicitly linked to existing admin, never created or overwritten")
                role=enum(d.get("role","employee"),{"employee","manager"})
                if role=="manager" and d.get("managerConfirmed") is not True:
                    raise ValueError("Management privileges require explicit confirmation")
                old_hash=row.get("password")
                compatible=isinstance(old_hash,str) and re.fullmatch(r"\$2[aby]\$(?:0[4-9]|1[0-6])\$[./A-Za-z0-9]{53}",old_hash)
                source_active=str(row.get("status"))=="0" and str(row.get("del_flag"))=="0"
                # Preserve compatible BCrypt hashes. Incompatible/deleted/disabled accounts cannot log in.
                password_hash=old_hash if compatible else "!legacy-reset-required-"+uuid.uuid4().hex
                status="active" if compatible and source_active else "disabled"
                n=self.execute("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(%s,%s,%s,%s)",(username,password_hash,display,status))
                self.execute("INSERT INTO erp_identity_role(identity_id,role_id) SELECT %s,id FROM erp_role WHERE code=%s",(n,role))
                return n
            self.map("sys_user",sid,row,d,"erp_identity",create_account)
        for d in self.plan.get("employees",[]):
            sid=ident(d["sourceId"]); row=self.legacy("zj_employee",sid)
            kind=enum(d.get("type"),{"general","operator","engineer","manager"})
            identity=None
            if boolean(d["linkAccount"]):
                identity=self.ref("sys_user",row.get("source_user_id"),"erp_identity")
            active=value(row.get("active"))
            if active not in (0,1,False,True):
                raise ValueError("Legacy employee status unknown")
            self.map("zj_employee",sid,row,d,"erp_employee",
                lambda:self.execute("INSERT INTO erp_employee(employee_code,full_name,identity_id,employee_type,status) VALUES(%s,%s,%s,%s,%s)",
                    (optional(d,"code",100) or "LEGACY-E-"+sid,required(row,"name",200),identity,kind,"active" if active else "inactive")))
        for d in self.plan.get("qualifications",[]):
            sid=ident(d["sourceId"]); row=self.legacy("zj_report_credential",sid)
            employee=self.ref("zj_employee",d["employeeSourceId"],"erp_employee")
            if row.get("employee_id") is not None and ident(row["employee_id"])!=ident(d["employeeSourceId"]):
                raise ValueError("Credential employee differs from reviewed source relationship")
            status=enum(d.get("status"),{"valid","expired","revoked"})
            # Only an explicit qualification decision, never every legacy credential or text material.
            self.map("zj_report_credential",sid,row,d,"erp_employee_qualification",
                lambda:self.execute("INSERT INTO erp_employee_qualification(employee_id,qualification_name,certificate_no,expires_on,status,notes) VALUES(%s,%s,%s,%s,%s,%s)",
                    (employee,required(row,"title",200),optional(row,"certificate_no",120),date(row,"valid_until"),status,"旧资料原件见迁移档案；资格状态已人工确认")))
        for d in self.plan.get("partners",[]):
            sid=ident(d["sourceId"]); row=self.legacy("zj_intermediary",sid)
            notes=canonical({"company":row.get("company"),"remark":row.get("remark")})
            self.map("zj_intermediary",sid,row,d,"erp_partner",
                lambda:self.execute("INSERT INTO erp_partner(partner_code,name,phone,email,notes) VALUES(%s,%s,%s,%s,%s)",
                    (optional(d,"code",64) or "LEGACY-P-"+sid,required(row,"name",240),optional(row,"phone",40),optional(row,"email",190),notes)))
        for d in self.plan.get("contracts",[]):
            table=enum(d.get("sourceTable"),CONTRACTS);sid=ident(d["sourceId"]);row=self.legacy(table,sid)
            if row.get("contract_type")!=CONTRACTS[table]:
                raise ValueError("Contract table/type mismatch")
            customer=self.ref("manual_customer",d["customerKey"],"erp_customer")
            code=required(d,"contractNo",80) if "contractNo" in d else required(row,"contract_id",80)
            title=required(d,"title",240) if "title" in d else required(row,"contract_name",240)
            amount=money(d["amount"] if "amount" in d else row.get("amount"))
            starts,ends=dates(row,"start_time","end_time")
            # Import as draft; old records have no authoritative new-workflow status.
            signing=enum(d.get("signingSource"),{"direct","partner"})
            ownership=row.get("ownership")
            if (ownership=="company" and signing!="direct") or (ownership=="intermediary" and signing!="partner"):
                raise ValueError("Signing source contradicts legacy ownership")
            partner=share=None
            if signing=="partner":
                legacy_partner=d.get("partnerSourceId",row.get("intermediary_id"))
                if row.get("intermediary_id") is not None and ident(legacy_partner)!=ident(row["intermediary_id"]):
                    raise ValueError("Intermediary mapping contradicts source")
                partner=self.ref("zj_intermediary",legacy_partner,"erp_partner")
                share=percent(d.get("sharePercent"))
            def create_contract():
                n=self.execute("INSERT INTO erp_contract(contract_no,title,customer_id,contract_type,service_type,amount,starts_on,ends_on,status,notes,signing_source,partner_share_percent) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'draft',%s,%s,%s)",
                    (code,title,customer,CONTRACTS[table],TYPES[CONTRACTS[table]],amount,starts,ends,optional(row,"remark",60000),signing,share))
                if partner:
                    self.execute("INSERT INTO erp_contract_partner(contract_id,partner_id,share_percent) VALUES(%s,%s,%s)",(n,partner,share))
                return n
            self.map(table,sid,row,d,"erp_contract",create_contract)
        for d in self.plan.get("projects",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_work_project",sid)
            customer=self.ref("manual_customer",d["customerKey"],"erp_customer")
            project_type=enum(row.get("project_type"),TYPES)
            code=required(d,"projectNo",80) if "projectNo" in d else required(row,"project_no",80)
            name=required(row,"project_name",240);starts,ends=dates(row,"start_date","end_date")
            contract=None
            if row.get("contract_id") is not None:
                table=enum(d.get("contractSourceTable"),CONTRACTS)
                if CONTRACTS[table]!=project_type:
                    raise ValueError("Project and contract type mismatch")
                contract=self.ref(table,row["contract_id"],"erp_contract")
                if self.target("erp_contract",contract)["customer_id"]!=customer:
                    raise ValueError("Project and contract customer mismatch")
            leader=technical=None
            if d.get("leaderSourceId") is not None or d.get("technicalSourceId") is not None:
                if d.get("leaderSourceId") is None or d.get("technicalSourceId") is None:
                    raise ValueError("Both project leaders must be reviewed together")
                leader=self.ref("zj_employee",d["leaderSourceId"],"erp_employee")
                technical=self.ref("zj_employee",d["technicalSourceId"],"erp_employee")
                if leader==technical:
                    raise ValueError("Project leaders must be different")
                for employee in (leader,technical):
                    e=self.target("erp_employee",employee)
                    q=self.one("SELECT COUNT(*) AS n FROM erp_employee_qualification WHERE employee_id=%s AND status='valid' AND (expires_on IS NULL OR expires_on>=CURRENT_DATE)",(employee,))
                    if e["employee_type"]!="engineer" or e["status"]!="active" or q["n"]==0:
                        raise ValueError("Project leader must have confirmed active engineering qualification")
            description=canonical({k:row.get(k) for k in ("site_address","contact_name","contact_phone","remark","project_type")})
            def create_project():
                n=self.execute("INSERT INTO erp_project(project_no,name,contract_id,customer_id,description,manager_employee_id,technical_lead_employee_id,planned_start,planned_end,status) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,'planned')",
                    (code,name,contract,customer,description,leader,technical,starts,ends))
                if contract:
                    self.execute("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(%s,%s,'service')",(contract,n))
                if project_type=="WB":
                    self.execute("INSERT INTO erp_maintenance_project(project_id,notes) VALUES(%s,%s)",(n,"旧项目状态待人工确认；原始资料完整保全"))
                return n
            self.map("zj_work_project",sid,row,d,"erp_project",create_project)
        for d in self.plan.get("contractLinks",[]):
            c=self.ref(enum(d["contractSourceTable"],CONTRACTS),d["contractSourceId"],"erp_contract")
            p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            if self.target("erp_contract",c)["customer_id"]!=self.target("erp_project",p)["customer_id"]:
                raise ValueError("Additional contract and project customer mismatch")
            relation=enum(d["relationType"],{"service","renewal","supplement","other"})
            self.cur.execute("SELECT relation_type FROM erp_contract_project WHERE contract_id=%s AND project_id=%s",(c,p))
            old=self.cur.fetchall()
            if old:
                if old[0]["relation_type"]!=relation:
                    raise ValueError("Existing contract relationship differs; no overwrite")
            else:
                self.execute("INSERT INTO erp_contract_project(contract_id,project_id,relation_type) VALUES(%s,%s,%s)",(c,p,relation))
        # Explicit status is applied only to newly inserted contracts after project linkage.
        for d in self.plan.get("contracts",[]):
            table=d["sourceTable"];sid=ident(d["sourceId"])
            desired=enum(d.get("status","draft"),{"draft","pending","active","completed","terminated"})
            if (table,sid) in self.new and desired!="draft":
                c=self.ref(table,sid,"erp_contract")
                if desired in ("active","completed","terminated"):
                    count=self.one("SELECT COUNT(*) AS n FROM erp_contract_project WHERE contract_id=%s",(c,))
                    if count["n"]==0:
                        raise ValueError("Reviewed non-draft contract requires a linked project")
                self.execute("UPDATE erp_contract SET status=%s WHERE id=%s",(desired,c))
        for d in self.plan.get("contacts",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zjerp_wbcard_phone",sid)
            self.map("zjerp_wbcard_phone",sid,row,d,"erp_maintenance_contact",
                lambda:self.execute("INSERT INTO erp_maintenance_contact(name,phone) VALUES(%s,%s)",(required(row,"name",100),required(row,"phone",50))))
        for d in self.plan.get("files",[]):
            sid=ident(d["sourceId"]);row=self.legacy("sys_file",sid)
            self.import_file("sys_file",sid,row,d)
        for d in self.plan.get("rawFiles",[]):
            k=key(d["key"])
            self.import_file("manual_raw_file",k,{"key":k},d)
        actor=None
        if any(self.plan.get(s) for s in ("contractFiles","materials","materialArchives","cards","maintenanceStates")):
            # Never fabricate actor IDs. Use the pre-existing, active target admin explicitly supplied to CLI.
            actor=self.actor
        for d in self.plan.get("contractFiles",[]):
            k=key(d["key"])
            c=self.ref(enum(d["contractSourceTable"],CONTRACTS),d["contractSourceId"],"erp_contract")
            f=self.file_ref(d)
            kind=enum(d["fileType"],{"contract","maintenance_card","project_material","other"})
            self.map("manual_contract_file",k,{"key":k},d,"erp_contract_file",
                lambda:self.execute("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(%s,%s,%s,%s,%s)",(c,f,kind,optional(d,"title",240),actor)))
        for d in self.plan.get("materials",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_wbcard_material",sid)
            f=self.file_ref(d)
            if self.target("erp_file_object",f)["media_type"]!="application/pdf":
                raise ValueError("Maintenance material must be PDF")
            self.map("zj_wbcard_material",sid,row,d,"erp_maintenance_material",
                lambda:self.execute("INSERT INTO erp_maintenance_material(name,file_id,notes,created_by) VALUES(%s,%s,%s,%s)",(required(row,"name",200),f,optional(row,"remark",2000),actor)))
        for d in self.plan.get("materialArchives",[]):
            k=key(d["key"]);p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            c=self.ref("zj_contract_maintenance",d["contractSourceId"],"erp_contract")
            m=self.ref("zj_wbcard_material",d["materialSourceId"],"erp_maintenance_material")
            self.one("SELECT contract_id FROM erp_contract_project WHERE contract_id=%s AND project_id=%s",(c,p))
            resource=self.target("erp_maintenance_material",m)
            f=resource["file_id"]
            title=required(d,"title",200) if "title" in d else resource["name"]
            def create_archive():
                n=self.execute("INSERT INTO erp_maintenance_material_archive(contract_id,project_id,material_id,file_id,title,created_by) VALUES(%s,%s,%s,%s,%s,%s)",(c,p,m,f,title,actor))
                self.cur.execute("SELECT id FROM erp_contract_file WHERE contract_id=%s AND file_id=%s",(c,f))
                if not self.cur.fetchall():
                    self.execute("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(%s,%s,'project_material',%s,%s)",(c,f,title,actor))
                return n
            self.map("manual_material_archive",k,{"key":k},d,"erp_maintenance_material_archive",create_archive)
        for d in self.plan.get("cards",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_wbcard",sid)
            p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            c=self.ref("zj_contract_maintenance",d["contractSourceId"],"erp_contract")
            f=self.file_ref(d)
            self.one("SELECT contract_id FROM erp_contract_project WHERE contract_id=%s AND project_id=%s",(c,p))
            if self.target("erp_file_object",f)["media_type"]!="application/pdf":
                raise ValueError("Maintenance card must be PDF")
            code=required(row,"code",100);start,end=dates(d,"startsOn","endsOn")
            if not start or not end:
                raise ValueError("Maintenance card dates must be confirmed")
            def create_card():
                n=self.execute("INSERT INTO erp_maintenance_card(project_id,contract_id,file_id,code,project_name,company,leader,phone,systems,starts_on,ends_on,printed,sent,created_by) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                    (p,c,f,code,required(d,"projectName",255),required(d,"company",255),required(d,"leader",100),required(d,"phone",255),required(d,"systems",60000),start,end,boolean(d["printed"]),boolean(d["sent"]),actor))
                if re.fullmatch(r"[0-9]{1,18}",code):
                    self.execute("UPDATE erp_maintenance_card_sequence SET last_number=GREATEST(last_number,%s) WHERE id=1",(int(code),))
                self.cur.execute("SELECT file_type FROM erp_contract_file WHERE contract_id=%s AND file_id=%s",(c,f))
                links=self.cur.fetchall()
                if links and links[0]["file_type"]!="maintenance_card":
                    raise ValueError("Existing attachment type conflicts with maintenance card")
                if not links:
                    self.execute("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(%s,%s,'maintenance_card',%s,%s)",(c,f,code,actor))
                return n
            self.map("zj_wbcard",sid,row,d,"erp_maintenance_card",create_card)
        # Annual state is optional and explicit; never infer Honeybee status from unrelated fields.
        for d in self.plan.get("maintenanceStates",[]):
            p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            c=self.ref("zj_contract_maintenance",d["contractSourceId"],"erp_contract")
            self.one("SELECT contract_id FROM erp_contract_project WHERE contract_id=%s AND project_id=%s",(c,p))
            state=(boolean(d["honeybeeContractUploaded"]),boolean(d["honeybeeProjectCreated"]),boolean(d["materialsCompleted"]),
                   enum(d["publicCardStatus"],{"not_started","preparing","sent","delivered"}))
            self.cur.execute("SELECT honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status FROM erp_maintenance_contract_state WHERE contract_id=%s AND project_id=%s",(c,p))
            existing=self.cur.fetchall()
            if existing:
                x=existing[0]
                if tuple(x.values())!=state:
                    raise ValueError("Annual state differs; do not overwrite existing business state")
            else:
                self.execute("INSERT INTO erp_maintenance_contract_state(contract_id,project_id,honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status,updated_by) VALUES(%s,%s,%s,%s,%s,%s,%s)",(c,p,*state,actor))
            if boolean(d.get("current",False)):
                contract=self.target("erp_contract",c)
                if contract["status"]!="active" or not contract["starts_on"] or not contract["ends_on"]:
                    raise ValueError("Current maintenance contract must be explicitly active with dates")
                m=self.one("SELECT * FROM erp_maintenance_project WHERE project_id=%s",(p,))
                if m["current_contract_id"] not in (None,c):
                    raise ValueError("A different current maintenance contract already exists")
                if m["current_contract_id"] is None:
                    self.execute("UPDATE erp_maintenance_project SET current_contract_id=%s,honeybee_contract_uploaded=%s,honeybee_project_created=%s,project_materials_completed=%s,public_card_status=%s,updated_by=%s WHERE project_id=%s",(c,*state,actor,p))
                    self.execute("UPDATE erp_project SET contract_id=%s,planned_start=%s,planned_end=%s WHERE id=%s",(c,contract["starts_on"],contract["ends_on"],p))
        for d in self.plan.get("projectIssues",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_work_project_issue",sid)
            p=self.ref("zj_work_project",row["project_id"],"erp_project")
            severity=enum(d["severity"],{"low","medium","high","critical"})
            status=enum(d["status"],{"open","in_progress","resolved","closed"})
            description=canonical({k:row.get(k) for k in ("description","found_date","resolved_date")})
            self.map("zj_work_project_issue",sid,row,d,"erp_project_issue",
                lambda:self.execute("INSERT INTO erp_project_issue(project_id,title,description,severity,status) VALUES(%s,%s,%s,%s,%s)",(p,required(row,"title",240),description,severity,status)))
        for d in self.plan.get("projectDocuments",[]):
            table=enum(d["sourceTable"],{"zj_report","zj_inspection_report","zj_project_document"})
            sid=ident(d["sourceId"]);row=self.legacy(table,sid)
            p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            if row.get("project_id") is not None and ident(row["project_id"])!=ident(d["projectSourceId"]):
                raise ValueError("Historical document project contradicts source")
            title=required(d,"title",240) if "title" in d else required(row,{"zj_report":"report_name","zj_inspection_report":"report_no","zj_project_document":"name"}[table],240)
            kind=enum(d.get("kind","historical_report" if table!="zj_project_document" else "project_document"),{"historical_report","project_document","other"})
            links=d.get("files",[])
            if not isinstance(links,list) or any(not isinstance(x,dict) for x in links):
                raise ValueError("Invalid historical document files")
            file_ids=[self.file_ref(x) for x in links]
            if len(set(file_ids))!=len(file_ids):
                raise ValueError("Duplicate historical document file")
            if not file_ids and not boolean(d.get("missingOriginalConfirmed",False)):
                raise ValueError("Missing original must be explicitly acknowledged")
            created=value(row.get("created_at",row.get("create_time")))
            original_status=optional(d,"originalStatus",200)
            report_no=optional(row,"report_id" if table=="zj_report" else "report_no",100) if table!="zj_project_document" else optional(d,"reportNo",100)
            def create_document():
                n=self.execute("INSERT INTO erp_legacy_project_document(project_id,title,document_kind,report_no,source_namespace,source_table,source_key,original_created_at,original_status,archive_note) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                    (p,title,kind,report_no,self.ns,table,sid,created,original_status,optional(d,"note",2000)))
                for f in file_ids:
                    self.execute("INSERT INTO erp_legacy_project_document_file(document_id,file_id) VALUES(%s,%s)",(n,f))
                return n
            self.map(table,sid,row,d,"erp_legacy_project_document",create_document)
        self.summary.update(inserted=self.inserted,alreadyMapped=self.skipped,rawArchiveRetained=True)
        self.cur.execute("SELECT source_table,source_key FROM erp_legacy_migration_map WHERE source_namespace=%s",(self.ns,))
        existing={}
        for x in self.cur.fetchall():
            existing.setdefault(x["source_table"],set()).add(x["source_key"])
        self.summary["unmappedArchiveRows"]={t["table"]:t["rows"]-len(set(self.source.get(t["table"],{})) & existing.get(t["table"],set())) for t in self.manifest["tables"] if t["kind"]=="BASE TABLE"}
        self.summary["totalMappedByTable"]={table:len(keys) for table,keys in existing.items()}
        return self.summary

def blank_plan(manifest,hash):
    return {"format":"erp-legacy-review-v1","archiveSha256":hash,"note":"Add explicitly reviewed rows only; see LEGACY_MIGRATION.md. All unselected records remain in raw archive.",**{s:[] for s in SECTIONS}}


TRIAL_MARKER=".erp-next-trial.json"
def trial_identity(cursor,storage):
    cursor.execute("SELECT DATABASE() AS db,@@server_uuid AS server_uuid")
    server=cursor.fetchone()
    if server["db"]!="erp_next":
        raise ValueError("Only erp_next target schema is supported")
    cursor.execute("SELECT trial_id,server_uuid FROM erp_trial_isolation_marker WHERE id=1")
    db=cursor.fetchone()
    marker=json.loads(safe_path(storage,TRIAL_MARKER).read_text(encoding="utf-8"))
    expected={"format":"erp-next-trial-v1","trialId":db["trial_id"],"serverUuid":server["server_uuid"]} if db else None
    if not db or db["server_uuid"]!=server["server_uuid"] or marker!=expected:
        raise ValueError("Trial database and private file marker mismatch; stop")
    return marker

def receipt_for(identity,archive_hash,plan_hash):
    return {"format":"erp-next-preview-v1","trial":identity,"archiveSha256":archive_hash,"planSha256":plan_hash}

def verify_receipt(path,expected):
    if not path or json.loads(Path(path).read_text(encoding="utf-8"))!=expected:
        raise ValueError("Preview receipt must match exact trial target, archive and plan")

def reconcile(migration):
    migration.cur.execute("SELECT source_table,source_key,source_sha256,target_table,target_id FROM erp_legacy_migration_map WHERE source_namespace=%s ORDER BY source_table,source_key",(migration.ns,))
    rows=migration.cur.fetchall()
    errors=[];counts={};files=set()
    for row in rows:
        try:
            table,id=row["source_table"],row["source_key"]
            if table in SOURCE_TABLES and sha(migration.legacy(table,id))!=row["source_sha256"]:
                raise ValueError("Archived source hash differs")
            target=row["target_table"];n=row["target_id"]
            migration.target(target,n)
            if target=="erp_file_object" and n not in files:
                migration.verify_target_file(n);files.add(n)
            counts[table]=counts.get(table,0)+1
        except (ValueError,FileNotFoundError,OSError) as e:
            errors.append({"sourceTable":row["source_table"],"sourceKey":row["source_key"],"targetTable":row["target_table"],"targetId":row["target_id"],"error":str(e)})
    return {"mappedRows":len(rows),"verifiedRowsByTable":counts,"verifiedFiles":len(files),"errors":errors,"note":"Verifies archive source hashes, target existence and attachment bytes; business field equivalence still requires reviewed mapping and manual sampling."}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command",choices=["new-plan","preview","apply","reconcile"])
    parser.add_argument("--archive",required=True)
    parser.add_argument("--plan",required=True)
    parser.add_argument("--host",default="127.0.0.1")
    parser.add_argument("--port",type=int,default=3306)
    parser.add_argument("--user")
    parser.add_argument("--actor",type=int)
    parser.add_argument("--files-root")
    parser.add_argument("--isolated-trial",action="store_true")
    parser.add_argument("--confirm-plan-sha256")
    parser.add_argument("--preview-receipt",help="New receipt path for preview; existing matching receipt for apply")
    args=parser.parse_args()
    os.umask(0o077)
    root=Path(args.archive).resolve(strict=True)
    manifest,hash=archive_lib.verify(root)
    if args.command=="new-plan":
        archive_lib.secure_write(Path(args.plan),json.dumps(blank_plan(manifest,hash),ensure_ascii=False,indent=2)+"\n")
        return
    if not args.isolated_trial or not args.user or not args.files_root or not args.actor:
        parser.error("Only isolated trial erp_next is supported; --isolated-trial --user --actor --files-root required")
    plan=read_plan(args.plan,hash)
    if args.command!="reconcile" and not any(plan.get(s) for s in SECTIONS):
        parser.error("Empty reviewed plan")
    plan_hash=sha(plan)
    if args.command in ("preview","apply") and not args.preview_receipt:
        parser.error("Preview and apply require --preview-receipt")
    if args.command=="preview" and Path(args.preview_receipt).exists():
        parser.error("Preview receipt already exists; choose a new path")
    if args.command=="apply" and args.confirm_plan_sha256!=plan_hash:
        parser.error("Apply requires exact plan hash printed by preview")
    storage=Path(args.files_root).resolve(strict=True)
    if not storage.is_dir() or storage.is_relative_to(root) or root.is_relative_to(storage):
        parser.error("Target private file directory must be separate from archive")
    import mysql.connector
    conn=mysql.connector.connect(host=args.host,port=args.port,user=args.user,database="erp_next",
        password=os.environ.get("ERP_NEXT_DB_PASSWORD") or getpass.getpass("Target ERP password: "),connection_timeout=8,autocommit=False)
    migration=None; commit_attempted=False; locked=False
    try:
        c=conn.cursor(dictionary=True)
        c.execute("SELECT DATABASE() AS db,GET_LOCK('erp_next_legacy_trial_migration',0) AS locked")
        result=c.fetchone()
        if result["db"]!="erp_next" or result["locked"]!=1:
            raise ValueError("Target DB invalid or another migration is active")
        locked=True
        identity=trial_identity(c,storage)
        receipt=receipt_for(identity,hash,plan_hash)
        if args.command=="apply":
            verify_receipt(args.preview_receipt,receipt)
        c.execute("SELECT COUNT(*) AS n FROM erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id WHERE i.id=%s AND i.status='active' AND r.code='admin'",(args.actor,))
        if c.fetchone()["n"]!=1:
            raise ValueError("A pre-existing active target admin is required")
        migration=Migration(conn,root,manifest,hash,plan,storage,args.command=="apply")
        migration.actor=args.actor
        if args.command=="reconcile":
            summary=reconcile(migration)
            conn.rollback()
            print(json.dumps({"mode":"reconcile",**summary},ensure_ascii=False,indent=2))
            if summary["errors"]:
                raise ValueError("Reconciliation found mismatches; do not promote trial data")
            return
        summary=migration.run()
        if args.command=="apply":
            migration.execute("INSERT INTO erp_legacy_migration_run(source_namespace,archive_sha256,decision_sha256,summary_json) VALUES(%s,%s,%s,%s) ON DUPLICATE KEY UPDATE id=id",(migration.ns,hash,plan_hash,canonical(summary)))
            commit_attempted=True
            conn.commit()
        else:
            conn.rollback()
            archive_lib.secure_write(Path(args.preview_receipt),canonical(receipt)+"\n")
        print(json.dumps({"mode":args.command,"trialId":identity["trialId"],"planSha256":plan_hash,**summary},ensure_ascii=False,indent=2))
    except Exception:
        try:
            conn.rollback()
        finally:
            # A network failure during COMMIT has unknown outcome; retain files and reconcile rather than delete a committed artifact.
            if migration and not commit_attempted:
                for path in migration.created_files:
                    path.unlink(missing_ok=True)
        raise
    finally:
        if locked:
            try:
                c.execute("SELECT RELEASE_LOCK('erp_next_legacy_trial_migration')");c.fetchall()
            except Exception:
                pass
        conn.close()

if __name__=="__main__":
    try:
        main()
    except Exception as e:
        print("Trial migration failed; no automatic overwrite. Check isolated target, transaction and backup:",str(e),file=sys.stderr)
        sys.exit(1)
