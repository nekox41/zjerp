#!/usr/bin/env python3
"""Offline pure-function tests; no database, network, Actions, or real customer data."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
import decimal

def load(name,file):
    s=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    m=importlib.util.module_from_spec(s)
    s.loader.exec_module(m)
    return m

exporter=load("archive_test","erp-next-legacy-export.py")
importer=load("import_test","erp-next-legacy-import.py")

class MigrationValidationTest(unittest.TestCase):
    def test_money_and_ids(self):
        self.assertEqual(importer.money({"$type":"decimal","value":"10.5000"}),decimal.Decimal("10.50"))
        for value in (None,True,"0.001","NaN","-1","10000000000000000"):
            with self.assertRaises((ValueError,decimal.InvalidOperation)):
                importer.money(value)
        for value in (False,"1 OR 1=1","-1","0","01"):
            with self.assertRaises(ValueError):
                importer.ident(value)
    def test_archive_integrity(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/"tables").mkdir()
            (root/"files").mkdir()
            files={"tables/1.sql":"CREATE TABLE example(id BIGINT);\n","tables/1.jsonl":"{\"id\":1}\n","tables/1.raw.jsonl":"{\"id\":\"MQ==\"}\n","files/original.txt":"原始数据"}
            for path,value in files.items():
                exporter.secure_write(root/path,value)
            manifest={"format":"erp-legacy-archive-v1","sourceSchema":"server_migration","sourceNamespace":"test","tables":[{"table":"example","kind":"BASE TABLE","ddl":"tables/1.sql","ddlSha256":exporter.digest(root/"tables/1.sql"),"data":"tables/1.jsonl","sha256":exporter.digest(root/"tables/1.jsonl"),"rawData":"tables/1.raw.jsonl","rawSha256":exporter.digest(root/"tables/1.raw.jsonl"),"rows":1}],"files":[{"path":"original.txt","sha256":exporter.digest(root/"files/original.txt"),"size":(root/"files/original.txt").stat().st_size}],"directories":[]}
            exporter.secure_write(root/"manifest.json",exporter.canonical(manifest)+"\n")
            exporter.secure_write(root/"manifest.sha256",exporter.digest(root/"manifest.json")+"\n")
            checked,checksum=exporter.verify(root)
            self.assertEqual(checked["tables"][0]["rows"],1)
            plan=importer.blank_plan(checked,checksum)
            self.assertEqual(plan["archiveSha256"],checksum)
            with self.assertRaises(ValueError):
                exporter.safe_path(root,"../outside")
            (root/"files/original.txt").write_text("tampered",encoding="utf-8")
            with self.assertRaises(ValueError):
                exporter.verify(root)
    def test_raw_and_typed_values(self):
        self.assertEqual(exporter.encode(bytearray(b"\x00\xff"))["base64"],"AP8=")
        self.assertEqual(exporter.encode(decimal.Decimal("1.2345678901234567"))["value"],"1.2345678901234567")
        self.assertIsNone(exporter.encode(None))

if __name__=="__main__":
    unittest.main()
