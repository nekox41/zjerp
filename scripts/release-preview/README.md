# ERP Next 开发预览版（禁止用于生产）

Ubuntu / WSL 或 Debian，普通用户执行：

```bash
bash deploy.sh
```

首次检测并安装缺失的 Java 17 JRE、Nginx、PostgreSQL 17、Python 3 等运行依赖，必要时需要 sudo。无需 Docker、Maven、Node.js、npm 或源码。PostgreSQL 17 缺失且系统源没有时会配置官方 PGDG 软件源。

每次执行默认清空本工具独立创建的测试数据库及附件。状态保存在 ~/.local/share/zjerp-native-preview，数据库独立端口 55439，API 8090，网页 8089，全部绑定 127.0.0.1。不会连接系统数据库或旧 MySQL，不修改系统 Nginx 配置。首次部署前如旧 Docker 占用端口，请在原仓库执行 bash deploy.sh stop，或首次设置 ERP_PREVIEW_WEB_PORT / ERP_PREVIEW_API_PORT / ERP_PREVIEW_DB_PORT。端口配置随后保留。

浏览器 http://localhost:8089/next.html，admin / 123456。只使用虚拟测试资料，不要放入真实身份证件或合同。不要对外开放端口。停止：bash deploy.sh stop；状态：bash deploy.sh status；日志：bash deploy.sh logs。重新下载解压任意新版后直接执行 bash deploy.sh。WSL 重启后需重新执行部署（清空数据）。

不提供正式生产部署及迁移功能。原 Docker 测试入口仍保留在源码仓库中。
