# WSL2 一键测试、部署与旧数据迁移

## 1. 环境

Ubuntu 22.04 / WSL2，Docker Engine 或 Docker Desktop WSL 集成，Compose v2。建议克隆到 ~/zjerp 而不是 /mnt/c。当前用户须能直接运行 docker info，不要 sudo 执行脚本。缺少基础工具时：

```bash
sudo apt update
sudo apt install -y git python3 curl util-linux
```

## 2. 一键测试

```bash
bash test.sh
```

默认执行新版及旧版 Maven verify（容器内），前端生产构建、V1–V22 数据库迁移与 API Smoke（独立随机项目及卷），完整 stdout/stderr 写入 test-results/test-*.log。失败返回非零；发送该日志即可。可用 --static、--new-only、--smoke 缩小范围；本地集成测试由 Docker 分配两个临时回环端口，不占用已有 ERP 的 8089/8090。无需本机安装 Maven、JDK、Node 或 npm。

## 3. 一键部署

```bash
bash deploy.sh
```

访问 http://localhost:8089/next.html，账号 trial-admin，初始密码使用 `cat ~/.local/share/zjerp-trial/admin-password` 在本机查看。勿把密码文件或 .env 发给他人。默认 API/Web 仅绑定回环；MySQL 不映射宿主机端口。状态目录可通过 ERP_TRIAL_DIR 指定（仓库外 Linux 文件系统），首次启动可通过 ERP_NEXT_WEB_PORT、ERP_NEXT_API_PORT 修改端口。以后同一状态目录会复用凭据、数据库卷和附件；stop 不删除数据，start 可继续运行。普通生产部署仍使用 start.sh，两者不能混用。

## 4. 导入旧数据副本（只在验收环境）

先在旧系统安全获取 SQL 备份及完整上传目录副本，保留原始备份。SQL 应为单 schema 导出，不含 CREATE DATABASE、USE server、账户授权等全局管理语句；不要把旧生产连接地址提供给本脚本。不自动改写 SQL，导入失败时需核对兼容性与部分导入状态。

```bash
bash deploy.sh legacy-import /absolute/path/legacy.sql
# 或使用隔离旧库的交互式 MySQL：
bash deploy.sh legacy-shell
# 复制旧版上传目录内容，保持相对路径
cp -a /absolute/path/legacy-uploads/. ~/.local/share/zjerp-trial/legacy-files/
```

导入只针对独立 legacy-mysql 的 server_migration，非空时拒绝重复导入。新版“数据迁移”页可直接扫描此旧库，只读账户仅 SELECT/SHOW VIEW。检查冲突后冻结旧库及附件副本，不再写入。

## 5. 迁移

```bash
bash deploy.sh archive
bash deploy.sh plan
# 编辑 ~/.local/share/zjerp-trial/review.json，按 LEGACY_MIGRATION.md 逐项确认
bash deploy.sh preview
# 核查预演输出；apply 自动创建成对备份，替换为 preview 输出的完整 planSha256
bash deploy.sh apply PLAN_SHA256
bash deploy.sh reconcile
bash deploy.sh
```

Python MySQL 驱动在专用容器内安装，宿主机不需要 pip。archive 与 plan 不覆盖旧档案；preview 自动停止新版 Web/API，事务回滚；apply 要求准确计划哈希及同一隔离目标凭据；reconcile 逐项验证映射和附件。迁移失败保留现状供核查，不自动删除、重置或重新启动业务。迁移工具不会自行推断甲方、合同归属、员工角色或签发状态。实际数据核对与备份恢复仍需人工完成。

## 6. 反馈

测试反馈发送 test-results/*.log；实际部署问题可用 bash deploy.sh status 或 bash deploy.sh logs（Ctrl+C 退出）。真实业务数据、日志、SQL、原件、.env、admin-password 均可能包含敏感信息，分享前脱敏。该环境专用于候选版验收，不视为正式生产迁移。禁止 docker compose down -v 或清理全部 Docker 卷。

`bash deploy.sh backup` 可单独创建成对备份（停止 Web/API），`apply` 每次写入前亦自动创建。备份位于状态目录 backups/，只有 COMPLETE 标识存在且 SHA256SUMS 检查通过才视作完整备份；恢复仍需另行隔离演练。请勿将试迁移环境直接升格为正式生产。

再次启动时会停止新版应用写入再运行 Flyway；若 Git 提交相较上次成功启动有变化，会先保存成对备份。请在仓库根目录执行所有命令。旧版 SQL 如含 `USE server` 或 `CREATE DATABASE`，请从离线备份重新导出单 schema SQL（`mysqldump ... server`，不要使用 `--databases`）；不要直接修改真实生产数据。

## Docker Hub 拉取超时

若日志在 Maven 容器启动前出现 registry-1.docker.io:443 i/o timeout、exit 125，属于 Docker 镜像网络故障，而不是 Java 测试失败。test.sh 与 deploy.sh 会先检查本地镜像，缺失时最多尝试两次拉取并明确报错，不自动修改 Docker daemon 或切换到陌生镜像站。已缓存镜像可复用。

先单独运行 `docker pull maven:3.9.9-eclipse-temurin-17`。WSL 内运行 Docker Engine 时请按 https://docs.docker.com/engine/daemon/proxy/ 为 daemon 配置可达的 HTTP(S) 代理；使用 Docker Desktop 时在 Desktop 设置代理。Windows 代理需确认监听地址/端口与 WSL 网络模式，不要假定 127.0.0.1 可达。仅在终端 export HTTP_PROXY 不等于为 daemon 配置代理。重启 daemon 前检查其他容器。

也可以显式设置 `ERP_IMAGE_PREFIX`（以 / 结尾、无 URL scheme）为你信任且具备全部原始仓库路径和标签的镜像仓库前缀；例如内部仓库的 `registry.example.com/mirror/` 下有 maven、eclipse-temurin、node、nginx、mysql、flyway/flyway、python 对应镜像。此为示例占位符，不是可用公共镜像地址。test.sh 与 deploy.sh、Compose 及 Dockerfile 共用该前缀；再次部署需使用同样设置。也可离线 docker save/load 原标签镜像。Maven/npm/apt/pip 的下载网络仍需分别可达。不会自动禁用 TLS 或使用不明第三方镜像。

### 测试端口与依赖缓存

本地 Smoke 使用独立随机 Compose 项目及 Docker 自动分配的两个回环端口，只有从该项目查询到的端口会传给所有 API 测试（含 Python 测试）。既有 ERP 可以保持运行。新版和旧版 Maven 容器使用专用 `zjerp-local-test-maven-repository` Docker 卷缓存依赖；不缓存项目构建产物，不使用部署数据库。首次仍需要下载依赖；后续执行 `bash test.sh --smoke` 可直接复测数据库和 API，不重复 Maven 单测（后端 Docker 构建仍会执行）。
