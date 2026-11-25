# 众基 ERP · Monorepo

统一开发仓库，后端使用 Spring Boot / Java，前端使用 Vue 3 / Vite。前后端共享 Git 历史、唯一开发计划和 PR 流程，但保持独立构建。

## 目录

- [backend/](backend/) — 旧版后端 Maven 多模块工程
- [backend/erp-next/](backend/erp-next/) — 新版 ERP 独立 Spring Boot API
- [frontend/](frontend/) — Vue 前端；旧版入口为 /，新版入口为 /next.html
- [erp-next/](erp-next/) — 新版独立 MySQL、Flyway、API、Web Compose 与数据库迁移
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

## WSL2 本地一键测试与验收部署

Ubuntu 22.04 / Docker Compose v2，仓库建议克隆到 WSL Linux 文件系统（如 ~/zjerp）。不要求宿主机安装 Maven、JDK、Node 或 Python MySQL 依赖。需有 python3、curl、flock（Ubuntu 软件包 python3、curl、util-linux）。

```bash
bash test.sh        # 完整构建与隔离 Smoke，日志在 test-results/
bash deploy.sh      # 持久化本地验收环境，优先复用旧版 MySQL，默认仅回环端口
```

部署后访问 http://localhost:8089/next.html，账号 trial-admin，初始密码文件 ~/.local/share/zjerp-trial/admin-password。该目录保留数据库的隔离标识、凭据、旧附件副本及迁移档案。测试与部署不共享容器或数据卷。详见 [WSL2 验收与迁移](erp-next/WSL_ACCEPTANCE.md)。

## Codespaces 一键启动新版 ERP（普通空库）

在 GitHub Codespaces 打开仓库后，直接运行：

```bash
bash start.sh
```

脚本首次运行会生成独立随机数据库密码和管理员初始化令牌，保存到被 Git 忽略的 `erp-next/.env.local`，自动构建并启动新版 MySQL、Flyway、API 和 Web，完成健康检查后输出访问地址。**Codespaces 的 8089 转发端口请始终设为 Private**。首次打开 `/next.html` 时，从本地 `erp-next/.env.local` 获取 `ERP_BOOTSTRAP_TOKEN` 初始化管理员，密码至少 12 位。

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
