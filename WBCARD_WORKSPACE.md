# 维保卡工作台

## 菜单与数据

众基消防 → 维保卡生成 → 维保卡生成、附带资料管理、联系人管理。旧管理页面停用，历史 `zj_wbcard`、统一文件关联及 `zjerp_wbcard_phone` 原样保留；联系人直接复用原表，不另造重复数据。

附带资料使用新表 `zj_wbcard_material`，只保存名称、PDF链接、备注和时间，文件关联类型为 `wbcard_material`。用户自行制作统一水印 PDF 后上传，系统不改写、合并或追加项目水印，也不会在生成维保卡时附带生成。旧卡关联的历史附件继续可查看，不自动复制到新资料库。

## 排版

`WBCardUtils` 使用 Apache PDFBox 3.0.8，底图 `wbcard/templates/card.png`，A4 横向。中文使用可嵌入的静态 Noto Sans SC TrueType 字体。按 PDF 字体字形宽度逐 Unicode 码点换行，字段在各自区域中自适应14至7pt字号；无法完整容纳、缺字或字段超长会拒绝生成，没有静默截断。维护内容直接接受完整文字，不按人工字符数切成两行；旧 system2 字段仅作兼容输入。

预览与正式生成共用排版函数。预览返回PDF字节、不落盘、不写维保卡和文件记录。保存以日期和UUID生成文件名，项目名称不参与磁盘路径；保存一张维保卡PDF，登记到统一文件服务。数据库写入失败会清理本次输出文件。PDF不是可填写表单，不再依赖 iText。

## 接口与权限

- POST `/wbcard/gen/preview`、POST `/wbcard/gen/create`：`management:wbcard:add`。
- 原 `/management/wbcard`：保留历史记录列表、详情、状态和删除接口以及原权限。
- GET `/wbcard/resources/materials`：`management:wbcard:materials:list`；POST、DELETE `/{id}`：`management:wbcard:materials:edit`。
- GET `/wbcard/resources/contacts`：`management:wbcard:contacts:list` 或生成权限；POST、DELETE `/{id}`：`management:wbcard:contacts:edit`。

附带资料后端检查文件登记、扩展名、真实PDF解析、路径边界、50MB大小、1至300页及未加密要求。删除资料解除文件关联，保留物理原件。新增资源写权限由管理员单独授权，不根据旧查看权限自动授予。

## 升级和验证

先备份、隔离演练，再对已有库执行 `sql/wbcard_workspace.sql`，部署前后端。脚本创建新资料表并移动菜单，可重复执行；不清理历史数据。全新初始化已包含相同脚本。生产存在 wbcard 资源绑定挂载时，发布时需同步 card.png、静态字体和许可证，否则镜像内的新资源可能被旧宿主目录覆盖。

自动排版测试检查长文本提取完整、一页PDF、无表单、超长拒绝、缺字拒绝和连续生成互不影响，输出样张供页面渲染检查。接口演练覆盖预览不落库、仅生成卡片、资料原件不变、联系人CRUD、无效PDF拒绝和角色权限。浏览器打印仍以实际打印机纸型和缩放设置为准，建议A4横向、实际大小。

2026-10-29 16:34（北京时间）已在B部署。备份为 `post-migration-20261029-163300`；291张历史卡、13个联系人和5条旧模板记录保持不变。4项排版测试、13组接口集成检查、生产健康/登录/三菜单/历史数量/无落盘预览验收通过。B的发布记录在 `/vol2/zjerp-migration/wbcard-20261029`，当前发布源码在其 `final-source` 下。开发环境拉取后，在已有开发库执行本模块SQL，再重新构建全部模块并重启前后端。
