# 运维与验证

B当前Compose配置目录保持在/vol2/zjerp-migration/source/zjerp_backend-master；当前发布源码在/vol2/zjerp-migration/finance-ledger-20261029/source。配置目录名带source不表示它是最新业务代码，不能在错误目录覆盖配置。私密.env不提交Git。

上线顺序：检查当前服务/数据 → 在隔离数据库演练 → 构建与接口验证 → 停前后端 → 数据库、精确浮点补充、上传/模板快照和配置配套备份 → 执行已验收迁移 → 一致性检查 → 冻结镜像启动 → 独立只读生产验收。迁移失败不能跳过错误或覆盖旧备份。

最近升级前备份：/vol2/zjerp-migration/backups/post-migration-20261029-231524。B的finance-ledger-20261029/production-update保存原表核对基线，根目录deployed.json、production-validation.json记录发布与验收，check保存隔离验证报告；之前的报告迁移统计在project-finance-20261029。完整运维记录保留在B的迁移目录和本地local-archive中。

MySQL旧FLOAT的普通dump存在取值舍入风险，必须同时保存精确浮点补充。不得复制MySQL物理目录跨版本恢复，不执行down -v。合同兼容视图恢复到测试库时，检查视图引用没有指回生产库。

20261028历史发布工具已移出源码仓库，保存在本地local-archive与B的迁移目录；它们带固定路径、实例名和一次性状态断言，不是开发或日常部署入口。生产验收仅使用独立只读检查。

源码整理不意味着生产重新部署。回退不能只换镜像：有结构变化时需配套旧库；有新业务写入后先导出核对，不能直接用旧备份覆盖。

2026-10-29项目文件/财务发布采用B本机构建及冻结镜像标签，未依赖Docker Hub或等待Actions。前后端镜像分别为20261029-finance-4f76bc62158a和20261029-finance-78cc01e03f79。Compose构建上下文及MySQL初始化SQL只读挂载已指向本次final-source，生产数据卷复用保留。首次尝试因新表排序规则与连接规则不一致，在迁移版本查询阶段停止，原数据核对与旧服务恢复通过；修复为二进制精确版本比较后，按utf8mb4_unicode_ci重做完整演练及重复执行检查，再次发布成功。首次尝试备份和诊断保留，没有覆盖。

机器专用部署与验收脚本仍在B迁移目录/本地local-archive，不是日常启动入口。测试容器与有专用角色标记的隔离库仅在生产验收通过后清理；生产不提交测试报账。原表和原件继续保留，不能将退休模块的菜单/接口删除等同于删除审计数据。

2026-10-29 23:16财务工作台发布：镜像zjerp-backend:20261029-ledger-ffd10349e5a9、zjerp-frontend:20261029-ledger-58ed7f9fa868。停前后端后备份数据库、精确浮点补充、文件及配置，执行finance_ledger.sql与finance_ledger_menus.sql，逐行核对原61张表，仅新增菜单/授权关联；无旧账回写。Compose以--no-deps切换前后端，没有重建数据库容器。源码和后续构建上下文已更新到本次source目录。真实隔离API浏览器回归及生产只读验收通过；测试财务数据仅在带专用角色标记的隔离库，验收后清理库、账号、容器和临时凭据。

2026-10-29 23:41前端修复：当前前端镜像zjerp-frontend:20261029-api-616169e7b073，后端保持ffd10349e5a9。原公开模式配置被一次性源码打包器过度过滤，B构建产物丢失/prod-api前缀；已补齐前端.env.production、.env.development、.env.staging并修正打包器与源文件核对范围。不要把这些已跟踪的公开配置同后端私密.env混为一谈。Vite在缺少VITE_APP_BASE_API时拒绝启动/构建，缺失配置的负例检查通过。

api-fix/before.env保留切换前配置；deployed.json、browser-validation.json记录当前镜像、实际镜像产物浏览器路径检查及80/8088接口探测。不再只测本地dist或手工访问/prod-api/health；发布必须检查待部署镜像生成的JS实际发起的接口路径。索引仍为no-store，更新后旧标签页需刷新加载新JS。该次仅替换前端，无数据库变更。
