#!/usr/bin/env python3
"""只迁移数据库附件引用，不读取/复制/删除磁盘文件。使用 mysql/mysqldump，无 Python 第三方依赖。"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit

MAPPINGS = [
    ("zj_contract", "contract_file", "contract"),
    ("zj_report", "report_file", "report"),
    ("zj_report", "report_attach", "report_attach"),
    ("zj_wbcard", "file", "wbcard"),
]


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    connection = parser.add_mutually_exclusive_group(required=True)
    connection.add_argument("--defaults-file", help="权限为600的MySQL客户端配置文件")
    connection.add_argument("--compose", action="store_true", help="通过当前Compose项目的mysql容器执行客户端")
    parser.add_argument("--database", required=True)
    parser.add_argument("--backup-dir", required=True)
    parser.add_argument("--apply", action="store_true", help="默认只预览；显式传入才迁移")
    args = parser.parse_args()
    if args.compose:
        client_prefix = ["docker", "compose", "exec", "-T", "mysql", "sh", "-c",
                         'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec "$@"', "sh"]
        client_options = ["-uroot"]
    else:
        if Path(args.defaults_file).stat().st_mode & 0o077:
            parser.error("MySQL 客户端配置文件权限必须为600或更严格")
        client_prefix = []
        client_options = ["--defaults-extra-file=" + str(Path(args.defaults_file).resolve())]
    base = client_prefix + ["mysql"] + client_options + [
        "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names", args.database]

    def query(sql):
        result = subprocess.run(base, input=sql, capture_output=True, text=True, check=True)
        return result.stdout

    rows = []
    for table, column, kind in MAPPINGS:
        sql = ("SELECT JSON_OBJECT('id',id,'urls'," + column
               + ",'creator',create_by,'created',create_time) FROM " + table
               + " WHERE " + column + " IS NOT NULL AND TRIM(" + column + ") <> ''; ")
        for line in query(sql).splitlines():
            row = json.loads(line)
            row.update(table=table, column=column, kind=kind)
            rows.append(row)
    statements = ["START TRANSACTION;"]
    expected = []
    for row in rows:
        urls = list(dict.fromkeys(url.strip() for url in re.split("[,;]", row["urls"]) if url.strip()))
        if not urls:
            raise ValueError("附件组没有有效路径: " + str(row["id"]))
        guard = ("b.id=" + literal(row["id"]) + " AND BINARY b." + row["column"]
                 + "=BINARY " + literal(row["urls"]))
        for url in urls:
            resource = urlsplit(url).path if url.startswith(("http://", "https://")) else url
            if not resource.startswith("/profile/"):
                raise ValueError("不支持的本地文件引用: " + url)
            relative = resource[len("/profile/"):]
            if relative.startswith("/") or ".." in relative.replace("\\", "/").split("/"):
                raise ValueError("路径越界: " + url)
            name = unquote(relative.rsplit("/", 1)[-1])
            if not name or len(name) > 512:
                raise ValueError("文件名为空或过长: " + url)
            ext = name.rsplit(".", 1)[-1].lower() if "." in name else None
            if ext and not re.fullmatch("[a-z0-9]{1,16}", ext):
                ext = None
            values = [literal(name), literal(relative), literal(url), "NULL", "NULL", "NULL",
                      literal(ext), literal(row["kind"]), literal(row["id"]), "'0'",
                      literal((row["creator"] or "migration")[:64]), literal(row["created"]),
                      "'legacy_metadata_only; content_not_verified'"]
            statements.append("INSERT INTO sys_file (file_name,file_path,file_url,file_hash,file_size,file_type,file_ext,biz_type,biz_id,del_flag,create_by,create_time,remark) SELECT "
                              + ",".join(values) + " FROM " + row["table"] + " b WHERE " + guard
                              + " AND NOT EXISTS (SELECT 1 FROM sys_file f WHERE f.biz_type="
                              + literal(row["kind"]) + " AND f.biz_id=" + literal(row["id"])
                              + " AND BINARY f.file_url=BINARY " + literal(url) + " AND f.del_flag='0');")
            expected.append({"type": row["kind"], "id": row["id"], "url": url})
        statements.append("UPDATE " + row["table"] + " b SET b." + row["column"] + "=NULL WHERE " + guard + ";")
    statements.append("COMMIT;")
    summary = {"mode": "metadata-only", "groups": len(rows), "references": len(expected), "applied": False}
    if not args.apply:
        print(json.dumps(summary, ensure_ascii=False))
        return
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_dir = Path(args.backup_dir).expanduser().resolve() / (stamp + "-metadata-only")
    backup_dir.mkdir(mode=0o700, parents=True)
    os.chmod(backup_dir, 0o700)
    available_tables = set(query("SHOW TABLES;").splitlines())
    backup_tables = [table for table in ["sys_file", "zj_contract", "zj_report", "zj_wbcard", "sys_menu"] if table in available_tables]
    backup = backup_dir / "before-migration.sql"
    with backup.open("w") as output:
        os.chmod(backup, 0o600)
        subprocess.run(client_prefix + ["mysqldump"] + client_options + [
                        "--default-character-set=utf8mb4", "--single-transaction", "--no-tablespaces",
                        "--set-gtid-purged=OFF", "--hex-blob", args.database,
                        *backup_tables],
                       stdout=output, stderr=subprocess.PIPE, text=True, check=True)
    root = Path(__file__).resolve().parents[1]
    if "sys_file" in available_tables:
        query((root / "sql/sys_file_metadata_only_upgrade.sql").read_text())
    query((root / "sql/sys_file.sql").read_text())
    sql = "\n".join(statements)
    plan = backup_dir / "migration.sql"
    plan.write_text(sql)
    os.chmod(plan, 0o600)
    query(sql)
    # 逐条核对业务类型、业务ID及URL，无需依赖原磁盘文件。
    active = [json.loads(line) for line in query("SELECT JSON_OBJECT('type',biz_type,'id',biz_id,'url',file_url) FROM sys_file WHERE del_flag='0';").splitlines()]
    actual = {(item["type"], item["id"], item["url"]) for item in active}
    missing = [item for item in expected if (item["type"], item["id"], item["url"]) not in actual]
    remaining = {}
    for table, column, kind in MAPPINGS:
        remaining[kind] = int(query("SELECT COUNT(*) FROM " + table + " WHERE COALESCE(TRIM(" + column + "),'') <> ''; ").strip())
    summary.update(applied=True, verified=len(expected)-len(missing), missing=missing,
                   remaining_legacy_groups=remaining, backup_dir=str(backup_dir),
                   total_active_metadata=len(active))
    report = backup_dir / "result.json"
    report.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    os.chmod(report, 0o600)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if missing or any(remaining.values()):
        raise SystemExit("迁移后核对发现未完成项，请检查报告")


if __name__ == "__main__":
    main()
