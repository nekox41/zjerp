# 2026-11-02 维护保养发布

本文件取代MAINTENANCE_REGISTRATION.md及WBCARD_CONTACT_LAYOUT.md中“尚未部署”的历史说明。

## 现网

2026-11-02 15:18（Asia/Shanghai）在机器B发布。Compose配置目录继续使用/vol2/zjerp-migration/source/zjerp_backend-master，当前发布源码为/vol2/zjerp-migration/maintenance-20261102/source。镜像为zjerp-backend:20261102-maintenance-e5b725f87685、zjerp-frontend:20261102-maintenance-50dd23187ab3；升级前配套备份为/vol2/zjerp-migration/backups/post-migration-20261102-151635。

生产此前已是2026-11-01报告版本，因此以Compose当前构建上下文的完整源码为底，只叠加两次维保改动，保留报告生成、人员证书等后续功能；没有直接用更早的迁移源码覆盖。前后端顺序在B构建并冻结镜像，切换时使用--no-deps，不重建数据库。66张原有表的既有记录逐行核对一致，本次没有SQL结构升级或历史记录改写。

## 发布内容

- 维保项目创建左侧预览合同附件；负责人候选排除admin，服务端拒绝新设开发账号为负责人；移除地址、联系人和联系电话录入；日期快捷填入下一年同月同日，闰日按次年2月末处理。
- WB工作台隐藏详细信息、计划信息，原数据保留；登记改为逐条月度/季度/年度计划，五个状态独立记录；保留原登记JSON，沿用版本检查。
- 维保卡系统清单共用多选项，两条下划线各容纳一行，长内容自动缩放、超限拒绝截断；选择当地联系人不修改负责人，电话统一加公司固话，日期使用yyyy年MM月dd日至yyyy年MM月dd日。
- PDF样张复核时补充修复长电话末位换行：电话在单条下划线内自动缩小字号，保持一行。

## 验证与证据

B的Maven构建和测试通过，包含电话/日期规范化及完整文字PDF回归；长内容PDF渲染为PNG目视核对，确认两条系统下划线、电话和日期。最终前端镜像提取后验证/prod-api前缀，并用该镜像产物进行浏览器检查：隐藏的WB标签页、新增/保存登记、原键保留、创建字段、负责人候选、一年日期快捷填入和左侧合同附件地址。该浏览器检查使用合成接口数据，业务保存由真实隔离后端另外验证。

隔离库API验证：登记列表及旧字段持久化、过期版本拒绝、非布尔状态拒绝、开发账号负责人拒绝、合同附件字段返回、长内容预览。生产验收检查域名代理及8088入口的实际index与候选镜像一致、API前缀、报告模块仍在、健康检查、登录和维保项目/合同读取、只预览不保存的PDF生成。未向生产写测试项目、登记记录或维保卡。

证据保存在B的maintenance-20261102目录：build-validation.json、check/api-validation.json、check/artifact-validation.json、check/ui-validation.json、check/pdf-visual.ok、deployed.json和production-validation.json。验收通过后只清理带maintenance_check_前缀、专用角色标记的隔离库/账号及本次测试容器，保留备份、构建源码与验收报告。
