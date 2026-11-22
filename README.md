# 众基 ERP · Monorepo

统一开发仓库，后端使用 Spring Boot 3 / Java 17，前端使用 Vue 3 / Vite。前后端共享 Git 历史、Issue、Pull Request 和版本规划，但保持独立构建。

## 目录

- [backend/](backend/) — 原后端 Maven 多模块工程，包含 `pom.xml`、SQL、Dockerfile、Compose 与旧版部署脚本
- [frontend/](frontend/) — 原前端 Vue 工程，包含 `package.json`、页面和静态资源
- [docs/](docs/) — 统一仓库合并记录与产品文档
- [.github/workflows/](.github/workflows/) — 前后端构建验证

## 本地构建

```bash
cd backend && mvn clean verify
cd ../frontend && npm ci && npm run build:prod
```

旧版部署配置暂时仍位于 `backend/`；不要从仓库根目录直接运行原 Compose，也不要将其当作新版 ERP 的生产部署。路径和端到端启动验证由 #18 跟进。

## 迁移说明

- 本仓库由原 `nekox41/zjerp_backend` 重命名而来，前端 `nekox41/zjerp_frontend` 的完整 Git 历史通过双父提交接入。
- 旧前端仓库保留作历史参考；今后新业务开发统一提交本仓库的 Issue/PR。
- 旧仓库未合并 PR 和旧数据库迁移工作仍需逐项处理，不会因合并代码而自动完成。
- 新版 ERP 尚在开发中，**不要将目录合并等同于 V1 已可用**。

参见 [仓库合并执行记录](docs/monorepo-import.md)。
