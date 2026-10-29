# 资料与脚本索引

- codex/：交接设计、规范、计划及历史操作工具。
- codex/operations/20261028/：合同、用户资料、项目的检查、演练、验证、发布和清理脚本；仅在核实目标后使用。
- scripts/migrate-work-projects.py：版本化的一次性项目业务迁移。
- sql/：框架、文件、角色、用户资料、合同及项目结构/升级SQL。
- WORK_PROJECTS.md、CONTRACT_MODULES_UPGRADE.md、USER_MATERIALS.md、ROLE_ONLY_UPGRADE.md：对应模块说明。
- FILE_MIGRATION.md、FILE_STORAGE.md、PRODUCTION_MIGRATION_A_TO_B.md、DEPLOYMENT.md：原迁移和部署设计，部分是历史方案，实际状态以CURRENT_STATE和私密发布记录为准。
- 前端DESIGN_SYSTEM.md：界面规范；src/views/zhongji与共享样式：当前业务界面实现。
- 工作区local-archive/：源代码压缩包、初期迁移目录、旧版变换脚本、缓存清单和私密运维记录，保留本地并忽略提交。变换脚本可能重复修改源码，禁止当作可重跑构建工具。

所有原根目录文件的整理去向记录在本地local-archive/organization-manifest.json。生产备份和原件继续保存在B，不移动线上挂载目录。
