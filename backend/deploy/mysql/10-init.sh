#!/bin/bash
set -e
if [ "${BOOTSTRAP_DATABASE:-true}" = true ]; then
    MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD" bash /opt/zjerp/scripts/init-database.sh
else
    echo 'Bootstrap disabled. Restore and verify an existing database before starting backend.'
fi
