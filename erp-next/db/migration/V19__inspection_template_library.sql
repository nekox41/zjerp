-- V19: immutable reusable Word templates; blank system_name is the common report base.
CREATE TABLE erp_inspection_template (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 template_kind VARCHAR(16) NOT NULL,
 system_name VARCHAR(80) NOT NULL DEFAULT '',
 template_name VARCHAR(150) NOT NULL,
 revision_no INT NOT NULL,
 file_id BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 notes VARCHAR(1000) NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_inspection_template_revision(template_kind,system_name,template_name,revision_no),
 KEY idx_inspection_template_active(template_kind,active,system_name),
 FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CHECK(template_kind IN ('base','system')),
 CHECK((template_kind='base' AND system_name='') OR (template_kind='system' AND system_name<>''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO erp_permission(code,name) VALUES ('report:template:write','管理检测报告模板库')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code='report:template:write' WHERE r.code='admin';
