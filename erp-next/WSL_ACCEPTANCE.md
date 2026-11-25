# WSL2 / Linux 本地体验测试

```bash
git pull --ff-only
bash deploy.sh
```

每一次执行都重新构建并部署新版 API/Web、清空独立 PostgreSQL 中的 erp_next 数据库及新版私有附件，重新初始化唯一 V1 Schema 和 admin。即使代码未变化也是全新部署，不备份、不升级、不恢复旧测试数据。fresh 是兼容别名。镜像与构建缓存可复用。旧生产 server、旧附件和以前已保存的归档不参与此过程。

访问 http://localhost:8089/next.html，账号 admin，密码 123456。仅绑定本机回环端口；禁止用于生产或将端口转发至公网。正式账号密码策略保持不变。

- 状态：bash deploy.sh status
- 日志：bash deploy.sh logs
- 停止：bash deploy.sh stop
- 再次部署：bash deploy.sh（清空测试数据）

默认专用状态目录 ~/.local/share/zjerp-trial；可设置 ERP_TRIAL_DIR，首次创建时可设置 ERP_NEXT_WEB_PORT / ERP_NEXT_API_PORT。已绑定共享 MySQL 的旧状态目录拒绝接管，改用新的独立目录；不探测、修改、停止旧生产数据库或旧容器。

自动化测试运行 bash test.sh（默认新版构建和隔离 API smoke，日志 test-results/），或 bash test.sh --smoke。测试不使用体验部署的容器、卷或业务数据。

生产迁移暂停：旧版迁移代码暂时保留，测试部署不再提供旧库检测、导入、归档、映射、预演和迁移命令。完成正式版后，单独制定生产数据迁移与备份方案。生产部署不得使用本页脚本。

数据库已切换 PostgreSQL 17。首次新版部署自动移除当前试用项目的旧 MySQL/API/Web/Schema 容器；旧 MySQL 数据卷、旧生产库及归档不自动删除。后续每次部署删除独立 PostgreSQL 测试卷并重新初始化。账号仍为 admin / 123456。
