CREATE TABLE IF NOT EXISTS zj_inspection_report (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, report_no VARCHAR(100) NOT NULL,
 snapshot_json LONGTEXT NOT NULL, file_url VARCHAR(700) NOT NULL DEFAULT '', created_by VARCHAR(64),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
SELECT '检测报告生成',@brand,9,'inspection-reports','zhongji/reports/generate','InspectionReportGenerate','C','0','0','management:report:list','documentation',1,1,'admin',NOW()
WHERE @brand IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/reports/generate');
SET @page=(SELECT menu_id FROM sys_menu WHERE component='zhongji/reports/generate' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
SELECT '生成报告',@page,1,'#','F','0','0','management:report:generate',1,1,'admin',NOW() WHERE @page IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:report:generate');
