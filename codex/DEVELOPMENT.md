# 原生开发与 Codespaces

使用独立开发数据，勿连接机器 B 生产库。Java 17、Maven、Node 22、MySQL 8+、Redis 和 MySQL CLI 是必要依赖。两个仓库都应在 master 上拉取最新提交；更新前先 `git status`，保留未提交开发。

## 首次创建开发库

先通过 MySQL 管理员创建空库 `zjerp_dev`、独立开发账户并授权。以下初始化只接受空库，不覆盖旧表：

```bash
MYSQL_HOST=127.0.0.1 MYSQL_USER=zjerp_dev MYSQL_DATABASE=zjerp_dev \
MYSQL_PASSWORD='你的开发库密码' bash scripts/init-database.sh
cp .env.dev.example .env.dev
# 编辑 .env.dev 中的数据库、Redis 和随机 TOKEN_SECRET
bash scripts/dev-backend.sh
```

脚本 `clean package` 构建整个 Maven reactor，再运行本次生成的 `zjerp-admin/target/zjerp-admin.jar`。不要只编译 admin 并复用本地 Maven 仓库中的旧业务模块，也不要运行其他目录里遗留的 JAR。

前端仓库执行 `npm ci`、`npm run dev`，转发 8888 端口，开发代理指向同一 Codespace 的 8080。若两个应用在不同 Codespace，需要显式调整代理目标和对应端口访问权限。

## 界面仍像旧版时

1. 两个仓库分别检查 `git branch --show-current` 与 `git log -1`。
2. 检查监听 8080/8888 的进程及工作目录，关闭已确认的旧开发进程再运行新入口。
3. 确认使用开发库，检查 `zj_work_project`、`zj_contract_identity` 与 `sys_menu.component` 中的 `zhongji/projects/maintenance`。旧菜单来自数据库，重编译不能修复旧库。
4. 已有旧开发库保留备份，另建空库初始化并更换连接；需要旧数据时走审核迁移，不能直接重跑 seed SQL。
5. 退出再登录以加载当前角色菜单，确认打开的是 8888 开发端口而不是旧静态预览。

不要提交 `.env.dev`，也不要在日志或文档输出密码。

前端仓库已跟踪的.env.production、.env.development、.env.staging是公开构建配置，必须随源码保留，不能套用后端私密.env的过滤规则。Vite要求VITE_APP_BASE_API存在；生产应为/prod-api，否则拒绝构建。容器发布需从实际镜像提取页面检查浏览器请求前缀，本地dist通过不能替代镜像产物验证。

## 2026-10-29 Codespaces 实际修复

当前工作目录为 `/workspaces/codespaces-blank/zjerp`。两个子仓库已更新；原有 pom.xml、application.yml 的未提交修改和 ARCHITECTURE.md 均保留。现场没有原生 ERP 开发进程，只有旧后端容器，MySQL 的 server 库为空、sys_menu 不存在。现在已停止旧后端容器，在现有 MySQL 8 开发库初始化当前结构，使用 Java 17 全模块重新打包并原生运行，前端使用 Vite 的 8888 端口。

开发配置在后端 `.env.dev`，文件权限 600。现有 MySQL/Redis 开发容器继续运行，原生应用通过它们的容器地址连接；重建依赖容器后地址可能变化，需要更新 `.env.dev`。后端与前端日志/PID 位于工作目录下 `.codex-local/`，不提交 Git。重启 Codespace 后分别执行 `bash scripts/dev-backend.sh` 与前端 `npm run dev`；启动前确认没有旧实例占用端口。
