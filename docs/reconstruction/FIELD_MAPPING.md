# 核心表字段级迁移映射（初版）

> v0.1 / 2026-11-21。只定义可由 schema/快照支持的映射和待确认点。旧字段原值必须保存在来源快照中；本表不是可直接运行的 ETL 规格。

## 1. 合同

源表：`zj_contract_identity` + 五类合同表（assessment / inspection / maintenance / other / rating）。

**重要结构发现：五类合同扩展表字段名、顺序完全相同，均为 19 列。** 目标模型可将这些表合并为一个合同实体，类型直接映射到 `contract.type`，无需照搬旧的五张同构表。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| identity.id / subtype.id | legacy_identity_map.source_pk / contract.id 映射 | 以 subtype id 为同一旧合同身份；两处 ID 必须一致 |
| identity.contract_type / subtype.contract_type | contract.type | 两值必须一致；未知类型进入异常队列 |
| contract_id | contract.external_number | 语义需业务确认；保留原文，不直接认定为正式合同编号 |
| contract_name | contract.name | 保留原文；不得用名称去重 |
| start_time / end_time | contract.start_date / end_date | DATE 原样转换；无值保持 NULL |
| manager | contract.manager_text 或 manager_party/person link | 先保留原始文本；不能按姓名自动关联员工 |
| amount | contract.amount | DECIMAL(30,16) 转高精度 Decimal；禁止浮点 |
| ownership | contract.ownership_status | 建立显式枚举映射；未知值保留原值 |
| intermediary_id | contract_intermediary.intermediary_id | 通过旧 ID map 关联；非空外键已核对有效 |
| payment_plan | contract.payment_terms_legacy_text | 原文保留；未确认结构前不自动生成应收计划 |
| contract_file | contract_document/file link | 解析路径并与 sys_file 交叉核对；附件缺失时标记 missing |
| legacy_type / classification_pending | legacy attributes + contract classification review | 保留旧分类与待确认标记 |
| create_by / update_by | audit legacy_actor | 仅作为历史文本，不转成新系统用户 ID |
| create_time / update_time | audit legacy timestamps | 按源时区解释；导出连接时区为 UTC，需核实应用日期语义 |
| remark | contract.remark | 原文保留 |

## 2. 项目

源表：`zj_work_project`、`zj_work_project_number`、`zj_project_code`、`zj_work_project_legacy`。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| id | legacy_identity_map + project.id | 稳定来源键 |
| project_type | project.type | 已知 JC/PD/WB/PG/QT 映射；其他值待确认 |
| project_no | project_identifier | 当前主编号候选；若 number_pending=1 则不强行当正式编号 |
| legacy_number | project_identifier (legacy) | 历史编号单独保存，不覆盖 project_no |
| number_pending | project.number_status | 转为 explicit pending/confirmed；保留原值 |
| classification_pending | project.classification_status | true 时进入待确认队列 |
| project_name | project.name | 原文保留；不可用名称作为唯一键 |
| contract_id | project.contract_id | 可空；旧数据只有 13/536 非空，保留原关联 |
| client_name | project.client_party_name_legacy | 先保留文本；不得自动创建或合并客户主体 |
| site_address | project.site_address | 原文保留 |
| contact_name / contact_phone | project.contact snapshot | 联系人主体匹配需确认；联系方式按敏感信息权限处理 |
| leader_employee_id / technical_employee_id | project_member | 通过 Employee source map；当前非空引用已核验 |
| leader_user_id / technical_user_id | legacy user-role reference | 兼容旧字段；与 employee ID 分开处理，禁止按字段名误认 |
| start_date / end_date | project.start_date / end_date | DATE 保持 NULL 语义 |
| details_json / registration_json | project legacy payload + recognized structured fields | 先保存完整原 JSON，再按 schema 版本解析；当前仅各有 1 条非空 |
| remark | project.remark | 原文保留 |
| created_by / updated_by | audit legacy_actor | 历史文本，不映射新账号 |
| created_at / updated_at | audit timestamps | 按源时区核验 |
| version | legacy_version | 只保留来源版本信息，不当作新系统乐观锁初始版本 |
| zj_work_project_number.project_no | project_identifier | 每条编号都保留；同一 project_id 可能有正式编号和 legacy alias |
| zj_project_code.code / name / type / index / remark | project_identifier + legacy snapshot | 旧编号来源记录单独保留，匹配依据可追溯 |
| zj_work_project_legacy.source_table / source_id / snapshot_json | legacy_identity_map + legacy_snapshot | 唯一来源键为 source_table + source_id；snapshot 原文保存 |

