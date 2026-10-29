CREATE TEMPORARY TABLE retired_project_finance_roles AS SELECT rm.role_id,m.perms FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms LIKE 'outside:outside:%' OR m.perms LIKE 'financial:%' OR m.perms LIKE 'management:report:%';
CREATE TEMPORARY TABLE retired_project_finance_menus AS
WITH RECURSIVE retired AS (SELECT menu_id FROM sys_menu WHERE component='management/report/index' OR component LIKE 'financial/%' OR (parent_id=0 AND menu_name='财务模块') UNION ALL SELECT m.menu_id FROM sys_menu m JOIN retired r ON m.parent_id=r.menu_id) SELECT DISTINCT menu_id FROM retired;
DELETE rm FROM sys_role_menu rm JOIN retired_project_finance_menus old ON rm.menu_id=old.menu_id;
DELETE m FROM sys_menu m JOIN retired_project_finance_menus old ON m.menu_id=old.menu_id;
SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,icon,is_frame,is_cache,create_by,create_time) SELECT '财务管理',@brand,5,'finance','M','0','0','money',1,1,'admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@brand AND path='finance');
SET @finance=(SELECT menu_id FROM sys_menu WHERE parent_id=@brand AND path='finance' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) SELECT '报账',@finance,1,'submit','zhongji/finance/submit','ReimbursementSubmit','C','0','0','zhongji:finance:add','money',1,1,'admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/finance/submit');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) SELECT '报账总览',@finance,2,'overview','zhongji/finance/overview','ReimbursementOverview','C','0','0','zhongji:finance:list','chart',1,1,'admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/finance/overview');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time) SELECT '报账状态维护',menu_id,1,'#','F','0','0','zhongji:finance:edit',1,1,'admin',NOW() FROM sys_menu WHERE component='zhongji/finance/overview' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='zhongji:finance:edit');
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT role_id,@brand FROM retired_project_finance_roles WHERE perms NOT LIKE 'management:report:%';
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT role_id,@finance FROM retired_project_finance_roles WHERE perms NOT LIKE 'management:report:%';
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT r.role_id,m.menu_id FROM retired_project_finance_roles r JOIN sys_menu m ON m.perms='zhongji:finance:list' WHERE (r.perms LIKE '%:list' OR r.perms LIKE '%:query') AND r.perms NOT LIKE 'management:report:%';
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT r.role_id,m.menu_id FROM retired_project_finance_roles r JOIN sys_menu m ON m.perms='zhongji:finance:add' WHERE r.perms='outside:outside:add';
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT role_id,@brand FROM retired_project_finance_roles WHERE perms LIKE 'management:report:%';
INSERT IGNORE INTO sys_role_menu SELECT DISTINCT r.role_id,m.menu_id FROM retired_project_finance_roles r CROSS JOIN sys_menu m WHERE r.perms LIKE 'management:report:%' AND (m.component LIKE 'zhongji/projects/%' OR m.perms='management:project:query' OR (m.parent_id=@brand AND m.path='projects'));
DROP TEMPORARY TABLE retired_project_finance_roles;
DROP TEMPORARY TABLE retired_project_finance_menus;
