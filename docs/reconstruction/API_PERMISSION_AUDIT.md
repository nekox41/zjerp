# 后端业务 API 与权限边界盘点（静态审计）

> v0.1 / 2026-11-21。根据当前 Controller 的路由和 PreAuthorize 注解整理。不是完整 API 清单；尚需与前端调用点、Mapper SQL 和运行时权限配置交叉核对。

## 1. 合同

Controller：`ZjContractController`；前缀 `/management/contract`。

| 操作 | 路由 | 权限 |
|---|---|---|
| 列表 | GET /list | management:contract:list |
| 详情 | GET /{id} | management:contract:query |
| 创建/编辑 | POST /、PUT / | management:contract:add / edit |
| 删除 | DELETE /{ids} | management:contract:remove |
| 导出 | POST /export | management:contract:export |
| 类型计数/维保摘要 | GET /count/{type}、/wbsummary | management:contract:list |

服务层分配全局 identity ID，按合同类型写入对应类型表；合同附件经 BusinessFiles 绑定 `biz_type=contract`。合同被项目外键引用时删除会被数据库约束拒绝。需要继续审计合同金额、类型字段、支付计划、补充协议和历史附件完整语义。

## 2. 项目

Controller：`WorkProjectController`；前缀 `/management/work-project`。

| 操作 | 路由 | 权限 |
|---|---|---|
| 项目列表/详情 | GET /list、GET /{id} | management:project:list / query |
| 合同候选/人员候选 | GET /contracts、GET /users | project:add 或 edit |
| 创建/编号申请 | POST /、POST /{id}/apply-number | project:add / edit |
| 分区保存 | PUT /{id}/section/{section} | project:edit |
| 问题 | GET/POST /{id}/issues、DELETE /{id}/issues/{issueId} | query / edit |
| 计划 | GET/POST /{id}/plans、DELETE /{id}/plans/{planId} | query / edit |
| 项目费用投影 | GET /{id}/expenses | project:expenses |

项目服务验证类型、合同类型、父子记录归属和 version；费用面板读取报账数据及历史费用汇总，不应被误解为项目自有财务账本。

## 3. 项目档案与维保资源

Controller：`ProjectResourcesController`；前缀 `/management/work-project/{projectId}`。

- 文件列表/新增/移除：`/documents`；查询要求 project:query，写操作要求 project:edit。
- WB 项目资料关联：`/materials`；成品资料与维保卡候选：`/material-options`、`/card-options`。
- 服务层校验项目存在、WB 类型、资料/卡片存在和版本；文件引用经 BusinessFiles 维护，删除文件记录会清除业务引用但不删除磁盘原件。

## 4. 报账

Controller：`ReimbursementController`；前缀 `/finance`。

- 查询/详情/分析：`GET /reimbursements`、`/{id}`、`/analysis`，要求 zhongji:finance:list。
- 登录提交：`POST /reimbursements`，要求 zhongji:finance:add。
- 公开移动端提交：`POST /public/submit`，匿名；候选接口 `GET /public/users`、`/public/projects` 也匿名。
- 状态更新：`PUT /reimbursements/{id}/status`，要求 zhongji:finance:edit；paid 状态不能直接写入，必须通过财务流水支付。
- 报账保存金额、申报人、录入来源、项目、凭证、幂等 token 和状态。公开入口仅返回回执，不提供账本访问。

## 5. 财务账本

Controller：`FinanceLedgerController`；前缀 `/finance/ledger`。

- 总览与列表：`GET /overview`、`GET /{accounts|transactions|invoices|contracts|projects}`；要求 zhongji:finance:list。
- 流水/发票详情：`GET /{transactions|invoices}/{id}`。
- 候选：`GET /options/{contracts|claims}`。
- 账户/流水/发票登记：POST /accounts、/transactions、/invoices；要求 zhongji:finance:ledger。
- 流水/发票作废：PUT /{transactions|invoices}/{id}/void；要求 zhongji:finance:ledger。

FinanceLedgerService 对金额、日期、关联对象、账户锁、幂等和事务做校验。当前 Controller 公开的发票操作是登记/作废；尚未从该 Controller 看到独立收款、应收计划或发票核销 API，需继续确认是否在其他模块/外部流程实现。

## 6. 员工与资质

- `EmployeeController`：`/management/employees`，员工列表/新增或编辑分别要求 management:employee:list / edit；候选查询允许 employee:list 或 credential 权限。
- 员工资料：`/{id}/materials` 要求 management:credential:list。
- `ReportCredentialController`：`/management/report-credentials`，列表要求 management:credential:list，保存要求 management:credential:edit。
- ReportCredentialService 对人员资质要求 employeeId 指向存在员工；公司资质不绑定员工。报告生成时需要继续核对资质有效期和快照写入点。

## 7. 需要重点复核的契约问题

- [ ] 合同/项目候选的字段命名、分页和类型约束是否与前端一致。
- [ ] 项目页面仍有兼容字段名（如 leaderUserId），实际值已经迁为 employee ID；新 API 应使用明确的 employeeId 命名，避免 ID 主体混淆。
- [ ] 财务项目汇总、报账汇总、流水金额和历史费用投影的统计口径是否唯一。
- [ ] 公开报账候选 API 的数据最小化、频率限制、反滥用及隐私边界。
- [ ] 文件下载接口是否在每次读取时重新验证业务权限，不能只验证上传/绑定权限。
- [ ] 报告生成、预览、签发、历史版本的完整接口与授权链路。
- [ ] 删除/作废操作是否都留下不可覆盖的审计记录。
- [ ] 统一错误码、幂等头、版本冲突、分页和日期/时区契约。

## 8. 结论

当前后端接口按功能模块暴露，不等同于最终业务 API。新系统应以明确的用例/业务命令定义接口，避免把数据库 CRUD 直接暴露给前端；跨域查询提供聚合读模型，跨域写操作由应用层协调并验证每个关联对象。