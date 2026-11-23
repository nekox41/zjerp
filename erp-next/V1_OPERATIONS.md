# ERP Next V1 — 部署、备份与验收

> 当前文档面向独立新库 `erp_next` 的首次部署。**不要**将数据库指向旧系统 `server`；不要使用 `docker compose down -v`。

## 首次部署

1. 在仓库根目录执行 `cp erp-next/.env.example erp-next/.env.local`，将数据库 root 密码、应用密码及管理员初始化令牌替换为不同的随机强密钥。不要提交 `.env.local`。
2. 运行 `docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build`。
3. 查看 `docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml ps`，确认 MySQL、API、Web 健康，Flyway Schema 服务退出码为 0。
4. 打开 `http://服务器地址:8089/next.html`。首次初始化管理员账号（密码至少 12 位），随后登录。
5. 先配置业务角色与账号，再按顺序建立组织、员工、客户、合同、项目，随后开展服务工作包/报告、费用报账、发票和经营分析。

**网络与安全**：API 8090 默认仅绑定服务器本地回环地址；Web 8089 默认对外开放。生产环境应使用 HTTPS 反向代理和防火墙限制访问，不能直接将 HTTP 登录页面暴露到不可信网络。

## 升级

1. **先做数据库和附件备份**（见下节），在维护窗口操作。
2. 获取新代码，查看 Flyway 迁移文件及版本说明。
3. 运行 `docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build`。
4. 检查 Flyway 日志、API 健康检查、登录及主要业务路径。**不要删除已有 Flyway 迁移或修改其历史内容**。
5. 如升级失败，停止应用，恢复与升级前相匹配的数据库及附件备份，然后使用旧版本应用镜像/源码重新部署。数据库迁移一般不可直接通过回滚代码撤销。

## 备份（必须同时备份数据库与私有附件）

在仓库根目录执行；下面的命令使用部署环境文件读取数据库密码，不将密码硬编码在命令行。备份文件包含个人及业务敏感信息，应加密存储并限制访问。

```bash
set -euo pipefail
set -a
source erp-next/.env.local
set +a
mkdir -p backups
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml \
  exec -T -e MYSQL_PWD="$ERP_NEXT_DB_PASSWORD" erp-next-mysql \
  mysqldump -u erp_next --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 erp_next > "backups/erp_next_$STAMP.sql"
docker run --rm -v erp_next_private_files:/data:ro -v "$PWD/backups:/backup" alpine:3.20 \
  tar -C /data -czf "/backup/erp_next_files_$STAMP.tar.gz" .
sha256sum "backups/erp_next_$STAMP.sql" "backups/erp_next_files_$STAMP.tar.gz" > "backups/erp_next_$STAMP.sha256"
```

数据库与文件备份不是原子快照；应在**禁止新写入的维护窗口**执行，确保两者逻辑一致。建议在隔离测试环境定期演练恢复。请勿将明文备份上传到公开仓库。

## 恢复演练（仅在隔离环境）

1. 在独立的测试主机上部署相同版本的 ERP Next，**禁止**使用生产数据库卷或生产主机。
2. 停止测试 API/Web 写入；将对应的 SQL 导入测试 `erp_next` 数据库。
3. 在测试附件卷中解压对应的 `erp_next_files_*.tar.gz`，保留文件权限并检查路径。
4. 使用与备份对应的应用版本启动，确认管理员登录、报告附件下载、发票核销明细和费用审计。
5. 将恢复演练日期、备份 SHA-256、恢复耗时、抽样业务记录写入运维记录。

## V1 验收清单

- [ ] 新库 `erp_next` 初始化，Flyway V1–V7 全部成功；重复运行迁移无副作用
- [ ] 管理员初始化仅可执行一次，账号登录/退出和权限隔离正常
- [ ] 组织、员工、客户、联系人、合同及项目可创建和检索
- [ ] 项目里程碑、成员、服务工作包、记录、问题处理可操作
- [ ] 技术服务报告从草稿到提交、审核、签发、归档，附件可安全上传下载
- [ ] 费用报账提交、财务退回、批准及全额付款；禁止自审、重复付款
- [ ] 应收应付发票登记、不同账号审核、部分核销及余额正确
- [ ] 经营驾驶舱汇总与客户/项目筛选，财务指标权限隔离
- [ ] Web 页面在目标浏览器、手机与桌面实际手工验收
- [ ] 备份与恢复演练成功，HTTPS、访问控制及监控配置完成

**自动化覆盖范围**：GitHub Actions 的 Monorepo Build 与 ERP Next Schema and Smoke Test 可验证构建、迁移和关键 API 流程；**不能替代**人工 UI 验收、生产 HTTPS 配置、备份恢复演练或用户培训。

## 已知边界

- 旧版 `server` 数据库不会被自动迁入。管理员可视化迁移功能仍是独立后续任务。
- 发票台账不是税务平台开票、银行对账或法定会计总账；付款与核销为人工登记。
- 费用报账当前只支持全额付款登记；发票台账支持部分核销。
- 正式生产使用前需要人工验收及安全/运维配置；通过 CI 不等于已经生产就绪。
