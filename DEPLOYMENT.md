# Compose 部署

要求 Docker Compose v2，源码构建需相邻的前后端仓库。运行命令见 README。`.env` 由 `scripts/init-compose-env.py` 创建，已有文件不覆盖。`FRONTEND_CONTEXT` 可以指定实际前端目录；`PUBLIC_API_URL` 设置为访问入口加 `/prod-api`。

## 空数据库初始化

MySQL 官方入口首次创建空数据卷后，执行 `deploy/mysql/10-init.sh`，调用共享的 `scripts/init-database.sh`。按依赖顺序导入框架、调度、业务、文件、当前合同结构和菜单、当前项目结构和菜单，暂停演示任务，最后写入 `zjerp_schema_state` 完成标记。

MySQL 使用 TCP、应用账户和业务表检查；开启初始化时还要求完成标记存在。后端等待 MySQL 与 Redis healthy；前端等待后端 `/health`。初始化脚本失败时不会写完成标记，后端不会抢先启动。

初始化器拒绝非空数据库，不能用它升级生产库。MySQL 初始化中断后数据卷可能已有部分表：先查看 `docker compose logs mysql` 修复原因，在确认该卷仅含可丢弃的首次部署数据后，由操作者重建开发卷重试；不要对已有生产卷执行 `down -v`。

## 已有数据库

设置 `BOOTSTRAP_DATABASE=false`，先启动 MySQL、恢复经过核验的当前版本数据库，再启动后端。已有 MySQL 数据卷不会再次执行初始化脚本。数据库必须已经包含当前业务表、合同视图和菜单；代码更新不等于数据库升级。机器 B 现有生产迁移已经完成，不运行新库初始化器。

旧库升级脚本用于特定版本的人工审核升级，不属于新库初始化。精确金额备份及现网路径见 `codex/OPERATIONS.md`。MySQL root、应用账户、Redis、JWT 密钥分别配置；更改环境变量不会更改已有 MySQL 账户密码。

## 构建与验证

内存较小时依次 `docker compose build backend`、`docker compose build frontend`，再 `docker compose up -d`。用 `docker compose ps`、服务日志、登录及合同/项目菜单核验。上传和日志卷由 UID/GID 10001 写入；数据库及文件卷需要独立备份。
