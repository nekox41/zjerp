#!/bin/sh
set -eu
umask 077
dest="$HOME/zjerp-migration/rehearsal"
mkdir -p "$dest"
cp /home/aa/Codes/zjerp/docker-compose.yml "$dest/compose-A.yml"
docker exec mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --triggers --events server' > "$dest/database.sql"
test -s "$dest/database.sql"
tar -czf "$dest/wbcard.tar.gz" -C /home/aa/app/zjerp/wbcard .
tar -czf "$dest/upload.tar.gz" -C /home/aa/app/zjerp/upload .
docker image save -o "$dest/application-images-A.tar" aliceresia/zjerp-backend:latest aliceresia/zjerp-frontend:latest
cd "$dest"
sha256sum database.sql wbcard.tar.gz upload.tar.gz application-images-A.tar > SHA256SUMS
sha256sum -c SHA256SUMS
