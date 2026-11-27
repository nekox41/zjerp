# ERP Next 开发预览版（禁止用于生产）

Ubuntu / WSL 或 Debian，普通 Linux 用户执行：bash deploy.sh

首次检测并安装缺少的 Java 17 JRE、Nginx、PostgreSQL 17、Python 3、curl 等运行依赖，必要时需要 sudo。无需 Docker、Maven、Node.js、npm 或源码。

## 每次部署的固定流程

始终使用同一个私有 PostgreSQL 集群和同一个数据库名 erp_next：

1. 连接本工具管理的 PostgreSQL 17 实例。
2. 终止 erp_next 的现有连接。
3. 执行 DROP DATABASE IF EXISTS erp_next WITH (FORCE)。
4. 创建同名 erp_next 数据库并执行当前 Release 唯一完整的 db/schema.sql。
5. 覆盖 API JAR，停止旧 API 后直接启动新版 Java 服务。
6. 覆盖 Nginx 静态文件目录，检查配置，然后 reload 现有 Nginx master；首次部署才启动 Nginx。
7. 清空预览附件目录，初始化 admin 并验证登录。

只删除本工具私有 PostgreSQL 实例中的 erp_next，不删除 PostgreSQL 集群、不删除其他数据库、不连接系统默认 PostgreSQL 端口、不接触旧 MySQL/旧生产库。PostgreSQL 服务在部署间保持运行。附件每次部署清空。

## 进程与并发

部署入口使用固定状态目录中的文件锁。再次运行 bash deploy.sh 时，会识别并终止持有该锁的旧部署脚本，再开始新部署。服务进程使用 PID、内核启动时间、UID、真实可执行文件和专属 JAR/PID 文件识别；无法确认归属时拒绝发送信号，避免误杀其他服务。

## 访问与管理

状态目录：~/.local/share/zjerp-native-preview。默认端口：PostgreSQL 55439、API 8090、Web 8089，全部绑定 127.0.0.1。首次运行前如其他程序占用端口，请先停止对应服务。首次运行前可通过 ERP_PREVIEW_WEB_PORT、ERP_PREVIEW_API_PORT、ERP_PREVIEW_DB_PORT 设置端口，之后保存在状态目录中。

访问 http://localhost:8089/next.html，账号 admin，密码 123456。只使用虚拟测试资料，不要放入真实身份证件或合同，不要对外开放端口。

常用命令：
- bash deploy.sh status
- bash deploy.sh logs
- bash deploy.sh stop
- bash deploy.sh

stop 会停止本工具管理的 Nginx、API 和 PostgreSQL 服务，但保留 PostgreSQL 集群文件；下次 start 只重建同名 erp_next 数据库。本包仅用于开发预览，不提供正式生产部署或生产数据迁移功能。
