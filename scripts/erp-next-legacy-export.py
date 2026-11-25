#!/usr/bin/env python3
"""Read-only, offline legacy database and file preservation. No target DB connection."""
import argparse
import base64
import datetime as dt
import decimal
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys

def canonical(obj):
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)

def encode(value):
    if isinstance(value, (bytes,bytearray,memoryview)):
        value=bytes(value)
        return {"$type": "bytes", "base64": base64.b64encode(value).decode("ascii")}
    if isinstance(value, decimal.Decimal):
        return {"$type": "decimal", "value": str(value)}
    if isinstance(value, (dt.datetime, dt.date, dt.time)):
        return {"$type": type(value).__name__, "value": value.isoformat()}
    if isinstance(value, dt.timedelta):
        return {"$type": "timedelta", "days": value.days, "seconds": value.seconds, "microseconds": value.microseconds}
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    raise TypeError("Unsupported SQL value: " + type(value).__name__)

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def secure_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as f:
        f.write(text)

def quote_ident(s):
    if not s or "\x00" in s:
        raise ValueError("invalid identifier")
    return "\x60" + s.replace("\x60", "\x60\x60") + "\x60"

def safe_path(root, relative):
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise ValueError("invalid archive path")
    if Path(relative).is_absolute() or any(part in ("..",".","") for part in relative.split("/")):
        raise ValueError("unsafe archive component")
    p=root
    for part in relative.split("/"):
        p=p/part
        if p.is_symlink():
            raise ValueError("unsafe archive symlink")
    if not p.resolve().is_relative_to(root.resolve()):
        raise ValueError("unsafe archive path")
    return p

def connect_source(args):
    import mysql.connector
    if not re.fullmatch(r"server(?:_[A-Za-z0-9_]{1,56})?", args.schema) or (args.schema=="server" and not args.confirm_server_read_only):
        raise ValueError("Use an offline server_* copy or explicitly confirm a frozen read-only server source")
    return mysql.connector.connect(host=args.host, port=args.port, database=args.schema, user=args.user,
        password=os.environ.get("ERP_LEGACY_DB_PASSWORD") or getpass.getpass("Read-only source password: "),
        connection_timeout=8, autocommit=False, charset="utf8mb4")

def export(args):
    source_files = Path(args.files).resolve(strict=True)
    output = Path(args.output).absolute()
    if not source_files.is_dir() or output.resolve().is_relative_to(source_files) or source_files.is_relative_to(output.resolve()):
        raise ValueError("Source file tree and archive output must be separate directories")
    partial = output.with_name(output.name + ".partial")
    if output.exists() or partial.exists():
        raise ValueError("Output or partial archive already exists; never overwrite")
    partial.mkdir(mode=0o700, parents=True)
    (partial / "tables").mkdir()
    (partial / "files").mkdir()
    manifest = {"format": "erp-legacy-archive-v1", "sourceSchema": args.schema, "sourceNamespace": args.namespace,
                "createdUtc": dt.datetime.now(dt.timezone.utc).isoformat(), "tables": [], "files": [], "directories": []}
    conn = None
    try:
        conn = connect_source(args)
        cur = conn.cursor()
        cur.execute("SET SESSION time_zone = '+00:00'")
        conn.start_transaction(consistent_snapshot=True, readonly=True, isolation_level="REPEATABLE READ")
        cur.execute("SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME", (args.schema,))
        tables = cur.fetchall()
        if not tables:
            raise ValueError("No visible tables; verify source database and grants")
        for n, (name, kind, engine) in enumerate(tables, 1):
            print("Preserving table/view:", name, file=sys.stderr)
            cur.execute("SHOW CREATE " + ("VIEW " if kind == "VIEW" else "TABLE ") + quote_ident(name))
            definition = cur.fetchone()[1]
            cur.fetchall()
            prefix = "tables/%05d" % n
            ddl = prefix + ".sql"
            secure_write(partial / ddl, definition + ";\n")
            entry = {"table": name, "kind": kind, "engine": engine, "ddl": ddl, "ddlSha256": digest(partial / ddl)}
            if kind == "BASE TABLE":
                path = prefix + ".jsonl"
                cur.execute("SELECT * FROM " + quote_ident(name))
                cols = list(cur.column_names)
                count = 0
                with (partial / path).open("x", encoding="utf-8", newline="\n") as f:
                    for row in cur:
                        f.write(canonical(dict(zip(cols, (encode(v) for v in row)))) + "\n")
                        count += 1
                entry.update(data=path, sha256=digest(partial / path), rows=count, columns=cols)
                # Preserve raw wire bytes as well; typed conversion may turn legacy zero dates into NULL.
                raw_path=prefix+".raw.jsonl"
                raw_cur=conn.cursor(raw=True)
                try:
                    raw_cur.execute("SELECT * FROM "+quote_ident(name))
                    raw_count=0
                    with (partial/raw_path).open("x",encoding="utf-8",newline="\n") as f:
                        for raw_row in raw_cur:
                            encoded=[None if v is None else base64.b64encode(bytes(v)).decode("ascii") for v in raw_row]
                            f.write(canonical(dict(zip(cols,encoded)))+"\n")
                            raw_count+=1
                    if raw_count!=count:
                        raise ValueError("Raw and typed row counts differ: "+name)
                finally:
                    raw_cur.close()
                entry.update(rawData=raw_path,rawSha256=digest(partial/raw_path))
            manifest["tables"].append(entry)
        conn.rollback()
        # The source copy and its file directory must remain offline/unchanged while exporting.
        for current, dirs, names in os.walk(source_files, followlinks=False):
            dirs.sort(); names.sort()
            current = Path(current)
            for d in dirs:
                src = current / d
                if src.is_symlink():
                    raise ValueError("Symlink in source tree; resolve explicitly before export")
                relative = src.relative_to(source_files).as_posix()
                safe_path(partial, "files/" + relative).mkdir(parents=True, exist_ok=True)
                manifest["directories"].append(relative)
            for filename in names:
                src = current / filename
                if src.is_symlink() or not src.is_file():
                    raise ValueError("Unsupported nonregular source file")
                relative = src.relative_to(source_files).as_posix()
                dst = safe_path(partial, "files/" + relative)
                dst.parent.mkdir(parents=True, exist_ok=True)
                before = src.stat()
                with src.open("rb") as fin, dst.open("xb") as fout:
                    shutil.copyfileobj(fin, fout, 1024 * 1024)
                after = src.stat()
                if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
                    raise ValueError("Source file changed during export: " + relative)
                copied_hash = digest(dst)
                if copied_hash != digest(src):
                    raise ValueError("Copied file hash mismatch: " + relative)
                manifest["files"].append({"path": relative, "size": dst.stat().st_size, "sha256": copied_hash})
        secure_write(partial / "manifest.json", canonical(manifest) + "\n")
        secure_write(partial / "manifest.sha256", digest(partial / "manifest.json") + "\n")
        verify(partial)
        partial.rename(output)
        print("Verified archive created:", output)
    finally:
        if conn:
            conn.close()

