# 环境配置与代理规则

本项目通过 Vite 环境文件决定页面标题、运行模式和 API 前缀。浏览器始终请求同源相对路径；开发服务器或 Nginx 负责把请求转发给后端。不要把 Docker 内部服务名（例如 `backend`）写入浏览器端 API 地址。

## 环境矩阵

| 场景 | 构建/启动命令 | API 前缀 | 代理位置 | 默认上游 |
|---|---|---|---|---|
| 本地开发 | `npm run dev` | `/dev-api` | Vite dev proxy | `http://localhost:8080` |
| 预发布 | `npm run build:stage` | `/stage-api` | Nginx `/stage-api/` | `http://backend:8080` |
| 正式生产 | `npm run build:prod` | `/prod-api` | Nginx `/prod-api/` | `http://backend:8080` |

## 本地开发

修改 `.env.development` 中的 `VITE_DEV_API_TARGET` 即可更换本地后端地址：

```dotenv
VITE_APP_BASE_API = '/dev-api'
VITE_DEV_API_TARGET = 'http://localhost:8080'
```

Vite 代理会移除 `/dev-api` 前缀后转发给上游。例如浏览器请求 `/dev-api/login`，后端收到的是 `/login`。修改环境文件后需要重新启动 Vite。

## 预发布与生产

- `.env.staging` 用于预发布构建，API 前缀为 `/stage-api`。
- `.env.production` 用于正式构建，API 前缀为 `/prod-api`。
- Nginx 配置 `zjerp.conf` 同时代理这两个前缀到 `backend:8080`，并移除前缀后转发。
- Dockerfile 当前固定执行 `npm run build:prod`，因此标准 Docker 镜像为正式生产构建。预发布构建如需容器化，应明确使用 `npm run build:stage` 生成对应静态产物，再按同一 Nginx 代理规则部署。

示例：

```bash
npm ci
npm run build:stage
```

不要将 `.env.staging` 的产物误认为正式生产镜像。

## Compose 部署

当前后端仓库的 Compose 服务名为 `backend`，前端 Nginx 通过 Docker 网络内的 `backend:8080` 访问后端。前端容器无需暴露或配置后端容器的宿主机端口。

后端 `PUBLIC_API_URL` 应设置为用户实际访问地址加 `/prod-api`，例如：

```dotenv
PUBLIC_API_URL=https://erp.example.com/prod-api
```

该变量由后端使用，用于生成公开访问链接；它不是前端 Axios 的 `baseURL`。

## 独立部署 / 外部网关

如果前端不通过仓库提供的 Nginx 配置部署，外部反向代理必须至少满足：

1. 将 `/prod-api/` 转发到后端，并按当前 Nginx 规则去掉此前缀。
2. 如部署预发布产物，也要将 `/stage-api/` 转发到后端并去掉此前缀。
3. 将旧附件路径 `/profile/` 转发到后端文件服务。
4. 对上传请求设置足够的请求体大小限制和超时；当前 Nginx 配置为 10 GiB，请确保外层代理限制不更小。
5. 为前端 History 路由配置回退到 `index.html`。

如果使用独立域名或跨域 API，需要由外部网关处理 TLS、CORS 和安全策略；不要把 Docker 网络内的 `backend` 主机名作为浏览器可访问地址。

## 变更配置时的检查

- [ ] `VITE_APP_BASE_API` 与实际部署模式一致。
- [ ] Vite/Nginx 对应 API 前缀均有代理规则。
- [ ] 代理是否移除了 API 前缀，与后端路由保持一致。
- [ ] `/profile/` 附件访问正常。
- [ ] 上传大小限制和代理超时符合业务需求。
- [ ] History 深层 URL 刷新正常。
- [ ] HTML 不长期缓存，避免部署后继续引用旧资源。
