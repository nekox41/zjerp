# Docker 与 Compose 部署

从已有生产机器迁往另一台空机器并保留数据与原文件，请先阅读 [机器 A → 机器 B 生产迁移方案](PRODUCTION_MIGRATION_A_TO_B.md)。

## Dockerfile 检查与调整

| 原配置问题 | 当前处理 |
| --- | --- |
| 后端仅复制本机 target JAR，干净仓库无法构建 | Maven 构建阶段从源码打包，Java 17 JRE 阶段运行 |
| 后端缺少维保卡模板和字体 | 将 wbcard 资源一起复制到 /app/wbcard，保持代码依赖的相对路径 |
| 后端默认以 root 运行，匿名卷用途不明确 | 使用 UID/GID 10001，Compose 显式挂载上传和日志卷 |
| 前端依赖本机 dist，Nginx 镜像未限定版本系列 | Node 22 构建阶段执行 npm ci / 生产构建，Nginx stable-alpine 提供静态文件 |
| Nginx server_name 固定为局域网 IP | 使用通用主机匹配，保留 History 路由回退，代理 /prod-api/ 和旧 /profile/ |
| Docker 上下文可能包含上传目录和整库备份 | 两个仓库均配置 .dockerignore，排除本地数据和构建产物 |

镜像标签固定到兼容的版本系列；需要完全可重复的发布时，应在评审后固定所验证镜像的 digest。构建不需要宿主机安装 Maven、Java 或 Node。内存紧张时可分别执行 `docker compose build backend`、`docker compose build frontend`，再启动服务；前端构建阶段已将 Node 堆上限设为 1024MB。

## 服务与配置

在后端仓库中执行所有 Compose 命令。要求 Docker Engine、Compose v2；从源码构建时两个仓库相邻，或在 `.env` 中设置 `FRONTEND_CONTEXT`。生成环境配置：

```bash
python3 scripts/init-compose-env.py
```

脚本以权限 600 创建 `.env`，生成独立的 MySQL root、应用数据库账户、Redis 和 JWT 随机密钥；已有文件不会覆盖。也可复制 `.env.compose.example` 并填写其中的必填项。

| 配置 | 默认值或说明 |
| --- | --- |
| HTTP_PORT | 8088，唯一发布到宿主机的服务端口 |
| MYSQL_DATABASE / MYSQL_USER | server / zjerp；应用不使用 root 连接 |
| MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD | 必填，初始化后修改变量不会自动修改既有数据库账户密码 |
| REDIS_PASSWORD | 必填，容器内部 Redis 鉴权 |
| TOKEN_SECRET | 必填，Base64 格式的随机密钥，修改后旧 JWT 失效 |
| PUBLIC_API_URL | `http://localhost:8088/prod-api`，改为用户真实可访问的地址 |
| FRONTEND_CONTEXT | ../zjerp_frontend，按 Compose 文件所在目录解析 |
| BOOTSTRAP_DATABASE | true 为全新初始化；导入旧库时先设 false |
| BACKEND_IMAGE / FRONTEND_IMAGE | 可选，默认 zjerp-backend:local / zjerp-frontend:local |

服务名为 `mysql`、`redis`、`backend`、`frontend`。后端连接容器服务名，而不是 127.0.0.1。MySQL 和 Redis 不发布宿主机端口；前端依赖后端健康，后端依赖 MySQL/Redis 健康。

`PUBLIC_API_URL` 影响后端新返回的绝对文件地址，不会自动改写数据库中已有的旧域名 URL。通过 Codespaces、域名或 HTTPS 代理访问时，填写相应外部地址并重建容器环境。例如 `https://your-domain.example/prod-api`。HTTPS 证书和 TLS 终止由外部反向代理配置，当前 Compose 提供 HTTP。

