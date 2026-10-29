# 资料与脚本索引

- codex/：当前设计、规范、开发方式、现网事实与计划。
- DEPLOYMENT.md：Compose 新部署及已有库的边界。
- scripts/init-database.sh：Compose 和原生开发共用的空库初始化入口。
- scripts/dev-backend.sh、ry.sh：全模块重新构建后启动开发后端。
- scripts/migrate-work-projects.py、migrate-file-metadata.py：仍保留的版本化数据迁移实现，禁止作为日常启动命令。
- sql/：初始化所需结构和菜单，以及已明确命名的历史升级 SQL；只有 init-database.sh 列出的文件参与全新安装。
- WORK_PROJECTS.md、CONTRACT_MODULES_UPGRADE.md、USER_MATERIALS.md、ROLE_ONLY_UPGRADE.md：模块接口及升级说明。
- codex/PROJECT_FINANCE.md、sql/project_finance_schema.sql、sql/project_finance_menus.sql、scripts/migrate-project-finance.py：项目文件/维保资料面板、统一报账及历史报告迁移；迁移脚本仅供已备份的已有库升级，不属于日常启动。
- 前端 DESIGN_SYSTEM.md：所有新界面的设计规范。

2026-10-29 清理 FILE_MIGRATION、FILE_STORAGE、PRODUCTION_MIGRATION_A_TO_B、旧总计划和 task 文档，以及完成迁移的一次性机器操作脚本。历史副本在工作区 local-archive/cleanup-20261029；不上传 GitHub。现网备份和上传原件继续保存在 B，不随源码清理。