## 3. 报告与项目档案

源表：`zj_report`、`zj_project_document`。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| zj_report.id | legacy_identity_map.source_pk / report.id | 唯一迁移键；不得用 report_id |
| report_id | report.report_no_legacy | 允许重复、允许空；新编号规则另行制定 |
| report_name | report.title | 原文保留 |
| report_type | report.type | JC/PD/PG 映射；1 条为空，进入待确认 |
| report_isfinal | report.legacy_final_flag | Y/N 保留原值，再映射为历史状态；不能把 N 丢弃 |
| report_manager | report.manager_text | 先保留原文，不自动映射员工 |
| report_area | report.area_text | 原文保留；确认单位/数值语义后再转换 |
| report_file / report_attach | report file references | 当前两列均为空；文件线索来自 sys_file 与项目档案，不可只看此表 |
| create_by / update_by | audit legacy_actor | 历史文本 |
| create_time / update_time | report legacy timestamps | 时区待核验 |
| project_document.project_id | project_archive_entry.project_id | 必须通过 project ID map |
| project_document.kind / legacy_role | archive_entry.type / role | report 与 report_attachment 分开保存 |
| project_document.files | document_link | 路径映射到 FileObject；原件未随 SQL 提供 |
| legacy_report_id | archive_entry.source_report_id | 关联旧 report.id；不以 report_no 匹配 |
| legacy_snapshot | archive_entry.legacy_snapshot | 497 条均为可解析 JSON；完整保留 |
| name / created_by / created_at / updated_at | archive metadata | 保留原文和时间 |

报告编号重复：14 组、涉及 43 条记录；空编号 6 条。迁移后历史 report_no 不加全局唯一约束。

## 4. 文件

源表：`sys_file`。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| file_id | legacy_identity_map + file_object.id | 稳定来源键 |
| file_name | file_object.original_name | 保留原文件名 |
| file_path / file_url | file_object.legacy_path / legacy_url | 仅作为源定位线索；必须由只读文件扫描确认真实对象 |
| file_hash | file_object.sha256 | 若有旧 hash 作为参考；正式迁移时重新计算 |
| file_size | file_object.size_bytes | 元数据参考；正式迁移时重新读取实际大小 |
| file_type / file_ext | file_object.mime_type / extension | 标准化但保留原值 |
| biz_type / biz_id | document_link.owner_type / owner_id | 按业务类型转换为显式引用；异常引用进队列 |
| del_flag | file_object.legacy_deleted_flag | 不因旧删除标记直接删除物理文件 |
| create_by / create_time / update_by / update_time | file audit metadata | 保留来源操作者与时间 |
| remark | file_object.legacy_remark | 保留历史缺失/未验证标记 |

发现：1 条 contract 类型 biz_id 找不到当前 contract identity；1 条 sys_file 记录缺少 biz_type/biz_id。两者都必须作为异常记录保留。跨 report/project_document 的同 URL 关系按多个业务引用处理，不自动复制文件。

## 5. 员工与系统账号

