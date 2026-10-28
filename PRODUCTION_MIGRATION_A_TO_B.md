# 生产环境从机器 A 迁移到机器 B

## 1. 适用范围与交付目标

本方案针对目前的实际部署：机器 A 通过旧 Compose 运行前端、后端、MySQL、Redis；机器 B 是可 SSH 访问的空环境；公司局域网内通过 IP 访问，迁移后使用 B 的新 IP。

目标是保留全部业务数据、账号权限、配置及历史文件，在 B 部署当前优化版，并将四类业务附件迁入统一文件管理。先演练，再安排停写窗口执行最终迁移。本文是操作方案，尚未连接生产机器执行，不代表生产迁移已经完成。

| 对象 | A 上的位置 | 迁移方式 |
| --- | --- | --- |
| MySQL | 外部命名卷 `mysql_data`，容器 `mysql` | 单库逻辑备份并恢复；如有多个业务库，分别列出并备份 |
| 上传文件 | `/home/aa/app/zjerp/upload` → `/app/upload` | 完整归档，保留原有相对目录和文件名 |
| 维保卡模板及字体 | `/home/aa/app/zjerp/wbcard` → `/app/wbcard` | 单独完整归档，在 B 挂载到相同容器路径 |
| 应用配置 | A 的 Compose、应用环境、反向代理等 | 私密备份，按新 Compose 配置映射，不能直接套用旧变量 |
| Redis | 容器 `redis`，旧 Compose 未配置持久化卷 | 默认重建登录/配置缓存；先确认没有额外业务数据 |
| 应用版本 | A 当前运行镜像、B 指定提交构建镜像 | 保存 A 镜像用于回退，记录 B 前后端提交和镜像 ID |

不把 A 的 MySQL 物理目录直接复制给 B；不使用本仓库历史 SQL 代替生产库；不执行 `docker compose down -v`，也不删除 A 的 `mysql_data`。

## 2. 准备清单与迁移边界

正式操作前填写以下记录，放在私密运维目录，不提交 Git：

- A/B SSH 地址、用户、B 的新 IP 和访问端口、旧访问 IP 与端口。
- A 实际 MySQL 版本、字符集/排序规则、数据库名、表引擎及备份账户权限。
- B 使用的数据库镜像版本、前后端 Git 提交、Docker/Compose 版本。
- 上传及模板目录占用、文件数量、数据库大小、B 可用磁盘和迁移耗时。
- 定时任务、第三方接口、邮件/短信、外部写入方，以及停写和恢复负责人。
- 可接受的停写窗口、验收人和回退决定时间。

B 要同时容纳数据库、原附件、标准化后的新附件副本、模板、备份、镜像及临时文件。带文件迁移会保留原文件，因此不能只按原上传目录大小预留空间。

### 2.1 在 A 确认实际部署（只读）

以下命令在 A 的旧 Compose 所在目录执行：

```bash
cd /home/aa/Codes/zjerp
docker compose ps
docker exec -it mysql mysql -uroot -p
```

在交互客户端输入：

```sql
SELECT VERSION();
SHOW DATABASES;
SELECT @@character_set_server, @@collation_server;
-- 将 YOUR_DATABASE 改为实际业务库名。
SELECT ENGINE, COUNT(*) FROM information_schema.tables
WHERE table_schema = 'YOUR_DATABASE' GROUP BY ENGINE;
SELECT table_name, table_rows, data_length, index_length
FROM information_schema.tables WHERE table_schema = 'YOUR_DATABASE';
```

`table_rows` 对 InnoDB 是估计值，只用于容量盘点，正式核对使用 `COUNT(*)`。不要把 `mysql`、`sys` 等系统库当业务库导入 B。

```bash
du -sh /home/aa/app/zjerp/upload /home/aa/app/zjerp/wbcard
df -h
# 结果包含挂载来源和目的地，不输出环境变量中的密码。
docker inspect --format '{{json .Mounts}}' backend
docker inspect --format '{{.Image}}' mysql backend front redis
```

必须核实后端实际 `ruoyi.profile` 就是 `/app/upload`，并确认是否存在上传目录之外的文件依赖、符号链接目标、外部存储和自定义模板目录。不要仅凭 Compose 的挂载声明判断所有文件都已包含。

