# 众基消防项目工作台

## 模块

众基消防 → 项目管理下有维护保养、检测、评定、评估和其他技术服务五类项目。每个项目关联同类型合同。项目负责人、技术负责人选择系统用户，供未来报告系统按用户 ID 获取证书资料。

项目工作台按页维护：基本信息、详细信息（建筑概况、面积、高度、使用性质、设施、服务范围和报告依据）、登记信息、现场问题、备注、计划、报账总览。各页独立保存，基本信息、详细信息、登记和备注使用版本号拒绝过期覆盖。问题支持照片/文件、程度、进展及发现和解决日期；计划支持现场作业、完工、出报告和其他节点。

报账只读，包含项目金额总览、按用户汇总和明细。沿用原开销记录，历史参与人员只有与唯一系统用户匹配时才关联用户，否则标记待确认。多人参与文本不做金额分摊。历史账面汇总与实际明细分别显示，不自动调整差异。本次没有新增报账录入接口。

## 编号

新建项目时在同一事务内锁定“年份 + 项目类型”计数器，保存成功才占用正式编号：`ZJXFYYYYMM类型序号`，序号年度内按类型递增，最少三位。保留所有历史编号，计数器从有效旧编号的最大序号继续。取消表单不申请编号。

旧编号缺失、格式错误或重复时，保留原编号并标记待核实；无法使用的旧记录以 LEGACY 标识暂存。先关联合同后可申请正式编号，原编号仍在保留清单中，不会重新分配给其他项目。类型待确认且尚无正式编号的迁移记录可以核实类型并选取对应合同。

## 历史迁移

生产已知来源：198条 `zj_project_code`、1条 `zjerp_project_system`、1组 `zj_project_cost` 及1条 `zj_cost` 明细。逐条保留编号申请，不按重复名称合并。旧系统和开销组只在项目名称唯一匹配时合并，否则创建独立历史项目。合同仅在“名称完全相同 + 类型一致 + 唯一候选”时关联，其他留待确认。

原表不删除。`zj_work_project_legacy` 保存来源表、来源ID及原始快照；原系统资料也放入详细信息的只读历史区。登记状态默认未登记，不推测已经到场或报告已经完成。

合同原 manager 字段是中间人归属原文，不再作为合同负责人出现在 API、表单和列表中。未确认归属的历史合同：明确“公司”转公司，明确人名匹配或创建中间人，空白、斜杠、未知或歧义名称留待确认。用户已经确认的归属和已有中间人资料不覆盖；原文继续在数据库中保留审计。

旧项目管理和编号申请菜单退出导航。旧项目系统写接口关闭；保留历史二维码的只读查询兼容入口。

## 权限与接口

权限由角色授予：`management:project:list/query/add/edit`，报账单独为 `management:project:expenses`。旧编号/项目菜单角色按对应操作迁移；不会自动给普通项目角色财务权限。

- `GET /management/work-project/list`：按项目类型、名称/编号、待关联合同筛选，服务端分页。
- `POST /management/work-project`：创建项目并申请编号。
- `GET /management/work-project/{id}`：详情。
- `PUT /management/work-project/{id}/section/{basic|details|registration|notes}`：页面保存，必须带 version。
- `POST /management/work-project/{id}/apply-number`：历史项目申请正式编号。
- `GET /management/work-project/contracts`、`/users`：有限字段的远程选择器。
- `GET/POST /management/work-project/{id}/issues`、`/plans`：问题和计划；DELETE 对应子项路径删除。子项更新必须属于路径指定的项目。
- `GET /management/work-project/{id}/expenses`：只读财务总览与分页明细。

## 运维

先在独立数据库演练，停止生产应用并做数据库、上传文件、模板、配置的配套备份。运行一次：

```sh
python3 scripts/migrate-work-projects.py --mysql-container YOUR_MYSQL_CONTAINER --database YOUR_DATABASE --apply --report /PRIVATE/project-migration.json
# 再在同一数据库执行 sql/work_projects_menus.sql
```

脚本拒绝在已初始化的项目表上重复运行。SQL DDL不是全局事务，失败时保持应用停止，先检查独立演练结果和备份，不能跳过错误或盲目重跑。全新安装由 MySQL 初始化脚本加载空项目表和菜单。

上线前逐表核对原业务数据、附件、编号、快照和开销关联。上线后使用独立只读脚本验收，不在生产创建测试合同、项目或费用。
