SET @finance=(SELECT menu_id FROM sys_menu WHERE parent_id=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1) AND path='finance' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
 SELECT '财务工作台',@finance,0,'workspace','zhongji/finance/workspace','FinanceWorkspace','C','0','0','zhongji:finance:list','dashboard',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/finance/workspace');
SET @workspace=(SELECT menu_id FROM sys_menu WHERE component='zhongji/finance/workspace' LIMIT 1);
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT rm.role_id,@workspace FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms='zhongji:finance:list';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
 SELECT '资金与开票记录维护',@workspace,1,'#','F','0','0','zhongji:finance:ledger',1,1,'admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='zhongji:finance:ledger');
