SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
CREATE TEMPORARY TABLE old_project_menus AS WITH RECURSIVE nodes AS (
SELECT menu_id FROM sys_menu WHERE component='management/projectSystem/index' OR (parent_id=0 AND path='projects')
UNION ALL SELECT m.menu_id FROM sys_menu m JOIN nodes p ON m.parent_id=p.menu_id
) SELECT DISTINCT menu_id FROM nodes;
CREATE TEMPORARY TABLE old_project_grants AS SELECT DISTINCT rm.role_id,
CASE WHEN m.perms LIKE '%:add' THEN 'add' WHEN m.perms LIKE '%:edit' THEN 'edit' WHEN m.perms LIKE '%:query' THEN 'query' ELSE 'list' END action
FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id JOIN old_project_menus p ON p.menu_id=m.menu_id;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
VALUES('项目管理',@brand,3,'projects','M','0','0','','tree',1,1,'admin',NOW());
SET @projects=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@brand FROM old_project_grants;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projects FROM old_project_grants;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('维护保养项目',@projects,1,'maintenance','zhongji/projects/maintenance','WorkProjectMaintenance','C','0','0','management:project:list','tree',1,1,'admin',NOW());
SET @projectpage=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectpage FROM old_project_grants;
SET @firstproject=@projectpage;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('检测项目',@projects,2,'inspection','zhongji/projects/inspection','WorkProjectInspection','C','0','0','management:project:list','tree',1,1,'admin',NOW());
SET @projectpage=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectpage FROM old_project_grants;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('评定项目',@projects,3,'rating','zhongji/projects/rating','WorkProjectRating','C','0','0','management:project:list','tree',1,1,'admin',NOW());
SET @projectpage=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectpage FROM old_project_grants;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('评估项目',@projects,4,'assessment','zhongji/projects/assessment','WorkProjectAssessment','C','0','0','management:project:list','tree',1,1,'admin',NOW());
SET @projectpage=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectpage FROM old_project_grants;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('其他技术服务项目',@projects,5,'other','zhongji/projects/other','WorkProjectOther','C','0','0','management:project:list','tree',1,1,'admin',NOW());
SET @projectpage=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectpage FROM old_project_grants;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) VALUES('项目查看',@firstproject,1,'#','F','0','0','management:project:query','admin',NOW());
SET @projectaction=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectaction FROM old_project_grants WHERE action IN ('query','list','add','edit');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) VALUES('创建及编号申请',@firstproject,1,'#','F','0','0','management:project:add','admin',NOW());
SET @projectaction=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectaction FROM old_project_grants WHERE action='add';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) VALUES('项目信息编辑',@firstproject,1,'#','F','0','0','management:project:edit','admin',NOW());
SET @projectaction=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT DISTINCT role_id,@projectaction FROM old_project_grants WHERE action='edit';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) VALUES('项目报账总览',@firstproject,1,'#','F','0','0','management:project:expenses','admin',NOW());
SET @projectaction=LAST_INSERT_ID();
DELETE rm FROM sys_role_menu rm JOIN old_project_menus p ON p.menu_id=rm.menu_id;
DELETE m FROM sys_menu m JOIN old_project_menus p ON p.menu_id=m.menu_id;
DROP TEMPORARY TABLE old_project_grants;
DROP TEMPORARY TABLE old_project_menus;
