#!/bin/sh
set -eu
umask 077
dest="$HOME/zjerp-migration/final"
mkdir -p "$dest"
test ! -e "$dest/database.sql"
cd /home/aa/Codes/zjerp
cp docker-compose.yml "$dest/compose-A.yml"
rollback_on_failure() {
    result=$?
    if [ "$result" -ne 0 ]; then
        echo 'Final backup failed; resuming original A application'
        docker compose start backend front || true
    fi
    exit "$result"
}
trap rollback_on_failure EXIT
docker compose stop front backend
date -Iseconds > "$dest/source-stopped-at.txt"
MIGRATION_STAGE=final REQUIRE_NO_EXTERNAL_WRITERS=true python3 "$HOME/zjerp-migration/migration-writers-A.py"
docker exec mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --triggers --events server' > "$dest/database.sql.tmp"
test -s "$dest/database.sql.tmp"
mv "$dest/database.sql.tmp" "$dest/database.sql"
MIGRATION_STAGE=final python3 "$HOME/zjerp-migration/migration-export-floats-A.py"
MIGRATION_STAGE=final python3 "$HOME/zjerp-migration/migration-integrity.py" A baseline
MIGRATION_STAGE=final python3 "$HOME/zjerp-migration/migration-preflight-A.py"
MIGRATION_STAGE=final python3 "$HOME/zjerp-migration/migration-manifest-A.py"
tar -czf "$dest/wbcard.tar.gz" -C /home/aa/app/zjerp/wbcard .
docker inspect --format '{{.Name}} {{.Image}}' mysql backend front redis > "$dest/image-ids-A.txt"
cd "$dest"
sha256sum database.sql exact-floating-values.sql wbcard.tar.gz upload-manifest.json table-integrity-A-baseline.json > SHA256SUMS
sha256sum -c SHA256SUMS
echo 'Final source database and manifest ready; A application remains stopped'
