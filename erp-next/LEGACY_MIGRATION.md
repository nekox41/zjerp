# ERP V1 迁移映射与保全清单（M01/M02）

以仓库的旧版 SQL、升级脚本和新版 V1–V14 为依据。旧库实况可能与初始化 SQL 不同，扫描结果不能用代码清单替代。迁移只针对离线副本，生产 `server` 不执行 UPDATE/INSERT/DDL；目标必须为独立 `erp_next`。先备份完整数据库与文件目录，再扫描、人工映射和演练。

| 旧数据 | 新数据 / 处理策略 | 必须核对 |
|---|---|---|
| sys_user | erp_identity | 用户名唯一、显示名；历史密码哈希仅保全，新增账户默认禁用且不自动赋权、管理员重置密码 |
| sys_role, sys_menu, sys_user_role, sys_role_menu | 原始保全 + 新版权限人工映射 | 绝不按旧角色编号授予 admin |
| zj_employee, sys_user_material, zj_report_credential | erp_employee / erp_employee_qualification，未确定资质内容原样保全 | source_user_id 映射；员工类型原值明确映射，资格有效期和附件，双负责人不能自动推断 |
| zj_intermediary | erp_partner | 编号可使用稳定旧 ID 前缀；公司/电话/邮箱/备注；不把 biz_agent 自动视为同一人 |
| biz_agent | 原样保全，人工决定是否归合作方 | 名称相同不自动合并 |
| zj_contract_identity + zj_contract_{maintenance,inspection,rating,assessment,other} | erp_contract、erp_contract_partner、erp_contract_project | 原始 ID 为唯一来源；WB→maintenance、JC→fire_inspection、PD→fire_assessment（消防评定）、PG→fire_evaluation（消防评估）、QT→other；甲方需明确确认，旧表没有独立甲方 ID；原金额 30,16 到 18,2 不允许静默舍入；中间人合同缺分成比例需确认；状态无直接证据不自动置 active |
| zj_work_project | erp_project、erp_contract_project、erp_maintenance_project | client_name 仅候选甲方；联系人单独核对；合同归属一致，负责人映射、项目编号与分类待确认；原 details_json/registration_json 逐字保全 |
| project | 原样保全，核对与 zj_work_project_legacy 关系 | 不重复生成同一项目 |
| zj_work_project_legacy, zj_work_project_sequence, zj_work_project_number, zj_project_code | 原样保全，映射编号/来源；历史编号不得复用 | 核对唯一编号和保留旧 ID |
| zj_work_project_issue | erp_project_issue（业务字段核实后） | severity/status 需明确映射，文件保全 |
| zj_work_project_plan | 原样保全，待识别现场服务/计划 | 不把计划当成已到场事实 |
| zj_work_project_expense_link | 原样保全 | 暂缓财务关联仍保留 |
| zj_wbcard, zj_wbcard_number, zj_wbcard_sequence | erp_maintenance_card / 序列 | 原卡号、PDF、打印寄出状态；项目与年度合同未明确时仅归档待关联 |
| zjerp_wbcard_phone, zj_wbcard_material | erp_maintenance_contact、erp_maintenance_material | 电话、资料 PDF 完整，历史版本和停用状态 |
| zjerp_project_system | 原样保全并人工关联 | 项目名/二维码不能作为唯一 ID |
| sys_file, zj_project_document, zj_project_material, templates, zj_report, zj_inspection_report | 原始记录与原文件全量保全；确认业务关联后映射 erp_file_object、合同/报告附件 | 原始路径、长度、哈希、软删除、孤儿文件和缺失文件；不能伪造不存在的原件；历史报告不自动签发 |
| zj_reimbursement, zj_finance_account, zj_finance_invoice, zj_finance_transaction | 完整保全、暂缓业务映射 | 不按推测重算款项或结算 |
| sys_oper_log, sys_logininfor, sys_dict_type, sys_dict_data, sys_config, sys_notice, gen_table, gen_table_column, sys_job, sys_job_log, QRTZ_*、所有迁移版本表、未知/自定义表 | 完整离线原样保全 | 不自动激活旧计划任务、不将配置/凭证暴露给一般员工、不静默删除未知表 |

## 只读来源配置

1. 从完整备份恢复到独立数据库（例如 server_migration），专用账号只授予 SELECT，不要使用旧生产账号。数据库连接经管理员配置，不能从 HTTP 请求任意指定 URL、用户、表名或 SQL。
2. `erp-next/.env.local` 配置 ERP_LEGACY_SCAN_ENABLED=true、ERP_LEGACY_DB_URL=jdbc:mysql://隔离主机:3306/server_migration、ERP_LEGACY_DB_SCHEMA=server_migration、只读账号和密码。按部署网络配置安全连通/TLS。不要提交真实配置。
3. 登录新版超管，打开“数据迁移”扫描，核查表数、精确计数、未知/暂缓表、失败查询和项目/合同/文件冲突。网页仅允许白名单业务表前 20 条脱敏/截断预览；不展示账号哈希或任意表数据。
4. 该页只读扫描，不导入、不修改任何业务数据。备份、映射确认、试迁移、验收与回滚继续实施。不要因扫描没有报错就删除旧库。

## 完整离线保全工具（M04）

安装 Python MySQL Connector（`python -m pip install mysql-connector-python`），在已隔离、停止业务写入的旧库副本和上传目录运行：

```bash
python scripts/erp-next-legacy-export.py export --host 127.0.0.1 --user legacy_readonly --schema server_migration --namespace company-legacy --files /path/to/legacy-uploads-copy --output /secure/erp-legacy-archive
python scripts/erp-next-legacy-export.py verify --archive /secure/erp-legacy-archive
```

每个可见实体表保留原 DDL 和逐行 JSONL（Decimal、日期、二进制显式编码，不能丢精度），视图保留定义；所有文件（包含孤儿/暂缓业务文件）按相对路径逐字复制并核验长度、SHA256；生成清单和校验和。失败保留 .partial，不会覆盖已存在档案或把未完成档案当作完成。文件备份保留旧路径关系，拒绝符号链接。清单不代表加密或数字签名，必须存放于权限受控、加密的备份介质，不提交 Git、不上传公共网盘。源库账号密码哈希、系统配置等敏感数据仅离线保全，不进入普通业务页面。另需保留标准 MySQL 全量备份（包括过程、触发器、事件、授权等非表对象）及旧部署配置，确保可恢复整个旧系统。

V15 增加旧 ID→新 ID 映射与已提交迁移批次账本，为后续显式映射、重复导入防重与审计提供基础。它不会自动执行任何迁移。
