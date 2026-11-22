# ERP 重建工程

> 状态：设计基线 v0.2（2026-11-21）。这是新架构的工作基线，不是已完成的系统设计，也不代表任何生产数据已迁移。

本工程以业务合理性为第一原则，不要求新系统沿用旧系统的菜单、代码分层、API、数据库表或操作流程。旧系统只作为**业务事实、历史记录与数据来源**。新系统应在独立目标模型上设计，最后通过专用迁移程序导入旧数据。

## 文档导航

- [现状审计基线](CURRENT_STATE.md)：从代码、SQL 和项目文档确认的现状，以及尚待验证的事项。
- [生产数据快照审计](PRODUCTION_DATA_AUDIT.md)：本次用户提供的完整数据库导出统计、关系抽查、数据质量和附件限制。
- [核心字段级迁移映射](FIELD_MAPPING.md)：合同、项目、报告、文件、人员、财务和维保资源的列级转换规则。
- [旧表到新业务域映射矩阵](LEGACY_MAPPING_MATRIX.md)：68 张源表的目标归属、迁移策略与待确认事项。
- [API 与权限盘点](API_PERMISSION_AUDIT.md)：现有 Controller 路由、服务端权限边界及待核对的契约问题。
- [跨业务域交互模型](DOMAIN_INTERACTIONS.md)：模块职责、数据所有权、跨域命令与关键业务旅程。
- [目标数据模型](TARGET_DATA_MODEL.md)：结合生产快照提出的规范化核心实体、关系、约束和迁移顺序。
- [目标架构原则](TARGET_ARCHITECTURE.md)：新 ERP 的业务域、实体关系、生命周期和系统边界。
- [一键迁移设计](MIGRATION_DESIGN.md)：旧数据盘点、映射、预演、导入、校验、切换与回滚设计。
- [第一版 ERP 产品范围](MVP_PRODUCT_SCOPE.md)：可用 ERP 的业务范围、完整用户旅程、完成定义与实施顺序。

## 项目跟踪

- 后端架构与业务模型：[zjerp_backend#5](https://github.com/nekox41/zjerp_backend/issues/5)
- 前端跨模块体验：[zjerp_frontend#14](https://github.com/nekox41/zjerp_frontend/issues/14)
- 后端联合重构总跟踪：[zjerp_backend#2](https://github.com/nekox41/zjerp_backend/issues/2)
- 前端体验总跟踪：[zjerp_frontend#10](https://github.com/nekox41/zjerp_frontend/issues/10)

## 决策状态

| 决策 | 当前结论 |
|---|---|
| 是否必须兼容旧代码/菜单/API | 否 |
| 是否必须沿用旧数据库结构 | 否 |
| 是否保留旧业务事实与历史价值 | 是 |
| 是否允许自动猜测不确定的业务关联 | 否 |
| 新旧系统是否直接共用生产数据库 | 否，目标是独立目标库 |
| 是否要求用户手工逐条迁移 | 否，目标是迁移向导批量自动迁移 |
| 是否允许迁移过程覆盖/删除源数据 | 否 |
| 是否在未完成核对前切换生产 | 否 |

## 迁移工具

首个只读源库检查器位于 [`zjerp-migration`](../../zjerp-migration/README.md)，不执行数据导入。

## 当前阶段（方向校准：2026-11-22）

主目标是交付第一版完整可用的 ERP，产品路线见 [后端 #8](https://github.com/nekox41/zjerp_backend/issues/8) 和 [前端 #16](https://github.com/nekox41/zjerp_frontend/issues/16)。现有生产快照审计、领域关系和目标架构文档作为首批实现基线；只处理阻塞开发的业务决策，不再无限扩展审计。

实施顺序：平台基础 → 客户/合同 → 项目工作台 → 人员/资质 → 服务/报告 → 工作台/待办 → 财务闭环 → 维保。每个切片必须包含真实数据库、后端规则/API、权限、前端交互、测试和部署验证。

迁移是首版上线门禁和数据保障能力，不是当前主产品迭代的中心。现有只读 inspector 与私有 SQL 暂存基础保留；目标业务模型稳定前暂停扩展扫描/映射 UI。首版上线前必须保证每条历史数据都有明确去向，并完成隔离全量演练、数量/金额/关系/文件 hash 对账和恢复演练。