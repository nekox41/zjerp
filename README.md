# 众基 ERP 后端

Java 17、Spring Boot 3、MyBatis、MySQL 8 和 Redis 组成的多模块后端。前端独立维护在 [zjerp_frontend](https://github.com/nekox41/zjerp_frontend)。

## 一起部署前后端、MySQL 和 Redis

目录保持为相邻的两个仓库：

```text
zjerp/
├── zjerp_backend/       # 在这里运行 Compose
└── zjerp_frontend/
```

```bash
git clone https://github.com/nekox41/zjerp_backend.git
git clone https://github.com/nekox41/zjerp_frontend.git
cd zjerp_backend
python3 scripts/init-compose-env.py
# 编辑 .env：将 PUBLIC_API_URL 改为真实访问地址，例如 https://erp.example.com/prod-api。
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

默认访问 `http://localhost:8088`。首次启动空 MySQL 数据卷时自动创建框架、业务和统一文件表；不会导入业务备份。框架初始管理员为 `admin / admin123`，登录后修改密码。Redis 是登录会话及配置缓存的必需依赖。

**已有数据库请先阅读 [容器部署说明](DEPLOYMENT.md) 的旧库导入流程，不能把全新初始化当作升级步骤。**

## 文件系统迁移

附件元数据和业务关联统一保存在 `sys_file`。新上传采用 `files/<哈希前两位>/<UUID>.<扩展名>`，原始文件名只作为元数据保存。

[文件系统迁移步骤](FILE_MIGRATION.md) 包含备份、仅数据库迁移、带原文件的迁移、核对及回滚。缺少原文件时，哈希和大小保留为 NULL；不会伪造内容信息。已经仅迁移数据库的记录需要补回原文件后才能下载。

[文件存储设计](FILE_STORAGE.md) 描述业务类型、接口兼容和删除策略。

## 本地构建与运行

先配置 `zjerp-admin/src/main/resources/application-dev.yml` 对应的测试 MySQL、Redis，或通过环境变量覆盖连接参数。

```bash
mvn -Dmaven.test.skip=true package
bash scripts/verify-file-storage.sh
java -jar zjerp-admin/target/zjerp-admin.jar --spring.profiles.active=dev
```

后端默认端口为 `8080`。普通 Maven 测试命令为 `mvn test`；文件服务额外检查由上面的独立脚本执行，不依赖数据库或 Redis。

## 模块

下表为现有 Maven 工程模块。后续业务开发拆分为 13 个逻辑模块，功能、依赖和验收清单见 [业务模块实施计划](MODULE_PLAN.md)。

| 模块 | 用途 |
| --- | --- |
| zjerp-admin | 应用入口、系统和通用接口 |
| zjerp-framework | 安全、数据源、配置与基础设施 |
| zjerp-system | 系统管理、统一文件服务 |
| zjerp-management | 合同、报告、维保卡等业务 |
| zjerp-generator | 代码及维保卡文档生成 |
| zjerp-common / zjerp-quartz | 公共工具与任务调度 |

## 文档

- [业务模块实施计划](MODULE_PLAN.md)
- [系统设计方案](SYSTEM_DESIGN.md)

- [Docker 与 Compose 部署](DEPLOYMENT.md)
- [文件系统迁移步骤](FILE_MIGRATION.md)
- [统一文件存储设计](FILE_STORAGE.md)

`.env`、上传文件、日志和数据库备份均不应提交到 Git。仓库已有的历史完整 SQL 备份不参与自动初始化，也不会进入 Docker 构建上下文。