### 2.2 MySQL 版本门槛

A 的 `mysql:latest` 不能用于确定实际版本。仓库新 Compose 默认 `mysql:8.0`，不能在未知版本的情况下直接恢复。

- 记录 A 的 `SELECT VERSION()` 和镜像 ID；B 必须选择已经验证能恢复该备份、能运行本应用的具体版本。
- 如果 A 高于 B 默认版本，暂停默认 MySQL 8.0 的部署；另建 B 的版本 override，用兼容的版本进行逻辑恢复演练。
- 跨版本恢复的 SQL、字符集/排序规则、认证和存储过程兼容性，以实际恢复结果为准。不能通过随意删除报错 SQL 来宣告迁移成功。
- 以下流程假设已完成兼容性确认。若设置 `compose.override.yaml` 覆盖 MySQL 镜像，后续始终在同一目录运行 Compose，保证使用同一配置。

## 3. A：制作演练备份

演练备份可在低峰期制作，但在线数据库和文件的时间点不一定一致；它只验证恢复流程，不作为正式切换数据。最终迁移必须停写后重新备份。

### 3.1 建立备份目录

```bash
umask 077
MIGRATION_DIR="$HOME/zjerp-migration/rehearsal"
mkdir -p "$MIGRATION_DIR"
chmod 700 "$MIGRATION_DIR"
cp docker-compose.yml "$MIGRATION_DIR/compose-A.yml"
```

私密保存实际使用的 override、应用配置、代理配置；它们可能含凭据，不发送到聊天或提交 Git。确认所有备份命令退出码为 0，不因生成了文件就认为备份成功。

### 3.2 导出业务数据库

替换下面的 `YOUR_DATABASE`。旧容器通过 `MYSQL_ROOT_PASSWORD` 提供凭据，无须在命令中写真实密码：

```bash
DB_NAME=YOUR_DATABASE
docker exec -e MIGRATION_DATABASE="$DB_NAME" mysql sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysqldump -uroot --default-character-set=utf8mb4 \
    --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
    --hex-blob --routines --triggers --events "$MIGRATION_DATABASE"
' > "$MIGRATION_DIR/database.sql"
test -s "$MIGRATION_DIR/database.sql"
chmod 600 "$MIGRATION_DIR/database.sql"
```

若实际 root 密码与容器初始变量不同，此命令会失败，应使用受限权限客户端配置处理。不要把真实密码直接加在 `-p` 后。

该命令不带 `--databases`，避免自动携带 `CREATE DATABASE` / `USE`。仍应检查存储过程、视图、触发器中的库名和 `DEFINER` 是否依赖 A 的账户；需要时编写针对性的兼容修改并记录。禁止导入 A 的 MySQL 系统账户表覆盖 B 的账户。

`--single-transaction` 只能保证事务表快照；存在非事务表时，应在演练中注明，正式备份必须在所有写入和 DDL 停止后执行。备份失败或恢复报错均不得进入切换。

### 3.3 打包文件、模板和旧镜像

```bash
tar -czf "$MIGRATION_DIR/upload.tar.gz" -C /home/aa/app/zjerp/upload .
tar -czf "$MIGRATION_DIR/wbcard.tar.gz" -C /home/aa/app/zjerp/wbcard .
tar -tzf "$MIGRATION_DIR/upload.tar.gz" > "$MIGRATION_DIR/upload-manifest.txt"
tar -tzf "$MIGRATION_DIR/wbcard.tar.gz" > "$MIGRATION_DIR/wbcard-manifest.txt"
```

归档根目录就是目录内容：例如旧 `/profile/upload/2025/a.pdf` 对应恢复后的 `/app/upload/upload/2025/a.pdf`，不能多套一层 `upload` 或 `profile`。

如果目录包含符号链接，tar 默认只保存链接，不保存外部目标；应盘点目标、单独备份并明确恢复方式。标准化迁移拒绝越出上传根目录的路径，不能用越界链接绕过检查。

A 镜像的 `latest` 后续可能变化，至少保存旧应用镜像：

