# Monorepo 合并执行记录（Issue #14）

目标仓库：`nekox41/zjerp`（已由原后端仓库重命名）。

源仓库：`nekox41/zjerp_frontend`（私有）。

## 为什么分两阶段

第一阶段使用 GitHub Actions 将**完整前端 Git 历史**接入当前后端仓库，再把前端文件放入 `frontend/`。不使用仅复制文件的方案，因为它会丢失来源提交的可追溯性。

第二阶段在统一仓库的后续 PR 中将原后端文件移动到 `backend/`，调整 Maven、Vite、Docker、Compose、CI 和部署路径，完成构建/启动验证。迁移完成前不要将当前目录布局称为最终 Monorepo。

## 执行前准备

GitHub 默认 `GITHUB_TOKEN` 只能访问本仓库，不能读取另一个私有仓库。因此需要仓库管理员在 `nekox41/zjerp` 的 Actions secrets 中设置：

- `FRONTEND_READ_TOKEN`：**fine-grained PAT**，仅对 `nekox41/zjerp_frontend` 授予 `Contents: Read`。不需要源仓库写入权限。

确认目标仓库 Actions 的工作流具有 `Contents: Write`、`Pull requests: Write` 权限，并允许工作流创建 PR。绝不将 PAT 写入 Issue、PR、源码或日志。

## 运行

1. 合并引入工作流的 PR。
2. 在 GitHub Actions 选择 **Import frontend history into monorepo**，手动运行，目标分支保持默认 `integration/monorepo-full-history`。
3. 工作流在新分支上创建一个具有两个父提交的历史连接提交，再通过 `git read-tree --prefix=frontend/` 将源前端文件完整纳入目录。
4. 工作流检查 `frontend/package.json`、根 `pom.xml` 及双父历史，并自动创建 PR。
5. 人工快速复核文件数、历史与敏感文件后合并；继续 #15 的路径、CI 和部署修正。

如工作流因权限、分支重名或 GitHub Actions 限制失败，先修复原因，不要强推覆盖已有分支。

## 未完成事项

- 原后端代码仍在仓库根目录，待 #15 移至 `backend/`。
- 原后端 Draft PR #6、原前端 Draft PR #15、后端 PR #12 尚未全部处理；在对应内容核对之前不得关闭或遗忘。
- 旧仓库 Issue、Release、标签、Actions 配置和部署环境需要单独建立映射清单。
- 合并后必须验证 Maven、Vite、MySQL/Flyway 和 Compose，才能宣布统一仓库可用于开发与部署。

## 安全约束

不连接生产数据库，不复制生产 SQL 或附件，不修改旧前端仓库，不自动删除/归档源仓库。令牌仅供私有源仓库只读访问。
