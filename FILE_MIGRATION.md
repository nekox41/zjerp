# 文件系统迁移步骤

目标：把合同、报告正文、报告附件、维保卡中分隔保存的路径，拆分为 `sys_file` 元数据及业务关联。不会把文件 ID 列表再写回业务表。

## 1. 选择迁移方式

| 情况 | 使用方式 | 结果 |
| --- | --- | --- |
| 只有旧数据库，缺少原文件 | 本文第 3 节：仅数据库迁移 | 原 URL / 路径保留，哈希、大小、MIME 为 NULL，标记未核实 |
| 旧数据库及原文件都可用，旧附件列尚未清空 | 本文第 4 节：带文件迁移 | 原文件复制到标准路径，计算真实元数据，成功后清空旧列 |
| 已完成仅数据库迁移，之后找回原文件 | 本文第 5 节：恢复历史文件 | 按旧目录层级恢复后可以访问；不会自动重做已经清空的旧列 |

涉及的字段和业务类型：

| 表 / 旧字段 | biz_type | biz_id |
| --- | --- | --- |
| zj_contract.contract_file | contract | 合同 id |
| zj_report.report_file | report | 报告 id |
| zj_report.report_attach | report_attach | 报告 id |
| zj_wbcard.file | wbcard | 维保卡 id |

`sys_file` 记录原名、相对路径、URL、SHA-256、字节数、MIME、扩展名、创建信息、业务关联和删除状态。新上传使用 `files/<hash前两位>/<UUID>.<小写扩展名>`，原始特殊字符不会进入新磁盘路径。

## 2. 迁移前备份和停写

先确认正在操作目标数据库，准备可恢复备份。Compose 环境从后端仓库运行：

```bash
mkdir -p backups
chmod 700 backups
docker compose stop frontend backend
docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > backups/before-file-migration.sql
chmod 600 backups/before-file-migration.sql
# 有原文件时同时备份上传卷；下面命令不启动后端。
docker compose run --rm --no-deps --entrypoint tar backend -czf - -C /app/upload . > backups/profile-before-file-migration.tar.gz
chmod 600 backups/profile-before-file-migration.tar.gz
```

旧应用及任何会写业务表的实例都应停止。仅数据库迁移脚本自身也会备份 `sys_file`（若已存在）、四类业务表和 `sys_menu`，但不能替代整库及原文件备份。备份放在 Git 之外或已忽略目录，勿提交生产记录。

## 3. 仅迁移数据库，不处理磁盘文件

### Compose 环境

MySQL 容器已启动；宿主机需要 Python 3，不需要安装 MySQL 客户端，也不需要开放 MySQL 端口。数据库名按 `.env` 的 `MYSQL_DATABASE` 调整：

```bash
# 预览，不写入数据库。
python3 scripts/migrate-file-metadata.py --compose --database server --backup-dir ./backups
# 执行；先备份、兼容升级，再迁移并核对。
python3 scripts/migrate-file-metadata.py --compose --database server --backup-dir ./backups --apply
```

### 非 Compose 环境

宿主机需要 Python 3、`mysql` 和 `mysqldump`。创建权限 600 的客户端配置文件，勿把密码放入 shell 参数或 Git：

```ini
[client]
protocol=TCP
host=127.0.0.1
port=3306
user=YOUR_MIGRATION_USER
password="YOUR_PASSWORD"
```

```bash
chmod 600 /private/path/mysql-client.cnf
python3 scripts/migrate-file-metadata.py --defaults-file /private/path/mysql-client.cnf --database server --backup-dir /private/path/backups
python3 scripts/migrate-file-metadata.py --defaults-file /private/path/mysql-client.cnf --database server --backup-dir /private/path/backups --apply
```

迁移账户需要读取业务表、创建/修改文件表、插入元数据及更新旧字段/菜单的权限。脚本的具体行为：

1. 读取固定白名单的旧字段，兼容逗号和分号分隔，按每组去重。
2. 备份涉及表；旧库没有 `sys_file` 时创建，有则执行 `sql/sys_file_metadata_only_upgrade.sql` 的字段兼容升级。
3. 允许未知哈希和大小使用 NULL；扩大元数据字段容量。
4. 在一个数据库事务中按业务类型、ID 和原 URL 去重登记，保留原 URL、相对路径、原创建信息。没有经过验证的内容元数据为 NULL，`remark` 标记 `legacy_metadata_only; content_not_verified`。
5. 清空成功迁移的旧附件字段。插入和更新共享事务，SQL 出错会回滚数据操作；DDL 兼容升级是 MySQL 独立提交的结构操作。
6. 按业务类型、ID、URL 逐条核对，并输出 `result.json`。再次预览应显示 groups=0、references=0。

