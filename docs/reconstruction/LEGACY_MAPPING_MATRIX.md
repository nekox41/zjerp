# 生产数据到新 ERP 的迁移映射矩阵（初版）

> v0.1 / 2026-11-21。此矩阵由生产快照表清单和现有代码审计形成，是目标模型的映射起点，不是最终字段级转换规格。所有映射必须在 schema review、业务规则确认和 dry-run 后冻结版本。

## 1. 迁移原则

- 来源键统一为 `(source_system, source_table, source_pk)`；报告业务编号、项目编号和姓名均不能替代来源主键。
- 目标 ID 重新分配；所有旧 ID 可通过 Legacy Identity Map 反查。
- 对每条源记录必须有明确去向：导入、作为历史快照保留、明确排除、待人工确认或失败。
- 不确定的身份、合同/项目关联、历史付款状态、文件存在性不自动推断。
- 空表仍记录在 source manifest 中，但不需要为它们制造业务记录。
- 所有 JSON、TEXT、文件路径和历史快照先保留原文，再逐字段规范化；不得静默丢弃未知字段。

## 2. 业务数据映射

| 来源表 | 行数 | 新系统目标域/实体 | 初步策略 |
|---|---:|---|---|
| zj_contract_identity | 397 | Contracts / LegacyIdentityMap | 建立统一合同来源身份，保留旧合同 ID 与类型 |
| zj_contract_assessment | 2 | Contracts / ContractTypeDetails | 映射 PG 专属字段，关联旧合同 identity |
| zj_contract_inspection | 49 | Contracts / ContractTypeDetails | 映射 JC 专属字段 |
| zj_contract_maintenance | 315 | Contracts / ContractTypeDetails | 映射 WB 专属字段 |
| zj_contract_other | 6 | Contracts / ContractTypeDetails | 映射 QT 专属字段 |
| zj_contract_rating | 25 | Contracts / ContractTypeDetails | 映射 PD 专属字段 |
| zj_contract_legacy_20261028 | 380 | Contracts / HistoricalSnapshot | 作为迁移前历史快照保留，不覆盖当前合同字段 |
| zj_intermediary | 31 | CRM / Intermediary | 独立主体；合同归属关系映射为显式关联 |
| zj_work_project | 536 | Projects / Project | 项目主档；保留待编号/待分类和空字段语义 |
| zj_work_project_legacy | 552 | Migration / LegacyIdentityMap + LegacySnapshot | 保留源表、源主键、原始 JSON 和来源类别 |
| zj_project_code | 198 | Projects / ProjectNumberAlias | 旧项目编号来源，保留编号和原始分类 |
| zj_work_project_number | 537 | Projects / ProjectNumberHistory | 保留正式编号与历史别名；同项目多编号不合并丢弃 |
| zj_work_project_sequence | 4 | Numbering / NumberSequence | 迁移编号序列前核对年度/类型最大已分配编号 |
| zj_work_project_migration | 1 | Migration / BatchReport | 迁移批次证据和历史待处理统计 |
| zj_work_project_issue | 0 | Projects / ProjectIssue | 当前空表，保留 schema provenance，不生成记录 |
| zj_work_project_plan | 0 | Projects / ProjectPlan | 当前空表，保留 schema provenance，不生成记录 |
| zj_project_document | 497 | Documents / DocumentLink + ProjectArchive | 保留 352 报告档案和 145 附件档案的用途、项目关系与旧报告来源 |
| zj_report | 352 | Reports / LegacyReport | 以旧 report.id 作为来源键；report_id 重复/空值不影响唯一性 |
| zj_inspection_report | 0 | Reports / IssuedReport | 当前空表；目标系统仍可有新版正式报告模型 |
| zj_report_credential | 0 | Workforce / Credential | 当前空表；历史报告快照/附件仍需单独调查 |
| zj_employee | 19 | Workforce / Employee | 保留 active、待确认类型和旧 user 映射 |
| zj_employee_migration | 1 | Migration / BatchReport | 保存迁移批次来源和摘要，不当作员工实体 |
| sys_user | 18 | Identity / UserAccount | 单独迁移认证主体；不与员工档案合并 |
| sys_user_role | 19 | Identity / UserRoleAssignment | 仅按新角色策略映射，不盲目复制旧授权 |
| sys_role | 5 | Identity / LegacyRoleSnapshot | 旧角色供权限映射参考，重新审核权限最小化 |
| sys_menu | 136 | Identity / LegacyPermissionCatalog | 作为旧权限目录参考；新权限按用例重新定义 |
| sys_role_menu | 74 | Identity / LegacyRolePermissionSnapshot | 只用于构造权限映射和审计，不直接作为新权限最终结果 |
| sys_file | 2,193 | Documents / FileObject + DocumentLink | 元数据迁移；二进制必须从独立文件存储扫描、复制和 hash 校验 |
| zj_wbcard | 304 | ServiceDelivery / MaintenanceCard | 保留维保卡业务记录、状态、旧 ID |
| zj_wbcard_number | 303 | ServiceDelivery / MaintenanceCardNumberHistory | 保留卡片编号关系；重复编号待人工核对 |
| zj_wbcard_sequence | 1 | Numbering / NumberSequence | 作为编号序列初始值候选，核对最大现存编号后导入 |
| zj_wbcard_material | 4 | ServiceDelivery / MaintenanceMaterial | 资料元数据；关联文件对象而非直接复制 URL |
| zj_project_material | 13 | Projects / ProjectResourceLink | 映射项目、成品资料、维保卡关系 |
| zjerp_wbcard_phone | 13 | ServiceDelivery / MaintenanceCardContact | 作为维保卡联系方式/历史扩展，先确认字段含义和隐私规则 |
| zj_reimbursement | 2 | Finance / ExpenseClaim | 保留申请金额、状态、来源、申报人、幂等来源和历史快照；不推断付款 |
| zj_cost | 1 | Finance / LegacyExpense | 旧费用事实，保留 double 原值文本和快照；核实精度后再转 decimal |
| zj_project_cost | 1 | Finance / LegacyProjectCostSummary | 历史汇总快照，不作为账户流水 |
| zj_work_project_expense_link | 1 | Finance / ExpenseProjectAllocation | 保留旧费用与项目/人员关联证据 |
| zj_project_finance_migration | 1 | Migration / BatchReport | 保留报告匹配与历史费用迁移摘要 |
| zj_finance_account | 0 | Finance / FinancialAccount | 当前无账户记录；不创建虚构期初账户 |
| zj_finance_transaction | 0 | Finance / CashTransaction | 当前无流水；不造账 |
| zj_finance_invoice | 0 | Finance / Invoice | 当前无发票；不造票 |
| categories | 2 | Workflow / TodoCategory 或明确排除 | 先核实待办功能是否仍属于 ERP 业务 |
| todos | 4 | Workflow / Task | 保留待办、类别和历史状态；确认是否仍需迁移 |
| templates | 5 | Reports / Template 或 Workflow / Template | 先识别模板用途、格式和版本，再归属目标域 |
| zjerp_project_system | 1 | Projects / LegacyProjectSourceSnapshot | 保留旧项目系统原始记录和来源键 |
| sys_user_material | 0 | Workforce / LegacyCredentialSource | 当前空表；保留 schema 记录 |

