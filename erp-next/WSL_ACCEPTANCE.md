# WSL2 本地部署与迁移

在仓库根目录运行 `bash test.sh`（完整测试，日志 test-results/）或 `bash test.sh --smoke`。测试仍使用隔离临时 MySQL，不连接业务数据库。

## 部署

```bash
bash deploy.sh
```

首次部署识别旧版 Compose 项目 zjerp 的 mysql 服务；也可在首次部署前设置 `ERP_MYSQL_CONTAINER=实际容器名 bash deploy.sh`。无旧版容器则创建一个 MySQL 8.4。旧版 MySQL 8.0 不自动升级、不停止、不重建、不改旧账号密码。复用要求原容器运行、容器内 MYSQL_ROOT_PASSWORD 有效，专用用户名未冲突；脚本只初始化 erp_next 和专用最小范围账号。已绑定实例不自动切换；已有 erp_next 却没有本地标识时停止，请人工核对。之前版本建立过独立旧库容器时保留数据，需先备份并人工安排迁移。

打开 http://localhost:8089/next.html，用户名 admin，初始密码文件 ~/.local/share/zjerp-trial/admin-password。状态目录可通过 ERP_TRIAL_DIR 指定，首次端口 ERP_NEXT_WEB_PORT / ERP_NEXT_API_PORT。stop 只停止新版服务及自管 MySQL，不停止共享实例。原 start.sh 快速启动是独立旧入口，不用于共享实例部署；不要同时占用端口。

## 迁移

打开数据迁移页自动检测同一 MySQL 的 server：不存在或空库可跳过。已有旧库只读扫描；迁移前停止旧应用写入、完整备份 SQL 和附件。对于全新自管实例，可使用单 schema SQL：

```bash
bash deploy.sh legacy-import ~/legacy.sql
cp -a ~/legacy-uploads/. ~/.local/share/zjerp-trial/legacy-files/
```

SQL 不含 CREATE DATABASE、账户授权或其他 schema 操作。共享旧版实例禁止 SQL 导入及 legacy-shell，避免覆盖真实 server。导入后页面重新检测。原始文件须人工复制，保留相对路径。

```bash
# 共享实例须确认旧应用已停止写入，附件已备份
ERP_LEGACY_SOURCE_FROZEN=yes bash deploy.sh archive
bash deploy.sh plan
# 人工确认 review.json，参见 LEGACY_MIGRATION.md
bash deploy.sh preview
bash deploy.sh apply 完整计划SHA256
bash deploy.sh reconcile
bash deploy.sh
```

archive 使用专用只读账号和一致性事务；未映射的行保留在归档。apply 之前自动备份 erp_next 及私有附件，禁止自动改动 server。网页目前支持检测、扫描、冲突清单，不支持直接执行 SQL 或无审核一键转换。不能将不明确的甲方、合同归属、权限、签发状态自动猜测。共享实例的正式数据迁移需要维护窗口、备份恢复演练和授权。

日志：bash deploy.sh status / logs；备份：bash deploy.sh backup（停止新版写入）；启动：bash deploy.sh。不要分享密码、SQL、业务附件或真实数据日志。Docker 网络超时时参见 Docker daemon 代理配置说明 https://docs.docker.com/engine/daemon/proxy/ ，可使用可信 ERP_IMAGE_PREFIX。 

升级 V23–V24 后唯一超级账户名称为 admin；此前自动创建的 trial-admin 会保留 ID 与密码并重命名（存在冲突时停止迁移）。原初始密码文件不变。旧会话失效，重新登录即可。