## 全新安装

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 backend
```

MySQL 仅在空数据目录初始化时运行 `deploy/mysql/10-init.sh`，按顺序加载框架种子、Quartz 表、无业务数据的业务表结构和 `sys_file`。演示定时任务会暂停。初始管理员 `admin / admin123` 来自框架种子，首次登录后修改密码。

新库业务表和模板配置均为空；框架菜单及文件管理会初始化，其他自定义业务菜单、字典应导入已有配置或通过系统管理配置。维保卡的附带模板需要在部署后配置，不会从历史备份自动复制人员资料或生产业务数据。

## 导入旧数据库

这一步面向新的、尚未初始化的目标 MySQL 数据卷。已有目标库请先备份，不能直接覆盖。

1. 在 `.env` 中设置 `BOOTSTRAP_DATABASE=false`。
2. 仅启动存储服务：

```bash
docker compose up -d mysql redis
# 等待 mysql 健康后，将你自己的备份导入目标库。
docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$MYSQL_DATABASE"' < /private/path/database.sql
```

备份中的数据库名应与 `MYSQL_DATABASE` 一致，或使用只包含表和数据、不包含 `CREATE DATABASE` / `USE` 的单库备份。镜像自动创建的应用账户已获得 `MYSQL_DATABASE` 的权限。

3. 对导入的旧库执行 [文件系统迁移](FILE_MIGRATION.md)，不要再次运行会重建框架表的 `ry_20250417.sql`。
4. 恢复旧上传目录后启动应用：

```bash
docker compose up -d --build
```

如果旧上传文件暂不可用，可以先完成仅数据库迁移再启动应用，历史附件下载仍会失败。

## 数据持久化和文件恢复

默认创建以下命名卷，实际名称带 Compose 项目前缀：

- `mysql-data`：MySQL 数据。
- `redis-data`：Redis AOF；登录会话属于临时运行状态，不能作为业务备份。
- `upload-data`：`/app/upload`，包含新旧附件。
- `backend-logs`：`/app/logs`。

不要在保留数据的环境中使用 `docker compose down -v`。普通 `down` 和容器重建不会删除这些卷。

若要恢复旧文件，先将旧的整个 `ruoyi.profile` 目录打包；归档根目录应直接包含 `upload/`、`avatar/` 等子目录。后台停止时执行：

```bash
docker compose run --rm --no-deps --user root --entrypoint sh backend -c 'tar -xzf - -C /app/upload && chown -R 10001:10001 /app/upload' < /private/path/profile.tar.gz
```

不要额外增加一层 profile 目录。例如 `/profile/upload/2025/a.pdf` 应落在容器 `/app/upload/upload/2025/a.pdf`。若数据库模板路径是旧机器的绝对路径，需要另外改为容器中的实际模板路径；仅复制镜像内的默认模板不会重写数据库记录。

如需使用宿主机目录，增加本地 Compose override 将上传卷替换为 bind mount，并先给目录设置 UID/GID 10001 的读写权限。

## 运维与验证

```bash
docker compose ps
curl -fsS http://localhost:8088/
curl -fsS http://localhost:8088/prod-api/health
docker compose logs --tail=100 backend frontend
```

检查登录、系统管理 → 文件管理、文件上传回显、合同/报告/维保卡保存、删除和取消编辑。新上传必须生成 `files/` 标准路径，并在 `sys_file` 有记录。文件管理路由名为 `SysFileManagement`，避免与旧顶层 `File` 菜单冲突。

更新代码后运行 `docker compose up -d --build`。应用日志和健康检查不代替数据库与上传目录的配套备份。前端 GitHub Actions 对 Pull Request 仅构建，合并后的分支事件才推送镜像。

## 本次验证记录（2026-10-27）

- 从源码实际构建前后端镜像；首次并行前端构建被终止，限制 Node 堆并串行构建后成功。
- 独立空数据库的四服务 Compose 均通过健康检查，没有使用现有开发库或生产备份。
- 通过 Nginx 验证首页、History 深层路由、后端代理、登录/登出、文件菜单、特殊字符原名上传和两种文件访问 URL。
- 通过真实 API 和 MyBatis 验证合同附件关联、清空和业务删除。
- 在隔离库中验证四类旧字段的仅数据库迁移、逐条核对、重复预览无待迁移项，以及原来不存在 sys_file 表的情况。
