# 运维与验证

B当前Compose配置目录保持在/vol2/zjerp-migration/source/zjerp_backend-master；当前发布源码在/vol2/zjerp-migration/projects/source。配置目录名带source不表示它是最新业务代码，不能在错误目录覆盖配置。私密.env不提交Git。

上线顺序：检查当前服务/数据 → 在隔离数据库演练 → 构建与接口验证 → 停前后端 → 数据库、精确浮点补充、上传/模板快照和配置配套备份 → 执行已验收迁移 → 一致性检查 → 冻结镜像启动 → 独立只读生产验收。迁移失败不能跳过错误或覆盖旧备份。

最近升级前备份：/vol2/zjerp-migration/backups/post-migration-20261028-223538。B的projects/production-update下保存镜像、迁移统计及校验结果，check下保存隔离验证报告。完整运维记录保留在B的/vol2/zjerp-migration/OPERATIONS.md和本地local-archive中。

MySQL旧FLOAT的普通dump存在取值舍入风险，必须同时保存精确浮点补充。不得复制MySQL物理目录跨版本恢复，不执行down -v。合同兼容视图恢复到测试库时，检查视图引用没有指回生产库。

codex/operations/20261028中的脚本是历史发布工具，带固定路径、实例名和一次性状态断言；不是日常一键部署入口。执行前阅读全文、核对目标、备份和前置条件。reset/cleanup只对已确认的隔离数据库使用。生产验收只使用独立readonly脚本。

源码整理不意味着生产重新部署。回退不能只换镜像：有结构变化时需配套旧库；有新业务写入后先导出核对，不能直接用旧备份覆盖。
