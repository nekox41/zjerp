# 众基 ERP · Monorepo

统一开发仓库，后端使用 Spring Boot / Java，前端使用 Vue 3 / Vite。前后端共享 Git 历史、唯一开发计划和 PR 流程，但保持独立构建。

## 目录

- [backend/](backend/) — 旧版后端 Maven 多模块工程
- [backend/erp-next/](backend/erp-next/) — 新版 ERP 独立 Spring Boot API
- [frontend/](frontend/) — Vue 前端；旧版入口为 /，新版入口为 /next.html
- [erp-next/](erp-next/) — 新版独立 PostgreSQL、Flyway、API、Web Compose 与数据库迁移
- [docs/](docs/) — 架构、产品和仓库迁移文档
- [.github/workflows/](.github/workflows/) — 前后端构建和新版全栈 smoke test

## 唯一开发计划入口

所有产品规划、架构决策、下一步计划和进度交接统一维护在 [ERP V1 总控 Issue #8](https://github.com/nekox41/zjerp/issues/8)。常规开发不再新建独立 Issue；每个可交付功能通过 PR 实现并引用 #8。

## 本地构建

旧版后端：

```bash
cd backend && mvn -B clean verify
```

新版后端：

```bash
mvn -B -f backend/erp-next/pom.xml verify
```

前端：

```bash
cd frontend && npm ci && npm run build:prod
```

## 开发预览版：下载 Release 一键部署（推荐）

每次推送开发分支或 master 后，GitHub Actions 自动构建新版后端与前端、执行单元测试和两次裸机部署验证，成功后发布带 commit 标识的预发行版。其他分支及 PR 运行构建，但不发布；一次 push 中包含多个 commit 时构建推送后的分支头。

打开 [Releases](https://github.com/nekox41/zjerp/releases)，选择最新成功的 preview，下载 `erp-next-preview-linux.tar.gz` 和对应 `.sha256` 文件。

```bash
sha256sum -c erp-next-preview-linux.tar.gz.sha256
mkdir -p ~/zjerp-preview
tar -xzf erp-next-preview-linux.tar.gz -C ~/zjerp-preview
cd ~/zjerp-preview
bash deploy.sh
```

Ubuntu/WSL 或 Debian 普通用户运行。首次自动检测并安装 JRE 17、Nginx、PostgreSQL 17、Python3 等运行依赖（必要时 sudo），以后无需重新下载构建依赖，无需 Docker、Maven、Node.js、npm。每次部署自动重建独立测试数据库及附件，旧生产库不连接。访问 http://localhost:8089/next.html，admin / 123456，仅回环测试，禁止使用真实业务资料或部署到生产。

旧 Docker 体验环境若占用端口，在原源码仓库执行 `bash deploy.sh stop`。裸机预览的停止/状态/日志命令为 Release 目录中的 `bash deploy.sh stop|status|logs`。源码 Docker 构建入口继续保留，详见 [本地体验部署](erp-next/WSL_ACCEPTANCE.md)。

## Codespaces 一键启动新版 ERP（普通空库）

在 GitHub Codespaces 打开仓库后，直接运行：

```bash
bash start.sh
```

脚本首次运行会生成独立随机数据库密码和管理员初始化令牌，保存到被 Git 忽略的 `erp-next/.env.local`，自动构建并启动新版 PostgreSQL、Flyway、API 和 Web，完成健康检查后输出访问地址。**Codespaces 的 8089 转发端口请始终设为 Private**。首次打开 `/next.html` 时，从本地 `erp-next/.env.local` 获取 `ERP_BOOTSTRAP_TOKEN` 初始化管理员，密码至少 12 位。

再次运行 `bash start.sh` 可升级/重建应用，保留已有密码、数据库和附件卷；`bash start.sh status` 查看状态，`bash start.sh logs` 查看日志，`bash start.sh stop` 安全停止（不删除持久化数据）。请勿运行 `docker compose down -v`。该入口专用于 `erp_next` 新库，不连接旧生产系统；Codespaces 用于验收测试，不代表已完成生产 HTTPS/备份恢复演练。

## 启动新版 ERP

```bash
cp erp-next/.env.example erp-next/.env.local
# 编辑 erp-next/.env.local，替换全部占位密钥
docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build
```

打开 http://localhost:8089/next.html，按页面引导创建首个管理员。默认 API 端口为 8090。数据库使用独立命名卷，不连接旧版 server 数据库。停止服务使用 `docker compose ... down`；不要在需要保留数据时使用 `down -v`。

## 旧版部署

原有 RuoYi 部署配置仍位于 backend/，与新版 ERP Compose 分离。不要将旧版部署文件当作新版生产部署，也不要把旧版生产数据库指向 erp_next。新版尚未完成生产 HTTPS、恢复演练与完整浏览器验收；现有 CI 仅证明自动化检查通过。

## 仓库历史

本仓库由原 zjerp_backend 重命名而来，前端 zjerp_frontend 的完整 Git 历史通过双父提交接入。旧前端仓库首页已标注为历史归档入口，今后只在本 Monorepo 开发。原始仓库历史和旧 Issue/PR 讨论仍保留供追溯。

参见 [仓库合并执行记录](docs/monorepo-import.md)。

新版运行栈：Docker Compose、PostgreSQL 17、Flyway 10.20.1、Spring Boot 和 Vue。仅新版 PostgreSQL；旧生产 MySQL 不变，生产迁移暂缓。
