# ERP Next — isolated schema foundation

This directory is the **new ERP target database**, not the existing RuoYi/legacy database. It does not start the old ERP application and it is not yet a usable ERP release.

## Start an isolated schema

Use Docker Compose v2 and Docker. Set **unique, non-production** credentials:

```bash
export ERP_NEXT_DB_ROOT_PASSWORD='<unique-local-root-password>'
export ERP_NEXT_DB_PASSWORD='<unique-local-user-password>'
docker compose -f erp-next/compose.yaml up -d erp-next-mysql
docker compose -f erp-next/compose.yaml run --rm erp-next-schema
```

Running the migration command a second time must succeed without reapplying V1. The CI workflow checks this, the Flyway version history, and eight platform tables. This stack has no published MySQL port and uses a dedicated named volume.

To stop while retaining data:

```bash
docker compose -f erp-next/compose.yaml down
```

To destroy **only the isolated sandbox volume** (irreversible):

```bash
docker compose -f erp-next/compose.yaml down -v
```

Do not run `down -v` on production.

## Safety and compatibility

- This compose file creates a new database named `erp_next`. The old `server` database is never mounted, connected to, or modified.
- Flyway `clean` is disabled and `baselineOnMigrate` is false. Existing nonempty schemas cannot be silently adopted.
- No default admin account, shared password, or credential is seeded.
- V1 defines only organization, identity, role, identity-role, employee, audit, file object, and file reference tables. **No API, authentication, file upload, or production deployment is provided yet.**
- User accounts and employee records are distinct and may exist independently. The nullable one-to-one link is explicit.
- File metadata does not make the object publicly accessible; future application services must authorize downloads and business references.
- `owner_type/owner_id` is a polymorphic reference; its integrity must be enforced by the owning application service and tested when business domains are added.
- MySQL 8.4 is required by the Compose sandbox. Schema history is maintained by Flyway.
- Backup and restore must be verified before any production use. MySQL DDL is not generally transactionally reversible; failed migration recovery requires a backup, not a blind retry.
- Before adding business tables, review V1 keys, status vocabulary, access control, and retention policies.

## Next

Implement identity, role authorization, audit and file services against this schema, then build the first customer/contract/project business slice. Legacy SQL import and reconciliation remain a separate pre-launch gate.