## 3. 平台与运维数据映射

| 来源表 | 行数 | 处理策略 |
|---|---:|---|
| sys_config | 5 | 逐项白名单迁移；密钥、令牌和环境地址重新配置，不复制环境秘密 |
| sys_dict_type | 15 | 将确实属于业务的字典转换为新枚举/可配置字典；技术字典不必照搬 |
| sys_dict_data | 44 | 与字典类型一起映射，未知/废弃值保留历史文本 |
| sys_job | 3 | 重新定义新系统任务；不直接复制旧执行器配置 |
| sys_job_log | 0 | 当前空表 |
| sys_logininfor | 1,118 | 安全审计归档；按法规/内部保留期决定是否迁移，脱敏并限制访问 |
| sys_oper_log | 2,743 | 操作审计归档；保留操作者/对象/时间语义，脱敏后迁移或只读归档 |
| sys_notice | 2 | 确认是否为有效业务通知；过期通知可归档而非进入活动通知 |
| qrtz_blob_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_calendars | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_cron_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_fired_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_job_details | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_locks | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_paused_trigger_grps | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_scheduler_state | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_simple_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_simprop_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| qrtz_triggers | 0 | 旧 Quartz 运行态，不迁移 |
| gen_table | 4 | 代码生成器元数据，通常不属于业务数据；归档或排除 |
| gen_table_column | 41 | 代码生成器元数据，通常不属于业务数据；归档或排除 |

## 4. 需要优先冻结的映射规则

1. **合同**：identity 与类型扩展表必须在同一迁移事务/批次中完成；历史表 `zj_contract_legacy_20261028` 不可覆盖当前记录。
2. **项目**：项目主档、旧项目编号、报告匹配来源、历史项目系统快照分开导入；每条自动匹配保存算法/规则版本和匹配依据。
3. **报告**：以 `zj_report.id` 作为来源键，重复的 `report_id` 仅作为普通业务字段；报告最终/非最终状态不得合并。
4. **文件**：FileObject 与 DocumentLink 分离；相同 URL 在不同业务上下文出现时优先复用同一个对象，保留多条引用。
5. **人员**：UserAccount、Employee、Credential 分开；旧角色/菜单仅用于授权映射审计，不直接复制权限。
6. **财务**：ExpenseClaim 与 CashTransaction 分开；当前空的 account/transaction/invoice 表保持空，不造历史账。
7. **编号**：先验证序列值大于所有有效编号的最大序号，再迁移 sequence；旧别名不参与新编号生成。
8. **日志**：日志迁移与业务迁移分批；敏感字段脱敏、保留期和访问权限单独审批。

## 5. 生产演练所需补充材料

- 对应快照的附件/文件存储目录或对象存储只读清单（不需要把所有文件上传到 Git）。
- 源数据库版本、时区、导出方式和一致性说明。
- 业务确认：重复报告编号、重复维保卡编号、待确认项目类型、待确认人员类型、历史费用口径。
- 目标系统已冻结的实体模型与 mapping version。

## 6. 映射状态

当前矩阵只达到“表级归属 + 初步策略”。在完成每张表的列级映射、状态枚举映射、JSON 路径盘点、文件路径解析、金额精度测试和 dry-run 之前，不得用于生产正式迁移。