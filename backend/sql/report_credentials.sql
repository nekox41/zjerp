CREATE TABLE IF NOT EXISTS zj_report_credential (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 kind VARCHAR(20) NOT NULL,
 title VARCHAR(200) NOT NULL,
 holder VARCHAR(100) NOT NULL DEFAULT '',
 certificate_no VARCHAR(100) NOT NULL DEFAULT '',
 valid_until DATE NULL,
 files TEXT NOT NULL,
 version INT NOT NULL DEFAULT 0,
 created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
SELECT '公司与人员资质',@brand,8,'report-credentials','zhongji/reports/credentials','ReportCredentials','C','0','0','management:credential:list','documentation',1,1,'admin',NOW()
WHERE @brand IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/reports/credentials');
SET @credentials=(SELECT menu_id FROM sys_menu WHERE component='zhongji/reports/credentials' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
SELECT '资质编辑',@credentials,1,'#','F','0','0','management:credential:edit',1,1,'admin',NOW()
WHERE @credentials IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:credential:edit');