```bash
OLD_BACKEND_IMAGE=$(docker inspect --format '{{.Image}}' backend)
OLD_FRONT_IMAGE=$(docker inspect --format '{{.Image}}' front)
docker image save -o "$MIGRATION_DIR/application-images-A.tar" \
  "$OLD_BACKEND_IMAGE" "$OLD_FRONT_IMAGE"
printf '%s\n%s\n' "$OLD_BACKEND_IMAGE" "$OLD_FRONT_IMAGE" \
  > "$MIGRATION_DIR/application-image-ids-A.txt"
```

记录 MySQL/Redis 镜像 ID，确保回退可继续使用 A 现有容器和卷。是否额外导出这些镜像按本地镜像可用性决定。

### 3.4 校验与传输

```bash
cd "$MIGRATION_DIR"
sha256sum database.sql upload.tar.gz wbcard.tar.gz application-images-A.tar > SHA256SUMS
sha256sum -c SHA256SUMS
# 将 B_USER、B_IP 替换为实际 SSH 信息；B 使用独立私密目录接收。
ssh B_USER@B_IP 'umask 077; mkdir -p ~/zjerp-migration/rehearsal'
scp -p database.sql upload.tar.gz wbcard.tar.gz application-images-A.tar \
  SHA256SUMS B_USER@B_IP:~/zjerp-migration/rehearsal/
```

B 接收后运行 `sha256sum -c SHA256SUMS`。完整 SQL、原文件与原配置备份至少在 A/B 之外再保留一份。清单、失败报告也可能包含客户和文件名信息，应作为私密资料保存。

## 4. B：准备新版环境

B 需要 Docker Engine、Compose v2、Git、Python 3、足够磁盘，以及源码构建时拉取依赖所需的网络。可联网时从源码构建；不能联网时在可联网环境构建已确认提交的镜像，使用 `docker image save/load` 传输，并在 B 用 `up --no-build` 启动。

```bash
mkdir -p ~/Codes/zjerp
cd ~/Codes/zjerp
git clone https://github.com/nekox41/zjerp_backend.git
git clone https://github.com/nekox41/zjerp_frontend.git
cd zjerp_backend
# 两个仓库分别 checkout 经过确认的提交，不在切换途中追随新提交。
git rev-parse HEAD
git -C ../zjerp_frontend rev-parse HEAD
python3 scripts/init-compose-env.py
```

编辑权限为 600 的 `.env`：

```dotenv
HTTP_PORT=8088
MYSQL_DATABASE=YOUR_DATABASE
MYSQL_USER=zjerp
BOOTSTRAP_DATABASE=false
PUBLIC_API_URL=http://B_IP:8088/prod-api
FRONTEND_CONTEXT=../zjerp_frontend
```

保留脚本生成的独立密码和 JWT 密钥，不复制 A 的弱密码。`MYSQL_DATABASE` 必须与备份内容匹配。选用 80 端口时，同时修改 `HTTP_PORT=80` 和公开地址；不要保留示例中的 `B_IP`。新 Compose 不发布 MySQL/Redis 的宿主机端口。

### 4.1 保留生产维保卡资源

```bash
mkdir -p ~/app/zjerp/wbcard
tar -xzf ~/zjerp-migration/rehearsal/wbcard.tar.gz -C ~/app/zjerp/wbcard
```

在后端仓库创建本地 `compose.override.yaml`。下面绝对路径需替换为 B 的实际路径：

```yaml
services:
  backend:
    volumes:
      - /home/B_USER/app/zjerp/wbcard:/app/wbcard
```

本地 override 不提交 Git。此挂载覆盖镜像内默认模板，保留 A 的生产内容。根据实际目录权限，为容器 UID/GID 10001 提供所需读写权限（如使用 `sudo chown -R 10001:10001 ~/app/zjerp/wbcard`，只对 B 专用恢复目录执行）。如果数据库保存 A 的绝对宿主机路径，必须针对相关记录改成容器实际路径，并验证模板和字体可读取。

```bash
docker compose config --quiet
docker compose build backend
docker compose build frontend
docker compose up -d mysql redis
docker compose ps
```

等待 MySQL/Redis 健康。此时不启动应用；空库不应加载框架示例数据。`BOOTSTRAP_DATABASE=false` 只在空数据卷首次初始化时生效，事后修改不能撤销已导入的初始化数据。

## 5. B：恢复数据库和文件

在后端仓库执行，以下 `RESTORE_DIR` 演练时指向 rehearsal，正式迁移时改为 final：

