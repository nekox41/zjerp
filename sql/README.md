# 数据库脚本

全新环境只运行 `bash scripts/init-database.sh`，Compose 自动调用相同入口。禁止按文件名排序批量执行本目录。

初始化顺序：`ry_20250417.sql`（框架与用户资料）→ `quartz.sql` → `business_schema.sql` → `sys_file.sql` → `zhongji_contract_schema.sql` → `zhongji_contract_menus.sql` → `work_projects_schema.sql` → `work_projects_menus.sql` → `wbcard_workspace.sql` → `project_finance_schema.sql` → `project_finance_menus.sql`。最后由初始化器暂停演示任务并写入完成标记。

`upgrades/` 仅保留旧版本升级脚本，不参与首次部署。合同旧表迁移与直接建当前合同表是互斥路径，不能一起运行。菜单脚本也不是可重复执行的升级器。任何生产结构升级需先备份、隔离演练和审核，不在应用启动时自动执行。
