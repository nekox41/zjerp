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

## 启动新版 ERP

```bash
cp erp-next/.env.example erp-next/.env.local
# 编辑 erp-next/.env.local，替换全部占位密钥
docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build
```

打开 http://localhost:8089/next.html，按页面引导创建首个管理员。默认 API 端口为 8090。数据库使用独立命名卷，不连接旧版 server 数据库。停止服务使用 `docker compose ... down`；不要在需要保留数据时使用 `down -v`。

## 旧版部署

原有 RuoYi 部署配置仍位于 backend/，与新版 ERP Compose 分离。不要将旧版部署文件当作新版生产部署，也不要把旧版生产数据库指向 erp_next。新版部署目前处于早期平台开发阶段，尚未完成备份恢复演练与生产上线验收。

## 仓库历史

本仓库由原 zjerp_backend 重命名而来，前端 zjerp_frontend 的完整 Git 历史通过双父提交接入。旧前端仓库首页已标注为历史归档入口，今后只在本 Monorepo 开发。原始仓库历史和旧 Issue/PR 讨论仍保留供追溯。

参见 [仓库合并执行记录](docs/monorepo-import.md)。
