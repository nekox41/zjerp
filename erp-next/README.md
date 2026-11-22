# 新版 ERP 独立运行环境

这是新版 ERP 的独立运行栈，与旧版 RuoYi 应用和旧的 server 数据库完全隔离。它包含 MySQL 8.4、Flyway Schema V1/V2、新版 Spring Boot API 和独立 Vue 工作空间入口。

## 首次启动

在仓库根目录执行：

```bash
cp erp-next/.env.example erp-next/.env.local
# 编辑 erp-next/.env.local，设置三个唯一强随机密钥
docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build
```

访问新版入口：`http://localhost:8089/next.html`。API 默认仅通过本机 8090 端口提供，也可在环境文件中修改端口。

首次打开会要求输入环境变量 ERP_BOOTSTRAP_TOKEN 对应的一次性初始化凭据，创建第一个系统管理员（密码至少 12 位）。管理员建立后初始化接口自动拒绝再次初始化。之后使用管理员账号登录。

## 开发

后端独立 Maven 工程，不加入旧版 reactor：

```bash
mvn -f backend/erp-next/pom.xml verify
```

前端仍使用统一 Vue/Vite 工程：

```bash
cd frontend
npm ci
npm run dev
```

Vite 的 `/next-api` 代理默认指向 `http://localhost:8090`。如需修改，可设置 `VITE_NEXT_DEV_API_TARGET`。

## 安全边界

- 只连接独立数据库 `erp_next`，不读取或修改旧版 `server`。
- MySQL 不发布宿主机端口；只有 API/Web 端口对宿主机映射。
- 数据库密码与初始化凭据必须通过环境变量提供；不要提交真实 .env 文件。
- 密码使用 BCrypt；登录令牌为随机不透明值，数据库只保存 SHA-256 摘要；会话可撤销并有过期时间。
- 后端每个请求验证会话和角色权限；前端隐藏入口不是安全边界。
- 管理员初始化只允许在尚无管理员时执行，且需要独立的一次性部署凭据。
- 当前首个切片实现账号创建、角色读取、审计查询；尚未实现密码重置、会话管理 UI、组织管理、文件服务和业务模块。

## 数据持久化

`erp_next_mysql_data` 是新版数据库的独立命名卷。普通停止使用 `docker compose down`，不要随意使用 `down -v`；后者会永久删除该新版数据库卷。生产使用前必须完成备份/恢复演练。
