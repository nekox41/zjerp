# 众基消防合同模块

## 业务入口

首页下方新增“众基消防”，包含中间人管理，以及合同管理下的维护保养、消防检测、消防评定、消防评估、其他技术服务五个模块。

中间人维护姓名、电话、单位、邮箱、备注。合同维护编号、名称、金额、日期、归属、付款计划、附件和备注。旧“负责人”原文实际为中间人归属，已移除独立负责人字段；具体迁移见 WORK_PROJECTS.md。付款计划包含阶段名称、金额、计划日期，不代表已收款流水。

合同归属为 `pending`（待确认）、`company`（公司）、`intermediary`（中间人）。关联中间人必须有效；被合同引用时不可删除。

## 存储与迁移

- 新表：`zj_contract_maintenance`、`zj_contract_inspection`、`zj_contract_rating`、`zj_contract_assessment`、`zj_contract_other`。
- `zj_contract_identity` 分配全局 ID，新增时与对应合同表在同一事务写入。删除通过外键级联删除对应合同记录。
- 原始表完整重命名为 `zj_contract_legacy_20261028`，仅供审计、回退核对。应用不再向此表写入。
- `zj_contract` 为五张新表的 UNION ALL 兼容视图，旧查询、统计接口继续使用原名称。写入由服务器上的类型白名单路由，客户端不能指定表名。
- 原合同 ID 保留，附件继续通过 `sys_file` 的 `contract + id` 关联，文件不复制、不丢弃。
- 历史金额由原 FLOAT 的 JSON 数值表示转为 DECIMAL(30,16)，原 FLOAT 同时保留在归档表。接口将金额作为字符串传输；未修改历史金额时不按两位小数重写，新输入金额最多两位小数。
- 所有历史归属留空语义（存储 pending），页面显示待确认；历史付款计划不推测，保持未填写。
- 历史 WB→维护保养301，JC→消防检测47，PD→消防评定25，PG→消防评估2，QT/YS/空→其他5。2份YS及1份空类型保留 legacy_type，并标记 classification_pending。

## 升级

先停应用并保存配套数据库、文件及配置备份。依次执行 `sql/zhongji_contract_upgrade.sql`、`sql/zhongji_contract_menus.sql`，部署前后端。脚本包含建表和重命名，只允许执行一次；不要对已升级库重复运行。全新部署由 MySQL 初始化脚本按顺序执行。

原合同菜单被移动为维护保养合同入口，保留 ID 和已有按钮权限。原合同角色被赋予新合同目录及五类页面，合同接口仍使用 `management:contract:*` 权限。中间人单独使用 `management:intermediary:list/edit`，管理员可在角色管理中授权。合同编辑者可通过受保护的 options 接口搜索中间人。

升级前后须逐条核对合同字段、金额、归属映射、归档、附件记录以及其他业务表。切换前在隔离数据库验证五类合同增删改查、付款计划校验、中间人引用约束、角色权限、分页和新装脚本。

回退需要旧镜像和升级前数据库配套恢复；升级后已有新业务写入时，应先导出并核对新增、修改数据，不能直接覆盖恢复。

## 前端规范

详见前端根目录 `DESIGN_SYSTEM.md`。共享样式为 `src/assets/styles/zhongji.scss`，五类页面复用 `ContractWorkspace.vue`，避免重复实现表单和文件交互。
