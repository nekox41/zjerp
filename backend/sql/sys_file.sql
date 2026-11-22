-- 上线前执行；不删除、不覆盖旧业务列。MySQL / InnoDB。
CREATE TABLE IF NOT EXISTS sys_file (
  file_id bigint NOT NULL AUTO_INCREMENT,
  file_name varchar(512) NOT NULL,
  file_path varchar(512) NOT NULL,
  file_url varchar(600) NOT NULL,
  file_hash char(64) DEFAULT NULL COMMENT '历史仅元数据迁移时未知',
  file_size bigint DEFAULT NULL COMMENT '字节数，NULL表示尚未核实',
  file_type varchar(255) DEFAULT NULL,
  file_ext varchar(16) DEFAULT NULL,
  biz_type varchar(64) DEFAULT NULL,
  biz_id bigint DEFAULT NULL,
  del_flag char(1) NOT NULL DEFAULT '0',
  create_by varchar(64) DEFAULT '',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64) DEFAULT '',
  update_time datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  PRIMARY KEY (file_id),
  KEY idx_file_biz (biz_type, biz_id, del_flag),
  KEY idx_file_hash (file_hash, del_flag),
  KEY idx_file_url (file_url(191), del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一文件元数据及业务附件关联';

-- 管理员自动拥有菜单。已有同名菜单时跳过。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '文件管理', 1, 20, 'file', 'system/file/index', 'SysFileManagement', 1, 0, 'C', '0', '0', 'system:file:list', 'documentation', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:file:list');

-- 顶层旧文件菜单也使用默认路由名 File，新菜单显式命名以避免 Vue Router 覆盖。
UPDATE sys_menu SET route_name = 'SysFileManagement'
WHERE perms = 'system:file:list' AND path = 'file' AND component = 'system/file/index';