恢复前先在 B 关闭 MySQL 事件调度，防止导入的事件提前执行业务操作（仅操作 B）：

```bash
docker compose exec -T mysql sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysql -uroot -e "SET GLOBAL event_scheduler=OFF;"
'
```

数据库容器若重启，须重新检查此设置。导入后记录 `SHOW EVENTS` 的事件状态，仅在正式开放时按已确认规则恢复调度。

```bash
RESTORE_DIR="$HOME/zjerp-migration/rehearsal"
(cd "$RESTORE_DIR" && sha256sum -c SHA256SUMS)
docker compose exec -T mysql sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"
' < "$RESTORE_DIR/database.sql"
```

检查退出码及错误输出。不得使用 `--force` 跳过 SQL 错误。导入后核对所有表、视图、触发器、存储过程、事件及其权限。应用使用 B 新建的 `zjerp` 账户，不能依赖旧 root 连接。

```bash
docker compose run --rm --no-deps --user root --entrypoint sh backend -c \
  'tar -xzf - -C /app/upload && chown -R 10001:10001 /app/upload' \
  < "$RESTORE_DIR/upload.tar.gz"
```

恢复时应用必须停止；在专用空上传卷中解压，避免与其他环境文件混在一起。不要移动、重命名原文件。模板目录同样使用当前备份恢复，并检查与数据库配置一致。

### 5.1 记录迁移前基线

使用管理员客户端：

```bash
docker compose exec mysql sh -c \
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE"'
```

```sql
SELECT COUNT(*) FROM zj_contract;
SELECT COUNT(*) FROM zj_report;
SELECT COUNT(*) FROM zj_wbcard;
SELECT COUNT(*) FROM sys_user;
SELECT COUNT(*) FROM sys_role;
SELECT COUNT(*) FROM sys_menu;
SELECT COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file),'') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file),'') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach),'') <> '';
SELECT COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file),'') <> '';
SELECT job_id, job_name, status FROM sys_job;
```

对生产其他业务表也逐表核对精确数量，记录关键记录及关联。菜单执行升级会增加文件管理项，属于预期差异。演练中先记录任务状态再暂停调度；在确认第三方配置和真实任务影响之前，B 不得向外发送消息、调用写接口或执行真实定时任务。若使用框架 `sys_job`，可在 B 记录状态后执行 `UPDATE sys_job SET status='1';`，并检查是否还有自定义调度。不要修改 A 的任务状态来完成 B 的演练。

## 6. B：执行带原文件的统一附件迁移

本次生产原文件在 A，优先采用带文件迁移。**不要先运行 `migrate-file-metadata.py --apply`**：它仅登记旧 URL 并清空旧列，之后带文件接口无法再根据这些旧列计算哈希和标准化复制。

### 6.1 创建或升级文件表及菜单

```bash
docker compose exec -T mysql sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  exec mysql -uroot "$MYSQL_DATABASE"
' < sql/sys_file.sql
```

如果生产库已经有早期原型的 `sys_file`，先备份并审查表结构，按 [文件迁移说明](FILE_MIGRATION.md) 执行 `sql/sys_file_metadata_only_upgrade.sql` 后再运行上述 SQL；不要覆盖已存在的数据。核对 `sys_menu` 含有升级 SQL 需要的 `route_name` 列等字段；若生产结构更旧，需先补齐经评审的结构差异，不能运行全量框架建库 SQL。

如果旧附件列已经清空且 `sys_file.remark` 标记 `legacy_metadata_only`，先停止本节流程：完整恢复原目录可恢复部分旧链接，但当前没有对这些元数据记录做标准化回填的批量工具，需要单独处理。

### 6.2 在隔离访问条件下启动并迁移

仅允许运维和验收人员访问 B，暂不开放业务写入。B 的后台定时任务和第三方写接口应已隔离。

```bash
docker compose up -d --no-build backend frontend
docker compose ps
docker compose logs --tail=100 backend frontend
```

通过 `http://B_IP:8088` 用原生产管理员登录，进入“系统管理 → 文件管理”，点击“迁移旧附件”（接口 `POST /system/file/migrate`）。记录成功、失败数和错误详情，保存私密迁移报告。接口可能同步处理大量文件，应在演练测量耗时并确认代理超时；浏览器超时不能直接视为回滚或立刻重复发起，先检查后台日志和数据库状态，避免并发运行。