生成的备份目录含 `before-migration.sql`、`migration.sql`、`result.json`，权限受限，不进入仓库。历史原名如果自身包含未编码的分隔符，旧串可能无法无歧义拆分，应先按原备份人工确认；数据库迁移不验证磁盘内容存在性。

## 4. 原文件齐全时的迁移

仅适用于旧附件列仍有路径的记录。停止旧版本写入，备份完成后：

1. 把完整旧文件根目录恢复到新环境的 `ruoyi.profile`。Compose 中是 `/app/upload`，具体恢复命令见 [部署说明](DEPLOYMENT.md)。
2. 在旧业务库执行 `sql/sys_file.sql`。若文件表来自早期原型且字段长度不同，先执行 `sql/sys_file_metadata_only_upgrade.sql`。这些 SQL 不包含旧业务表 DROP，也不替代完整迁移。
3. 启动新后端和前端，以管理员登录，在“系统管理 → 文件管理”点击“迁移旧附件”；对应接口为 `POST /system/file/migrate`。
4. 观察 `success`、`failed`、`errors`，修复缺失源文件后重试。

每个附件组独立事务：整组成功后才清空对应旧列；任何文件失败则保留旧字段，回滚该组新元数据并清理本次新文件。原磁盘文件保留。迁移只处理本地 `/profile/` 资源，拒绝路径越界，不从外部服务器下载 URL。

有真实文件的迁移会计算 SHA-256 和大小，复制到新的标准路径。旧文件不删除，数据库旧列不删除。普通业务保存也会把仍留在旧列中的本地附件登记到统一文件表。

## 5. 已仅迁移数据库，之后恢复原文件

数据库记录已经保留原 URL，因此先按原目录层级恢复源文件，即可恢复这些历史 URL 的访问。当前原文件仍使用历史名称，不会被仅数据库迁移重新命名。

**不要再次点击旧字段迁移来期待补齐哈希：旧列已经清空，接口不会扫描待核实的 sys_file 记录。** 当前未提供批量扫描已迁移记录并标准化复制/回填哈希的命令；若需要完成这一步，应另行实现并验证专用回填流程。新上传从一开始就使用标准路径和完整元数据。

## 6. 核对与恢复服务

可在 MySQL 客户端运行：

```sql
SELECT biz_type, COUNT(*) FROM sys_file WHERE del_flag = '0' GROUP BY biz_type;
SELECT COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file), '') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file), '') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach), '') <> '';
SELECT COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file), '') <> '';
SELECT COUNT(*) FROM sys_file WHERE del_flag = '0' AND (file_hash IS NULL OR file_size IS NULL);
```

前四项旧字段查询迁移后应为 0；最后一项在仅数据库迁移后非零是预期状态。再检查合同、报告和维保卡详情仍能回显附件、文件管理列表数量与报告一致，新上传及删除/取消编辑的行为正常。

```bash
docker compose up -d --build
docker compose ps
```

本地当前开发库于 2026-10-27 完成仅数据库迁移：1,104 组附件、1,489 条引用，四类旧字段待迁移数量均为 0。原文件缺失，因此该开发库历史下载尚不可用；这不表示其他部署数据库也已迁移。

## 7. 回滚

停写前端和后端，确认备份属于当前数据库及迁移前时间点后恢复：

```bash
docker compose stop frontend backend
docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE"' < backups/before-file-migration.sql
```

还原对应文件备份并部署与该备份兼容的应用版本，再启动服务。仅数据库迁移没有改动原文件；有文件的迁移保留旧文件，新副本暂留，核对无引用后才另行清理。若脚本迁移前文件表尚不存在，其表级备份不含新建的 `sys_file`，完整回滚应使用整库快照或在确认后单独移除新建表，不能只恢复部分表就继续使用新版本。

数据库、上传目录和应用版本必须配套回滚；迁移后的业务新增/修改应先另行备份，不能直接用旧快照覆盖。
