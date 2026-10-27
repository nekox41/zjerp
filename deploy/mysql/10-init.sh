#!/bin/sh
set -eu

# 官方镜像仅在 /var/lib/mysql 为空时运行本脚本。
if [ "${BOOTSTRAP_DATABASE:-true}" != "true" ]; then
    echo "Skipping schema initialization; restore the existing database before starting backend."
    exit 0
fi

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
for script in ry_20250417.sql quartz.sql business_schema.sql sys_file.sql; do
    echo "Initializing $script"
    mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE" < "/opt/zjerp/sql/$script"
done

# 原始框架种子包含定时演示任务，新部署时先暂停，避免自动执行示例。
mysql -uroot "$MYSQL_DATABASE" -e "UPDATE sys_job SET status = '1';"
