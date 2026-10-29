-- Current maintenance-card workspace. Safe to repeat; never removes historical cards/files/contacts.
CREATE TABLE IF NOT EXISTS zj_wbcard_material (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(200) NOT NULL,file VARCHAR(600) NOT NULL,remark VARCHAR(2000),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_wb_material_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE wbcard_workspace_roles AS
 SELECT DISTINCT rm.role_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE m.perms LIKE 'management:wbcard:%';
SET @brand=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='zhongji' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,icon,is_frame,is_cache,create_by,create_time)
 SELECT '维保卡生成',@brand,4,'maintenance-cards','M','0','0','documentation',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=@brand AND path='maintenance-cards');
SET @folder=(SELECT menu_id FROM sys_menu WHERE parent_id=@brand AND path='maintenance-cards' LIMIT 1);
SET @old=(SELECT menu_id FROM sys_menu WHERE component='management/wbcard/index' LIMIT 1);
UPDATE sys_menu SET menu_name='维保卡生成',parent_id=@folder,order_num=1,path='generate',component='zhongji/wbcard/generate',route_name='WbCardGeneration',is_cache=1 WHERE menu_id=@old;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
 SELECT '维保卡生成',@folder,1,'generate','zhongji/wbcard/generate','WbCardGeneration','C','0','0','management:wbcard:list','documentation',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/wbcard/generate');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
 SELECT '附带资料管理',@folder,2,'materials','zhongji/wbcard/materials','WbCardMaterials','C','0','0','management:wbcard:materials:list','documentation',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/wbcard/materials');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,visible,status,perms,icon,is_frame,is_cache,create_by,create_time)
 SELECT '联系人管理',@folder,3,'contacts','zhongji/wbcard/contacts','WbCardContacts','C','0','0','management:wbcard:contacts:list','peoples',1,1,'admin',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='zhongji/wbcard/contacts');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
 SELECT '附带资料维护',menu_id,1,'#','F','0','0','management:wbcard:materials:edit',1,1,'admin',NOW() FROM sys_menu
 WHERE component='zhongji/wbcard/materials' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:materials:edit');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time)
 SELECT '联系人维护',menu_id,1,'#','F','0','0','management:wbcard:contacts:edit',1,1,'admin',NOW() FROM sys_menu
 WHERE component='zhongji/wbcard/contacts' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:contacts:edit');
-- Preserve prior visibility. New resource write privileges must be assigned by an administrator.
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@brand FROM wbcard_workspace_roles;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,@folder FROM wbcard_workspace_roles;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM wbcard_workspace_roles r CROSS JOIN sys_menu m WHERE m.parent_id=@folder AND m.menu_type='C';
DROP TEMPORARY TABLE wbcard_workspace_roles;

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time) SELECT '维保卡查询',menu_id,1,'#','F','0','0','management:wbcard:query',1,1,'admin',NOW() FROM sys_menu WHERE component='zhongji/wbcard/generate' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:query');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time) SELECT '维保卡生成',menu_id,1,'#','F','0','0','management:wbcard:add',1,1,'admin',NOW() FROM sys_menu WHERE component='zhongji/wbcard/generate' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:add');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time) SELECT '打印邮寄记录维护',menu_id,1,'#','F','0','0','management:wbcard:edit',1,1,'admin',NOW() FROM sys_menu WHERE component='zhongji/wbcard/generate' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:edit');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,is_frame,is_cache,create_by,create_time) SELECT '维保卡记录删除',menu_id,1,'#','F','0','0','management:wbcard:remove',1,1,'admin',NOW() FROM sys_menu WHERE component='zhongji/wbcard/generate' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='management:wbcard:remove');
