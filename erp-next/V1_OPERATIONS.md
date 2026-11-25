
> 最新开发流程：优先下载 GitHub Releases 的预发行包，在宿主机直接运行 JRE/Nginx/独立 PostgreSQL 17。包内 deploy.sh 每次重建测试数据，无需源码、Docker 或编译环境。本文原 Docker 流程保留为备选；正式部署和迁移仍未开放。

# ERP Next V1 — 部署、备份与验收

> 当前文档面向独立新库 `erp_next` 的首次部署。**不要**将数据库指向旧系统 `server`；不要使用 `docker compose down -v`。

## 首次部署

1. 在仓库根目录执行 `cp erp-next/.env.example erp-next/.env.local`，将数据库 root 密码、应用密码及管理员初始化令牌替换为不同的随机强密钥。不要提交 `.env.local`。
2. 运行 `docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml up -d --build`。
3. 查看 `docker compose --env-file erp-next/.env.local -f erp-next/compose.yaml ps`，确认 MySQL、API、Web 健康，Flyway Schema 服务退出码为 0。
4. 打开 `http://服务器地址:8089/next.html`。首次初始化管理员账号（密码至少 12 位），随后登录。
5. 先配置业务角色与账号，再按顺序建立组织、员工、客户、合同、项目，随后开展服务工作包/报告、费用报账、发票和经营分析。

**网络与安全**：API 8090 默认仅绑定服务器本地回环地址；Web 8089 默认对外开放。生产环境应使用 HTTPS 反向代理和防火墙限制访问，不能直接将 HTTP 登录页面暴露到不可信网络。

## 开发候选版本测试

开发期间每次 `bash deploy.sh` 直接清空独立新版测试库和附件，重新初始化；不备份、不迁移。生产数据迁移待正式完整版开发完成后单独设计。

## 正式发布后的升级（不适用于开发候选版本）

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
4. 使用与备份对应的应用版本启动，确认管理员登录、报告附件下载、发票核销明细和费用审批历史。
5. 将恢复演练日期、备份 SHA-256、恢复耗时、抽样业务记录写入运维记录。

## V1 验收清单

- [ ] 新库 `erp_next` 初始化，Flyway V1__initial_schema.sql 成功；重复运行迁移无副作用
- [ ] 管理员初始化仅可执行一次，账号登录/退出和权限隔离正常
- [ ] 组织、员工、客户、联系人、合同及项目可创建和检索
- [ ] 项目里程碑、成员、服务工作包、记录、问题处理可操作
- [ ] 技术服务报告从草稿到提交、审核、签发、归档，附件可安全上传下载
- [ ] 费用报账提交、财务退回、批准及全额付款；禁止自审、重复付款
- [ ] 应收应付发票登记、不同账号审核、部分核销及余额正确
- [ ] Web 页面在目标浏览器、手机与桌面实际手工验收
- [ ] 备份与恢复演练成功，HTTPS、访问控制及监控配置完成

**自动化覆盖范围**：两个 Actions 工作流改为手动触发；开发期间不运行。推荐在独立 Linux/WSL 测试机执行 `bash scripts/erp-next-local-verify.sh --full`，使用独立随机命名的测试容器与卷。构建、迁移和 API Smoke**不能替代**人工 UI 验收、生产 HTTPS 配置、备份恢复演练或用户培训。

## 已知边界

- 旧版 `server` 不会自动迁入。只读预检、离线档案与显式试迁移见 [LEGACY_MIGRATION.md](LEGACY_MIGRATION.md)。禁止在旧库或未经备份的生产目标执行试迁移。
- 发票台账不是税务平台开票、银行对账或法定会计总账；付款与核销为人工登记。
- 费用报账当前只支持全额付款登记；发票台账支持部分核销。
- 正式生产使用前需要人工验收及安全/运维配置；通过 CI 不等于已经生产就绪。

## 报告签发专项验收

- 新建无合同项目，在技术服务页直接创建服务工作；记录首次到场及整改复核人员、问题清单。
- 照片分类后提交、独立工程师退回、补充并再次送审；已审核/锁定批次禁止编辑。
- 检测报告编制并生成多个 DOCX，人工 Word 修订后另行上传，选择一个明确文件送审；审核与签发人员均不得为本版编制人、提交人或所选文件制作者。
- 报告撤回后保存新版本；已签发报告创建修订后，旧版标题、附件、签发文件和交付记录仍然可查。未签发版本禁止登记交付。旧生产版无明确文件签发证据的历史记录不自动猜测补齐；需重新编制送审再登记新交付。
- 员工资质更正为撤销/失效后，禁止使用无有效资质的员工审核；历史已完成审核保留。账号密码至少12字符、最多72 UTF-8字节，密码重置撤销旧会话。
- 知苑和牧云台原始 DOCX 分别具有47/59个独立两列两行照片表，原始结构检查无纵向合并/嵌套表/复杂域/签署文字；真实 Word 输出仍需按 [INSPECTION_PROFILE.md](INSPECTION_PROFILE.md) 本地生成并检查分页、图片和打印。
- 旧库离线副本保全、映射试迁移与还原演练，核对原始表行数、附件校验和、旧 ID 映射与保留记录。
- 财务和评定评估专项仍按 Issue #8 暂缓，不作为 V1 发布前必需的新开发。

结构化检测专项：核验规范并录入条款摘要，建立两个系统的网页模板；重复导入不覆盖已有记录，增加同项目不同部位；未选依据、缺少模板项目、错误版本、未填写人工结果/判定均应阻止对应生成或送审；旧报告历史与停用条款引用保留。核对生成六列表及 Word 打印。

报告排版专项：按 INSPECTION_PROFILE.md 建立空白公共模板及两个系统，确认目录更新后重新登记，检查4+1照片分组、长说明、原图审核及固定文件归档；禁止将未更新的新目录草稿直接送审。实际 Word 打印仍需人工核对，不以 XML 结构检查代替。

最终统一验收以 [V1_ACCEPTANCE.md](V1_ACCEPTANCE.md) 为准。先运行 --static，再 --full，真实样本与旧库只在独立环境验证；保留实际执行结果，不把代码提交或静态检查当作验收通过。正式部署前应限制 8089 入口到受控反向代理/内网，启用 HTTPS、备份和访问控制。

## WSL2 候选版一键验收

`bash test.sh` 使用容器提供构建工具并记录日志；`bash deploy.sh` 重建专用一次性体验环境（不含旧库），与本页普通部署及生产卷分离。参见 [WSL_ACCEPTANCE.md](WSL_ACCEPTANCE.md)。

体验部署先在宿主机编译并打包运行镜像，成功后才重建独立测试数据库与附件；不清除 Maven/npm 依赖缓存。正式/自动化容器构建入口保持原状。
