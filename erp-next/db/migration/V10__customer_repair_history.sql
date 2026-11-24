-- V10: independent customer repair records; no duplicated Honeybee reporting.
CREATE TABLE erp_repair_record (
 id BIGINT NOT NULL AUTO_INCREMENT,
 project_id BIGINT NOT NULL,
 repair_date DATE NOT NULL,
 title VARCHAR(240) NOT NULL,
 description TEXT NOT NULL,
 result_summary TEXT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 KEY idx_erp_repair_project_date(project_id,repair_date,id),
 CONSTRAINT fk_erp_repair_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_repair_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_repair_staff (
 repair_id BIGINT NOT NULL,
 employee_id BIGINT NOT NULL,
 PRIMARY KEY(repair_id,employee_id),
 CONSTRAINT fk_erp_repair_staff_record FOREIGN KEY(repair_id) REFERENCES erp_repair_record(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_repair_staff_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('repair:read','查看维修记录'),('repair:write','登记与维护维修记录')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('repair:read','repair:write')
 WHERE r.code='project_manager';