源表：`zj_employee`、`sys_user`、`sys_user_role`。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| employee.id | employee.id map | 来源键 |
| employee.name | employee.name | 原文保留；不通过姓名自动合并 |
| employee_type | employee.type | 16 条待确认，保持 pending/unknown |
| active | employee.active | 直接映射状态 |
| source_user_id | employee_account_link | 17 个来源链接已存在；必须核验唯一映射 |
| source_credential_id | credential source link | 当前全空；不生成资质 |
| user_id / user_name / nick_name | user_account | 登录主体独立于员工 |
| password | credential hash import candidate | 不在普通日志或迁移报告输出；仅经 hash 算法兼容与安全审查后决定保留，否则要求重置 |
| status / del_flag | user_account.status | 重新映射停用/删除状态；禁止把旧角色直接升级 |
| sys_user_role / sys_role / sys_menu / sys_role_menu | legacy permission snapshot | 用于新 RBAC 权限映射审核，不直接复制旧授权 |

## 6. 财务

源表：`zj_reimbursement`、`zj_cost`、`zj_project_cost`、`zj_work_project_expense_link`，以及当前为空的账户/流水/发票表。

| 旧字段 | 目标字段 | 转换/规则 |
|---|---|---|
| reimbursement.id | expense_claim legacy source key | 幂等键 |
| receipt / client_token | expense_claim.legacy_receipt / source token | 作为历史幂等/回执元数据，不复用为新系统凭据 |
| amount | expense_claim.amount | DECIMAL(18,2)，精确保留 |
| purpose / remark | expense_claim.description / remark | 原文保留 |
| project_id | expense allocation/project link | 只保留已存在关联 |
| user_id / employee_id / claimant_name | claimant identity snapshot | 三者分开；不靠姓名猜测 |
| expense_date | expense_claim.expense_date | 业务日期，不做时区偏移 |
| source / status | expense_claim.source / status | 原始状态和来源值保留 |
| invoices | document_link | 路径需文件原件核验 |
| legacy_cost_id / legacy_snapshot | legacy expense provenance | 保留旧费用来源与完整快照 |
| zj_cost.cost (DOUBLE) | legacy expense amount raw + normalized amount | 保留原始十进制文本；经核对后转换 DECIMAL |
| zj_project_cost.project_cost | legacy project cost summary | 仅保留历史汇总，不生成流水 |
| zj_work_project_expense_link | expense allocation/source link | 关联项目、旧费用、旧人员来源 |
| finance_account / transaction / invoice | — | 当前 0 行，不创建虚假余额、付款或发票 |

## 7. 维保资源

源表：`zj_wbcard`、`zj_wbcard_number`、`zj_wbcard_material`、`zj_project_material`。

- 维保卡 `id` 为来源键；`code` 作为编号候选，不作为历史全局唯一键（存在一组重复）。
- `file` 解析为文件引用，不直接当作可用下载链接。
- `print_check`、`send_check` 先保留原值，再按真实枚举解释。
- `zj_wbcard_number` 作为编号关系/历史编号来源独立迁移。
- 成品资料 `name/file/remark` 映射到 MaintenanceMaterial 与 DocumentLink。
- `zj_project_material.project_id/material_id/card_id` 映射为 ProjectResourceLink，三个引用均需通过 source map 解析。

## 8. 通用字段处理规则

- 所有旧 `create_by/update_by` 都作为 legacy actor 文本保存；不假定是新账号 ID。
- 所有日期时间字段必须带源时区说明；SQL dump 设置 UTC 会话不代表历史时间均以 UTC 写入。
- 所有 DECIMAL 保持精度；DOUBLE 原值先保存文本，禁止直接经 JavaScript Number 或 binary float 中转。
- 所有 NULL 保持 NULL；空字符串与 NULL 不自动等价。
- 所有未知枚举值保留 raw value，并进入映射异常队列。
- JSON 字段保存 raw JSON 和 parser version；解析失败不丢弃原始内容。
- 文件路径必须与文件扫描清单匹配；只有 URL/元数据不代表文件已迁移。
- 每个映射都记录 mapping version、source key、目标 ID、批次、处理结果和校验摘要。