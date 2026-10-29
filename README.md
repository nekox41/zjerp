# 众基 ERP 后端

Java 17、Spring Boot 3、MyBatis、MySQL 8+、Redis。前端仓库：[zjerp_frontend](https://github.com/nekox41/zjerp_frontend)。

当前业务包括角色权限、统一附件、用户资料、中间人、五类合同和五类项目。首页菜单由数据库下发，单独更新 Java/Vue 源码不会更新旧数据库菜单。

## 开发（含 Codespaces，不使用 Docker 运行应用）

见 [开发指南](codex/DEVELOPMENT.md)。准备独立 MySQL 开发库与 Redis；首次用 `scripts/init-database.sh` 创建当前表和菜单，再用 `bash scripts/dev-backend.sh` 构建全部模块并启动。前端执行 `npm ci && npm run dev`，转发 8888 端口。

## Compose 首次部署

两个仓库相邻命名为 `zjerp_backend`、`zjerp_frontend`，在后端执行：

```bash
python3 scripts/init-compose-env.py
# 编辑 .env 中的 PUBLIC_API_URL；目录不同则修改 FRONTEND_CONTEXT
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

默认访问 http://localhost:8088，初始管理员 `admin / admin123`，首次登录后修改密码。详见 [部署说明](DEPLOYMENT.md)。

## 项目资料

- [设计、代码规范、当前状态与后续计划](codex/README.md)
- [合同模块](CONTRACT_MODULES_UPGRADE.md)、[项目模块](WORK_PROJECTS.md)、[用户资料](USER_MATERIALS.md)、[角色权限](ROLE_ONLY_UPGRADE.md)
- 前端界面遵循前端仓库 `DESIGN_SYSTEM.md`。

测试：`mvn test` 与 `bash scripts/verify-file-storage.sh`。上传文件、密钥、日志、数据库备份和历史生产操作工具不进入仓库。