def verify(root):
    root = Path(root).resolve(strict=True)
    mp = safe_path(root,"manifest.json")
    actual = digest(mp)
    if actual != safe_path(root,"manifest.sha256").read_text(encoding="utf-8").strip():
        raise ValueError("Manifest SHA256 mismatch")
    manifest = json.loads(mp.read_text(encoding="utf-8"))
    if manifest.get("format") != "erp-legacy-archive-v1":
        raise ValueError("Unknown archive format")
    if not re.fullmatch(r"server(?:_[A-Za-z0-9_]{1,56})?",str(manifest.get("sourceSchema",""))) or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}",str(manifest.get("sourceNamespace",""))):
        raise ValueError("Invalid archive source identity")
    seen = set()
    expected = {"manifest.json", "manifest.sha256"}
    def track(relative):
        if relative in expected:
            raise ValueError("Duplicate archive member: "+relative)
        expected.add(relative)
    for table in manifest["tables"]:
        name = table["table"]
        if name in seen:
            raise ValueError("Duplicate table in manifest")
        seen.add(name)
        if digest(safe_path(root, table["ddl"])) != table["ddlSha256"]:
            raise ValueError("DDL checksum mismatch: " + name)
        track(table["ddl"])
        if table["kind"] == "BASE TABLE":
            p = safe_path(root, table["data"])
            if digest(p) != table["sha256"]:
                raise ValueError("Data checksum mismatch: " + name)
            with p.open("r", encoding="utf-8") as f:
                count = sum(1 for line in f if line.strip())
            if count != table["rows"]:
                raise ValueError("Row count mismatch: " + name)
            track(table["data"])
            if "rawData" not in table:
                raise ValueError("Raw row preservation missing: "+name)
            raw=safe_path(root,table["rawData"])
            if digest(raw)!=table["rawSha256"]:
                raise ValueError("Raw row checksum mismatch: "+name)
            with raw.open("r",encoding="utf-8") as f:
                if sum(1 for line in f if line.strip())!=table["rows"]:
                    raise ValueError("Raw row count mismatch: "+name)
            track(table["rawData"])
    file_names = set()
    for f in manifest["files"]:
        if f["path"] in file_names:
            raise ValueError("Duplicate file in manifest")
        file_names.add(f["path"])
        relative = "files/" + f["path"]
        p = safe_path(root, relative)
        if p.stat().st_size != f["size"] or digest(p) != f["sha256"]:
            raise ValueError("File checksum/size mismatch: " + f["path"])
        track(relative)
    for d in manifest["directories"]:
        if not safe_path(root, "files/" + d).is_dir():
            raise ValueError("Missing archived directory")
    found = set()
    for p in root.rglob("*"):
        if p.is_symlink():
            raise ValueError("Archive symlink is forbidden")
        if p.is_file():
            found.add(p.relative_to(root).as_posix())
        elif not p.is_dir():
            raise ValueError("Archive contains nonregular member")
    if found != expected:
        raise ValueError("Archive contains missing or untracked files")
    return manifest, actual

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("export")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", required=True)
    p.add_argument("--schema", default="server_migration")
    p.add_argument("--confirm-server-read-only", action="store_true", help="Confirm server writes are stopped and attachment copy is consistent")
    p.add_argument("--namespace", required=True, help="Stable identifier of the original installation")
    p.add_argument("--files", required=True, help="Offline complete legacy upload directory")
    p.add_argument("--output", required=True)
    v = sub.add_parser("verify"); v.add_argument("--archive", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    if args.command == "export":
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", args.namespace):
            parser.error("Invalid source namespace")
        export(args)
    else:
        m, sha = verify(args.archive)
        print(canonical({"manifestSha256": sha, "tables": len(m["tables"]), "files": len(m["files"]),
                         "rows": sum(t.get("rows", 0) for t in m["tables"])}))

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("Migration archive failed:", str(e), file=sys.stderr)
        sys.exit(1)