当前迁移行为：

1. 读取合同、报告正文、报告附件和维保卡旧字段，按逗号/分号拆分引用。
2. 从恢复的上传根目录读取本地 `/profile/` 文件，不从 A 下载 HTTP 资源。
3. 计算真实哈希和大小，复制到 `files/<哈希前两位>/<UUID>.<扩展名>`，创建 `sys_file` 及业务关联。
4. 每个附件组独立事务，整组成功后才清空对应旧字段；失败组回滚本次元数据和新副本，旧字段保留。
5. 保留旧原文件。全局不是一次大事务，因此部分组成功时要按失败报告处理，不应把整次执行简单称为全成功或全失败。

旧字段如果有文件名本身包含未编码的逗号/分号，拆分有歧义；绝对 URL 的特殊字符也可能影响解析。必须参照原文件和旧记录人工确认，编写针对性修复后重试，不要猜测拆分结果、删除失败引用或用空文件替代。

## 7. 旧 IP、历史引用与模板路径处理

设置 `PUBLIC_API_URL` 只影响相关新地址生成，不会自动替换已有数据库 URL。带文件迁移成功的四类业务附件会使用新生成的 `/profile/files/...` 引用，但其他数据仍需检查。

逐项检查：用户头像、富文本内嵌图片、未迁移附件、已经存在的 `sys_file.file_url`、系统参数、模板与字体路径、外部系统回调、局域网收藏链接。先列出命中记录和字段，再备份、对准确的旧地址前缀做有范围的更新，保留原值到修改清单。不得对 SQL 备份全局 `sed` 替换旧 IP。

- 原 `/profile/...` 相对链接：验证 B 的 Nginx `/profile/` 转发和文件实际存在。
- `http://A_IP:旧端口/profile/...`：改为正确的 B 地址或应用支持的相对地址，并实测下载。
- 非 `/profile/` 文件访问形式、外部 URL：先确定归属，本地迁移接口不自动处理。
- `/home/aa/...` 等宿主机绝对模板路径：改为 B 容器中可访问的 `/app/wbcard/...` 等真实路径。

验收 B 时应确保浏览器下载请求没有依赖 A。仅仅页面上看到附件名称不算成功。

## 8. 验收标准

| 项目 | 必须通过的检查 |
| --- | --- |
| 备份 | SQL/归档可恢复，跨机 SHA-256 一致，A 和离机副本均保留 |
| 数据库 | 全部业务表精确数量、关键金额/状态/关联与停写基线一致；计划内变化有记录 |
| 统一附件 | 四类待迁移旧字段数量为 0；失败清单为空或有逐项确认且未阻断上线的处置结果 |
| 文件内容 | 迁移元数据有真实哈希/大小，抽样用源文件计算哈希核对；重要附件逐个核验，报告下载检查完整性 |
| 账号权限 | 原账号登录、角色权限及文件菜单正常，普通用户权限符合预期 |
| 业务 | 合同、报告、维保卡列表和详情、附件回显/下载、新建/编辑/取消/删除正常 |
| 特殊原名 | 中文、空格、逗号、分号、百分号、加号、井号的新上传正常，磁盘使用标准名称 |
| 文档生成 | 生产模板/字体读取正常，维保卡生成和打印内容、布局正常 |
| 地址与服务 | B 主页、深层路由、API、两种文件代理路径可用，日志无持续错误，无请求依赖 A |
| 调度与外部接口 | 恢复经过确认的任务状态，真实任务与第三方操作只由 B 执行一次 |

核对 SQL：

```sql
SELECT biz_type, COUNT(*) FROM sys_file WHERE del_flag='0' GROUP BY biz_type;
SELECT COUNT(*) FROM zj_contract WHERE COALESCE(TRIM(contract_file),'') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_file),'') <> '';
SELECT COUNT(*) FROM zj_report WHERE COALESCE(TRIM(report_attach),'') <> '';
SELECT COUNT(*) FROM zj_wbcard WHERE COALESCE(TRIM(file),'') <> '';
SELECT COUNT(*) FROM sys_file
WHERE del_flag='0' AND (file_hash IS NULL OR file_size IS NULL);
```

