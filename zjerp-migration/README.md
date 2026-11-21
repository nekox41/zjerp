# ZJERP Migration Kernel (development prototype)

This module is an internal ERP migration kernel prototype. The SQL dump parser, source inspector and manifest preflight are developer/test utilities only; they are not the final operator workflow. The product requirement is an admin-only visual migration center embedded in the ERP Web UI (see [Issue #7](https://github.com/nekox41/zjerp_backend/issues/7)). The deployed ERP must own uploads, persistent jobs, scanning, mapping review, dry-run, execution, recovery and reconciliation. Operators must not be asked to run a separate Java command, SSH into the host or execute SQL manually.

The module is packaged as a normal library inside `zjerp-admin`; it is not a standalone executable JAR. Developer validation should use the module tests from the repository root:\n\n```bash\nmvn -pl zjerp-migration -am test\n```\n\nThe static command entry points are retained only for local IDE/debug use with the Maven runtime classpath. They are not supported operator commands and are not part of deployment documentation.

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

## Developer-only inspection modes\n\nThe module retains offline dump parsing, read-only database inspection and manifest preflight entry points for unit tests and local diagnostics. They are not exposed as deployment commands. The embedded admin UI will orchestrate these capabilities through authenticated backend services.\n\n## Output and safety boundary

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