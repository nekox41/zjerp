# 用户资料

用户管理列表的“资料”按钮支持维护每个用户的资料。预设 key 为资格证书、执业证书、执业证书编号、操作员证书，也可以输入自定义 key。

类型：string（文本）、png、jpg（兼容 jpeg）、pdf、file。每项文件限一个；需要多份时使用不同 key。同一个用户的 key 唯一，保存同名 key 会更新内容。删除资料解除业务文件关联，磁盘文件遵循现有保留策略。

升级现有数据库需执行 `sql/user_material_upgrade.sql`，再部署前后端。全新安装主 SQL 已包含该表。2026-10-28 已在机器 B 部署。

接口（需登录）：

- GET `/system/user/{userId}/materials`：查询全部资料。
- GET `/system/user/{userId}/materials?key=资格证书`：供后续报告系统获取指定资料，返回数组；缺失时为空数组。
- PUT 同一路径：保存 `{ "materialKey": "执业证书编号", "valueType": "string", "content": "证书编号" }`。文件类型 content 为现有上传接口返回的 fileName（文件 URL）。
- DELETE 同一路径，查询参数 key：删除指定资料。

读取权限为 `system:user:query`，编辑权限为 `system:user:edit`，沿用角色授权。返回字段含 userId、materialKey、valueType、content。未来报告模块应显式授权资料读取；此变更没有实现报告模板插入功能。

前后端生产构建通过，后端原有回归测试通过。独立数据库验证了文本新增、同名更新、删除、PNG 上传关联和下载内容比对，并验证非法类型、无效文件、扩展名不匹配和未登录访问被拒绝。生产接口验证通过，原有38张表数据校验一致。

升级前配套备份：机器 B `/vol2/zjerp-migration/backups/post-migration-20261028-192618`。
发布记录：机器 B `/vol2/zjerp-migration/user-materials/production-update/`。