存在其他来源的元数据时，最后一项非零需要逐条解释，不能直接清空。业务附件引用数并不等于磁盘唯一文件数：同一文件可以关联多条业务，不能只用数量相同证明迁移正确。

## 9. 正式切换窗口

演练通过后，记录修复脚本、耗时和验收结果，冻结 B 前后端提交及部署配置。A 在演练期间仍在使用，因此演练数据不能直接开放生产。

1. 通知所有用户停写，禁止外部接口、定时任务及其他客户端继续写 A 的业务库或文件。
2. 在 A 旧 Compose 目录执行 `docker compose stop front backend`。MySQL 保持运行供备份使用；确认所有额外写入方、数据库事件/外部任务也已停写。
3. 按第 3 节重新制作 `~/zjerp-migration/final` 的数据库和文件完整备份，记录时间点、基线和校验值。最终文件归档期间也不能有外部程序改动目录。
4. B 停止演练应用：`docker compose stop frontend backend`。先备份 B 演练结果，确保没有真实用户数据。
5. 在 B 使用全新独立的数据卷恢复最终备份。推荐在同一目录的后续命令统一使用新的 `COMPOSE_PROJECT_NAME`，例如 `export COMPOSE_PROJECT_NAME=zjerp-production`，与演练默认项目的卷隔离；确保演练容器已停止，端口不冲突。用 `docker compose config --quiet` 和 `docker compose ps -a` 确认当前项目。不要删除卷来“重置”。
6. 对新项目重新执行第 4～8 节，`RESTORE_DIR` 改为 final。模板恢复到生产专用目录并更新本地 override；不要让仍运行的演练容器共享写入同一模板目录。暂停任务并隔离外部副作用后再启动应用，执行经过演练的修复和带文件迁移。
7. 核心验收全部通过后记录开放时间，用户统一改访问 B 的 IP。A 保持停写，不应双机同时处理业务。
8. 按基线恢复 B 应执行的任务状态及外部集成，观察日志、资源、上传、下载和文档生成。
9. 对 B 迁移完成状态再做数据库、上传和模板配套备份，记录新 Compose 项目名、卷名和镜像 ID。后续运维必须继续使用这个项目名；单次 `export` 不会跨 SSH 会话保留，应将其固化到受保护的运维环境或 `.env` 中的 `COMPOSE_PROJECT_NAME`。

不提供一条“清空 B 数据库”的快捷命令。若选择复用演练卷，必须另行确认库表、原文件和迁移副本都已正确重置，否则容易遗留演练数据；独立生产项目卷更便于复核。

## 10. 失败与回退

### B 尚未开放真实业务

停止 B 的前后端及外部写入任务，保留日志和失败备份。A 原数据库、原文件与旧镜像仍在，可在 A 原 Compose 目录执行：

```bash
docker compose start backend front
```

恢复 A 曾暂停的外部写入和任务，通知用户继续使用 A 的旧 IP。由于最终备份后 A 保持停写、B 未接收真实业务，此时可按停写时间点恢复使用。不要让 B 的定时任务继续运行。

### B 已产生真实业务

立即停止 B 的新增写入并备份 B 当前数据库、上传文件和模板。不能直接回到 A 后丢弃 B 的新增合同、回款、附件等变更；应先比较切换时间后的增量，制定回放/合并方案，或优先修复 B。旧版未必兼容新文件关系，不能简单把 B 数据库导回 A 旧版继续使用。

A 保留时长由业务方根据稳定运行及备份验证情况确定。在确认新环境备份可恢复、历史附件与文档生成正常前，不清理 A、旧文件或 B 的迁移原件。

## 11. 相关文档

- [Docker 与 Compose 部署](DEPLOYMENT.md)：新环境配置、卷和构建说明。
- [文件系统迁移步骤](FILE_MIGRATION.md)：两种迁移路径与已仅迁移元数据的处理边界。
- [统一文件存储设计](FILE_STORAGE.md)：元数据、关联与文件保存约定。

本方案不包含未来 13 个业务模块的数据库升级；当前迁移以已实现的统一文件服务为边界。后续业务模块应另行提供版本化数据库升级脚本和迁移验收。
