# 众基 ERP 前端

Vue 3、Element Plus、Pinia 和 Vite 管理端。后端及前后端、MySQL、Redis 的统一 Compose 配置维护在 [zjerp_backend](https://github.com/nekox41/zjerp_backend)。

## 本地开发

```bash
npm ci
npm run dev
```

默认访问 `http://localhost:8888`，`/dev-api` 代理到 `http://localhost:8080`。修改后端地址时调整 `vite.config.js`。先启动后端及其 MySQL、Redis。

## 构建镜像

```bash
docker build -t zjerp-frontend:local .
```

Dockerfile 自行执行 `npm ci` 和 `npm run build:prod`，无需事先生成 `dist`。运行阶段使用 Nginx，支持 History 路由回退、`/prod-api/` 代理和历史 `/profile/` 资源。Nginx 的后端服务名为 `backend:8080`；单独运行镜像时也需要可解析的同名后端服务。

## 一起部署

两个仓库放在同一父目录，在后端仓库运行：

```bash
cd ../zjerp_backend
python3 scripts/init-compose-env.py
# 编辑 .env，将 PUBLIC_API_URL 改为实际访问地址。
docker compose up -d --build
```

默认访问 `http://localhost:8088`。详见后端的 [部署说明](https://github.com/nekox41/zjerp_backend/blob/master/DEPLOYMENT.md) 和 [文件系统迁移步骤](https://github.com/nekox41/zjerp_backend/blob/master/FILE_MIGRATION.md)。

文件管理页面为 `/system/file`，动态路由及权限由后端菜单下发。旧数据仅迁移数据库时，列表可显示元数据，但下载仍需原文件；未知大小显示“未核实”。

## 验证

```bash
npm run build:prod
```

GitHub Actions 对 PR 仅构建验证，分支推送按现有 Docker Hub 配置发布镜像。不要提交 `.env.local`、上传文件或本地构建产物。
