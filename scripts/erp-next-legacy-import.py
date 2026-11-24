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
SECTIONS = ("customers","accounts","employees","qualifications","partners","contracts","projects","contacts","files","contractFiles","materials","cards","maintenanceStates")
TARGETS = {"erp_customer","erp_identity","erp_employee","erp_employee_qualification","erp_partner","erp_contract","erp_project","erp_maintenance_contact","erp_file_object","erp_contract_file","erp_maintenance_material","erp_maintenance_card"}
SOURCE_TABLES = {"sys_user","zj_employee","zj_report_credential","zj_intermediary","zj_work_project","zjerp_wbcard_phone","sys_file","zj_wbcard_material","zj_wbcard",*CONTRACTS}

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
    p=json.loads(Path(path).read_text(encoding="utf-8"))
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
    def target(self,table,id):
        if table not in TARGETS:
            raise ValueError("Unknown target table")
        return self.one("SELECT * FROM "+table+" WHERE id=%s",(id,))
    def ref(self,table,id,expected):
        id=ident(id) if table in SOURCE_TABLES else key(id)
        pair=(table,id)
        if pair not in self.mapped:
            self.cur.execute("SELECT target_table,target_id FROM erp_legacy_migration_map WHERE source_namespace=%s AND source_table=%s AND source_key=%s",(self.ns,table,id))
            found=self.cur.fetchall()
            if len(found)!=1:
                raise ValueError("Required source reference has not been migrated: "+table+"/"+id)
            self.mapped[pair]=(found[0]["target_table"],found[0]["target_id"])
        t,n=self.mapped[pair]
        if t!=expected:
            raise ValueError("Target type mismatch for "+table+"/"+id)
        self.target(t,n)
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
        self.mapped[pair]=(target,n)
        return n
    def run(self):
        for d in self.plan.get("customers",[]):
            k=key(d["key"]); code=required(d,"code",64); name=required(d,"name",240)
            self.map("manual_customer",k,{"key":k},d,"erp_customer",
                lambda:self.execute("INSERT INTO erp_customer(customer_code,name,phone,email,address,notes) VALUES(%s,%s,%s,%s,%s,%s)",
                    (code,name,optional(d,"phone",40),optional(d,"email",190),optional(d,"address",500),optional(d,"notes",20000))))
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
                # A deliberately unmatchable hash: disabled until admin sets password and permissions.
                return self.execute("INSERT INTO erp_identity(username,password_hash,display_name,status) VALUES(%s,%s,%s,'disabled')",
                    (username,"!legacy-reset-required-"+uuid.uuid4().hex,display))
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
        for d in self.plan.get("contacts",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zjerp_wbcard_phone",sid)
            self.map("zjerp_wbcard_phone",sid,row,d,"erp_maintenance_contact",
                lambda:self.execute("INSERT INTO erp_maintenance_contact(name,phone) VALUES(%s,%s)",(required(row,"name",100),required(row,"phone",50))))
        for d in self.plan.get("files",[]):
            sid=ident(d["sourceId"]);row=self.legacy("sys_file",sid)
            path=d.get("archivePath")
            if path not in self.file_manifest:
                raise ValueError("Reviewed file path missing from archive")
            info=self.file_manifest[path];source=safe_path(self.root,"files/"+path)
            if row.get("file_hash") and re.fullmatch(r"[0-9a-fA-F]{64}",str(row["file_hash"])) and str(row["file_hash"]).lower()!=info["sha256"]:
                raise ValueError("Legacy file SHA256 differs from archived original")
            if row.get("file_size") is not None and int(row["file_size"])!=info["size"]:
                raise ValueError("Legacy file size differs from archived original")
            filename=required(d,"filename",240) if "filename" in d else required(row,"file_name",240)
            media=enum(d.get("mediaType"),{"application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document"})
            with source.open("rb") as f: head=f.read(8)
            valid=({"application/pdf":head.startswith(b"%PDF-"),"image/jpeg":head.startswith(b"\xff\xd8\xff"),
                    "image/png":head.startswith(b"\x89PNG\r\n\x1a\n"),"text/plain":b"\x00" not in head,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document":head.startswith(b"PK\x03\x04")})[media]
            if not valid or info["size"]<=0:
                raise ValueError("Reviewed file content does not match declared media type")
            def create_file():
                name=str(uuid.uuid4())
                if self.apply:
                    dst=self.storage/name
                    self.created_files.append(dst)
                    with source.open("rb") as src,dst.open("xb") as dest:
                        shutil.copyfileobj(src,dest,1024*1024)
                    if digest(dst)!=info["sha256"]:
                        raise ValueError("Copied original differs from archive")
                return self.execute("INSERT INTO erp_file_object(storage_key,sha256,byte_size,media_type,original_name) VALUES(%s,%s,%s,%s,%s)",(name,info["sha256"],info["size"],media,filename))
            file_id=self.map("sys_file",sid,row,{**d,"archiveFileSha256":info["sha256"]},"erp_file_object",create_file)
            saved=self.target("erp_file_object",file_id)
            if saved["sha256"]!=info["sha256"]:
                raise ValueError("Target file metadata differs from archive")
            if self.apply and (self.storage/saved["storage_key"]) not in self.created_files:
                if digest(self.storage/saved["storage_key"])!=info["sha256"]:
                    raise ValueError("Existing target original missing or changed")
        actor=None
        if any(self.plan.get(s) for s in ("contractFiles","materials","cards","maintenanceStates")):
            # Never fabricate actor IDs. Use the pre-existing, active target admin explicitly supplied to CLI.
            actor=self.actor
        for d in self.plan.get("contractFiles",[]):
            k=key(d["key"])
            c=self.ref(enum(d["contractSourceTable"],CONTRACTS),d["contractSourceId"],"erp_contract")
            f=self.ref("sys_file",d["fileSourceId"],"erp_file_object")
            kind=enum(d["fileType"],{"contract","maintenance_card","project_material","other"})
            self.map("manual_contract_file",k,{"key":k},d,"erp_contract_file",
                lambda:self.execute("INSERT INTO erp_contract_file(contract_id,file_id,file_type,title,created_by) VALUES(%s,%s,%s,%s,%s)",(c,f,kind,optional(d,"title",240),actor)))
        for d in self.plan.get("materials",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_wbcard_material",sid)
            f=self.ref("sys_file",d["fileSourceId"],"erp_file_object")
            if self.target("erp_file_object",f)["media_type"]!="application/pdf":
                raise ValueError("Maintenance material must be PDF")
            self.map("zj_wbcard_material",sid,row,d,"erp_maintenance_material",
                lambda:self.execute("INSERT INTO erp_maintenance_material(name,file_id,notes,created_by) VALUES(%s,%s,%s,%s)",(required(row,"name",200),f,optional(row,"remark",2000),actor)))
        for d in self.plan.get("cards",[]):
            sid=ident(d["sourceId"]);row=self.legacy("zj_wbcard",sid)
            p=self.ref("zj_work_project",d["projectSourceId"],"erp_project")
            c=self.ref("zj_contract_maintenance",d["contractSourceId"],"erp_contract")
            f=self.ref("sys_file",d["fileSourceId"],"erp_file_object")
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
            # Do not set active/current contract automatically. That requires the normal business rules.
        self.summary.update(inserted=self.inserted,alreadyMapped=self.skipped,rawArchiveRetained=True)
        self.cur.execute("SELECT source_table,COUNT(*) AS n FROM erp_legacy_migration_map WHERE source_namespace=%s GROUP BY source_table",(self.ns,))
        existing={x["source_table"]:x["n"] for x in self.cur.fetchall()}
        self.summary["unmappedArchiveRows"]={table:len(rows)-sum(1 for sid in rows if (table,sid) in self.mapped) for table,rows in self.source.items()}
        self.summary["totalMappedByTable"]=existing
        return self.summary

def blank_plan(manifest,hash):
    return {"format":"erp-legacy-review-v1","archiveSha256":hash,"note":"Add explicitly reviewed rows only; see LEGACY_MIGRATION.md. All unselected records remain in raw archive.",**{s:[] for s in SECTIONS}}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command",choices=["new-plan","preview","apply"])
    parser.add_argument("--archive",required=True)
    parser.add_argument("--plan",required=True)
    parser.add_argument("--host",default="127.0.0.1")
    parser.add_argument("--port",type=int,default=3306)
    parser.add_argument("--user")
    parser.add_argument("--actor",type=int)
    parser.add_argument("--files-root")
    parser.add_argument("--isolated-trial",action="store_true")
    parser.add_argument("--confirm-plan-sha256")
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
    if not any(plan.get(s) for s in SECTIONS):
        parser.error("Empty reviewed plan")
    plan_hash=sha(plan)
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
        c.execute("SELECT COUNT(*) AS n FROM erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id WHERE i.id=%s AND i.status='active' AND r.code='admin'",(args.actor,))
        if c.fetchone()["n"]!=1:
            raise ValueError("A pre-existing active target admin is required")
        migration=Migration(conn,root,manifest,hash,plan,storage,args.command=="apply")
        migration.actor=args.actor
        summary=migration.run()
        if args.command=="apply":
            migration.execute("INSERT INTO erp_legacy_migration_run(source_namespace,archive_sha256,decision_sha256,summary_json) VALUES(%s,%s,%s,%s) ON DUPLICATE KEY UPDATE id=id",(migration.ns,hash,plan_hash,canonical(summary)))
            commit_attempted=True
            conn.commit()
        else:
            conn.rollback()
        print(json.dumps({"mode":args.command,"planSha256":plan_hash,**summary},ensure_ascii=False,indent=2))
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
