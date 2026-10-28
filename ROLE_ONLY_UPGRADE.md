# 纯角色权限与登录精简（2026-10-28）

当前版本删除部门和岗位的前后端管理、用户关联、部门数据范围、统计及相关表。用户只关联角色，菜单和按钮权限来自启用且未删除的角色。管理员身份来自启用的 `admin` 角色，不再由账号 ID 决定。角色授权、撤销和停用会在现有会话的下一次请求中生效。

登录及注册的验证码界面、API、校验、参数、配置、缓存常量和 Kaptcha 依赖已删除。登录请求只需 `username`、`password`。密码校验、停用账号校验和错误次数限制仍然有效；自助注册仍由原有注册开关控制。健康检查使用 `GET /health`。

## 已有数据库升级

应用停止写入并做好可恢复的整库、文件及配置备份后，执行 `sql/role_only_upgrade.sql`。它会移除：

- `sys_dept`、`sys_post`、`sys_user_post`、`sys_role_dept`；
- `sys_user.dept_id`，`sys_role.data_scope/dept_check_strictly`，`sys_oper_log.dept_name`；
- 部门、岗位菜单及其角色菜单关联，旧验证码配置。

其他用户、角色、权限关联、业务、文件记录和上传文件保持不变。SQL可重复执行；DDL会独立提交，回退应使用配套备份和兼容的应用镜像。数据库中的FLOAT金额备份应保留精确数值补充文件。

更新后清理旧验证码配置缓存、验证码缓存及登录会话，重新登录。新建环境直接使用本版本初始化SQL。

## 本次验证与上线

- 前后端完整构建成功；6项权限/JWT会话回归测试、8项既有文件服务检查、9项隔离接口检查通过。
- 空库全新初始化和旧库升级均通过；升级SQL重复执行安全，保留数据哈希一致。
- B于2026-10-28 17:34:47（Asia/Shanghai）上线。正式环境无验证码登录、旧接口移除、用户角色、菜单和附件下载验证通过。
- 升级前配套备份：`/vol2/zjerp-migration/backups/post-migration-20261028-173239`，包含原部门岗位数据、完整数据库、精确FLOAT补充、文件/模板COW快照、旧配置及镜像记录。
- 当前构建源码：`/vol2/zjerp-migration/role-only/source/zjerp_backend-master` 和相邻前端；生产Compose配置仍在 `/vol2/zjerp-migration/source/zjerp_backend-master`，override已指向新源码和SQL。
- 当前镜像：`zjerp-backend:20261028-roles-4fbd11e8ec9f`、`zjerp-frontend:20261028-roles-3d9e5e9e2618`。

验收与完整性报告位于B的 `/vol2/zjerp-migration/role-only/production-update/`。生产运维继续使用 `.env` 中固定的 `zjerp-production` 项目名。

本地回归测试：`mvn -pl zjerp-framework -am test`；文件服务检查在打包后运行 `bash scripts/verify-file-storage.sh`。
