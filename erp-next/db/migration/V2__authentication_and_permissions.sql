ALTER TABLE erp_identity
  ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
  ADD COLUMN locked_until DATETIME(3) NULL;

CREATE TABLE erp_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_erp_role_permission_role FOREIGN KEY (role_id) REFERENCES erp_role(id),
  CONSTRAINT fk_erp_role_permission_permission FOREIGN KEY (permission_id) REFERENCES erp_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_auth_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  identity_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_auth_session_token_hash (token_hash),
  KEY idx_erp_auth_session_identity (identity_id, expires_at),
  CONSTRAINT fk_erp_auth_session_identity FOREIGN KEY (identity_id) REFERENCES erp_identity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_role(code,name) VALUES
  ('admin','系统管理员'),
  ('employee','普通员工'),
  ('project_manager','项目负责人'),
  ('finance','财务人员')
  ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT INTO erp_permission(code,name) VALUES
  ('workspace:read','访问工作台'),
  ('identity:read','查看账号与角色'),
  ('identity:write','创建与维护账号'),
  ('audit:read','查看操作审计'),
  ('project:read','查看项目'),
  ('project:write','维护项目'),
  ('contract:read','查看合同'),
  ('contract:write','维护合同'),
  ('finance:read','查看财务'),
  ('finance:write','维护财务')
  ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('workspace:read')
  WHERE r.code='employee';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('workspace:read','project:read','project:write','contract:read')
  WHERE r.code='project_manager';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('workspace:read','finance:read','finance:write')
  WHERE r.code='finance';
