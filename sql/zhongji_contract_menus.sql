-- Keep existing role grants and reuse the original contract page for maintenance.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
VALUES('众基消防',0,0,'zhongji',NULL,'M','0','0','','build',1,1,'admin',NOW());
SET @zjroot=LAST_INSERT_ID();
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
VALUES('合同管理',@zjroot,2,'contracts',NULL,'M','0','0','','documentation',1,1,'admin',NOW());
SET @zjcontracts=LAST_INSERT_ID();
CREATE TEMPORARY TABLE zj_contract_roles AS SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms LIKE 'management:contract:%';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
SELECT '维护保养合同',@zjcontracts,1,'maintenance','management/contract/index','C','0','0','management:contract:list','documentation',1,1,'admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='management/contract/index');
SET @oldcontract=(SELECT menu_id FROM sys_menu WHERE component='management/contract/index' LIMIT 1);
UPDATE sys_menu SET menu_name='维护保养合同',parent_id=@zjcontracts,order_num=1,path='maintenance',component='zhongji/contracts/maintenance',route_name='ZhongjiMaintenance',is_cache=1 WHERE menu_id=@oldcontract;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@zjroot FROM zj_contract_roles;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@zjcontracts FROM zj_contract_roles;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@oldcontract FROM zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('消防检测合同',@zjcontracts,2,'inspection','zhongji/contracts/inspection','ZhongjiInspection','C','0','0','management:contract:list','documentation',1,1,'admin',NOW());
SET @page=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@page FROM zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('消防评定合同',@zjcontracts,3,'rating','zhongji/contracts/rating','ZhongjiRating','C','0','0','management:contract:list','documentation',1,1,'admin',NOW());
SET @page=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@page FROM zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('消防评估合同',@zjcontracts,4,'assessment','zhongji/contracts/assessment','ZhongjiAssessment','C','0','0','management:contract:list','documentation',1,1,'admin',NOW());
SET @page=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@page FROM zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time) VALUES('其他技术服务合同',@zjcontracts,5,'other','zhongji/contracts/other','ZhongjiOther','C','0','0','management:contract:list','documentation',1,1,'admin',NOW());
SET @page=LAST_INSERT_ID();
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@page FROM zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
VALUES('中间人管理',@zjroot,1,'intermediaries','zhongji/intermediary/index','ZhongjiIntermediary','C','0','0','management:intermediary:list','peoples',1,1,'admin',NOW());
SET @people=LAST_INSERT_ID();
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time)
VALUES('中间人编辑',@people,1,'#','F','0','0','management:intermediary:edit','admin',NOW());
DROP TEMPORARY TABLE zj_contract_roles;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '合同查询',@oldcontract,1,'#','F','0','0','management:contract:query','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:contract:query');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '合同新增',@oldcontract,1,'#','F','0','0','management:contract:add','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:contract:add');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '合同修改',@oldcontract,1,'#','F','0','0','management:contract:edit','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:contract:edit');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '合同删除',@oldcontract,1,'#','F','0','0','management:contract:remove','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:contract:remove');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '合同导出',@oldcontract,1,'#','F','0','0','management:contract:export','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:contract:export');
