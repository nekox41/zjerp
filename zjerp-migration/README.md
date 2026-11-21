# ZJERP Migration Kernel (development prototype)

This module is an internal ERP migration kernel prototype. The SQL dump parser, source inspector and manifest preflight are developer/test utilities only; they are not the final operator workflow. The product requirement is an admin-only visual migration center embedded in the ERP Web UI (see [Issue #7](https://github.com/nekox41/zjerp_backend/issues/7)). The deployed ERP must own uploads, persistent jobs, scanning, mapping review, dry-run, execution, recovery and reconciliation. Operators must not be asked to run a separate Java command, SSH into the host or execute SQL manually.

The examples below are for developers validating parser behavior in an isolated environment. They do not constitute a supported production migration procedure.

## Build

From the repository root:

```bash
mvn -pl zjerp-migration -am clean package
```

The executable archive is produced under `zjerp-migration/target/`.

## Developer-only: inspect a MySQL source

Create a dedicated database account with only the minimum read privileges required to inspect schema and count rows (for example, SELECT and SHOW VIEW). Do not use root or an application account with write permissions.

Linux/macOS:

```bash
export ZJERP_SOURCE_DB_PASSWORD='read-only-password'
java -jar zjerp-migration/target/zjerp-migration-3.8.9.jar \
  --url 'jdbc:mysql://127.0.0.1:3306/server?useSSL=true' \
  --user 'zjerp_reader' \
  --database 'server' \
  --output './migration-source-manifest.json'
```

PowerShell:

```powershell
$env:ZJERP_SOURCE_DB_PASSWORD = 'read-only-password'
java -jar .\\zjerp-migration\\target\\zjerp-migration-3.8.9.jar `
  --url 'jdbc:mysql://127.0.0.1:3306/server?useSSL=true' `
  --user 'zjerp_reader' `
  --database 'server' `
  --output '.\\migration-source-manifest.json'
```

The password is read from the environment variable and is never accepted as a command-line argument. Do not put credentials in the JDBC URL.

## Developer-only: inspect an existing SQL dump

```bash
java -jar zjerp-migration/target/zjerp-migration-3.8.9.jar \
  --dump \
  --file './zjerp_server_20261121_155818.sql' \
  --output './migration-source-manifest.json'
```

This mode parses MySQL DDL and INSERT tuple boundaries without executing SQL. It records table/column/key metadata and exact INSERT row counts; it does not decode or include business field values in the manifest. Keep the original dump local and protected because the source file itself may contain credentials and personal data.

## Developer-only: compare a source manifest with the reviewed production baseline

After creating a manifest with `--dump` or the live read-only inspector, run:

```bash
java -jar zjerp-migration/target/zjerp-migration-3.8.9.jar \
  --preflight \
  --manifest './migration-source-manifest.json' \
  --baseline './zjerp-migration/src/main/resources/baselines/production-2026-11-21.json' \
  --output './migration-preflight.json'
```

The preflight checks the 68 reviewed table names, expected row counts and primary keys on business tables. Missing tables or required primary keys block the preflight; row-count drift and additional tables are reported for review. A successful result is still `review_required`, never an automatic migration approval. It does not inspect row values, infer relationships, or write to the source/target database.

## Output and safety boundary

The manifest contains:
- database product/version and catalog name;
- table names and exact row counts;
- column names, SQL types, lengths, scales and nullability;
- primary-key and imported foreign-key metadata.

It does **not** read business row values, password hashes, personal information, file contents or application secrets. It does not run DDL/DML, and it does not modify the source database.

The tool sets the JDBC connection read-only and uses a repeatable-read transaction. This is defense in depth, not a replacement for a server-enforced read-only database account. The source account must have no INSERT, UPDATE, DELETE, CREATE, ALTER, DROP or administrative privileges.

The manifest itself can reveal internal schema names and should be stored with migration artifacts under restricted access. Review and redact before sharing.

## Current scope

- [x] Read-only MySQL schema inspection
- [x] Exact table row counts
- [x] Column, primary-key and foreign-key metadata
- [x] JSON source manifest
- [x] SQL dump schema/row-count manifest parser
- [x] Reviewed source-manifest structural preflight (table set, row-count drift, required PKs)\n- [ ] Row-level data-quality rule registry
- [ ] Dry-run target mapping and reconciliation
- [ ] Idempotent import runner
- [ ] Attachment storage scanner and hash verification

This is the first safe foundation only. Do not use it as a data migration command.