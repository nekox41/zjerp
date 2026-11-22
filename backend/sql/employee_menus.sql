SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
 SELECT '人员管理',@brand,7,'employees','zhongji/employees/index','Employees','C','0','0','management:employee:list','peoples',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/employees/index');
SET @employee=(SELECT menu_id FROM sys_menu WHERE component='zhongji/employees/index' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
 SELECT '人员维护',@employee,1,'#','F','0','0','management:employee:edit',1,1,'admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:employee:edit');
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT r.role_id,@employee FROM sys_role_menu r JOIN sys_menu m ON m.menu_id=r.menu_id WHERE m.perms IN ('management:credential:list','management:credential:edit');
UPDATE sys_menu SET menu_name='公司与人员资料' WHERE component='zhongji/reports/credentials';
DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms LIKE 'system:userMaterial:%';
DELETE FROM sys_menu WHERE perms LIKE 'system:userMaterial:%';
