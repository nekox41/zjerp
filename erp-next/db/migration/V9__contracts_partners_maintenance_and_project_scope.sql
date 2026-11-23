-- V9: business rules from the ERP V1 product definition.
-- Keep the legacy single contract_id column for compatibility; the relation table is canonical.

CREATE TABLE erp_partner (
 id BIGINT NOT NULL AUTO_INCREMENT,
 partner_code VARCHAR(64) NOT NULL,
 name VARCHAR(240) NOT NULL,
 partner_type VARCHAR(40) NOT NULL DEFAULT 'introducer',
 contact_name VARCHAR(160) NULL,
 phone VARCHAR(40) NULL,
 email VARCHAR(190) NULL,
 address VARCHAR(500) NULL,
 notes TEXT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'active',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_partner_code(partner_code),
 KEY idx_erp_partner_name(name),
 CONSTRAINT ck_erp_partner_status CHECK(status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE erp_contract
 ADD COLUMN service_type VARCHAR(40) NOT NULL DEFAULT 'other',
 ADD COLUMN signing_source VARCHAR(24) NOT NULL DEFAULT 'direct',
 ADD COLUMN partner_share_percent DECIMAL(5,2) NULL,
 ADD COLUMN current_flag BOOLEAN NOT NULL DEFAULT TRUE,
 ADD CONSTRAINT ck_erp_contract_service_type CHECK(service_type IN ('maintenance','fire_inspection','fire_assessment','fire_evaluation','other')),
 ADD CONSTRAINT ck_erp_contract_signing_source CHECK(signing_source IN ('direct','partner')),
 ADD CONSTRAINT ck_erp_contract_partner_share CHECK(partner_share_percent IS NULL OR (partner_share_percent>=0 AND partner_share_percent<=100));

CREATE TABLE erp_contract_partner (
 contract_id BIGINT NOT NULL,
 partner_id BIGINT NOT NULL,
 share_percent DECIMAL(5,2) NOT NULL,
 notes VARCHAR(1000) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(contract_id,partner_id),
 CONSTRAINT fk_erp_contract_partner_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_contract_partner_partner FOREIGN KEY(partner_id) REFERENCES erp_partner(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_contract_partner_share CHECK(share_percent>=0 AND share_percent<=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_contract_project (
 contract_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL,
 relation_type VARCHAR(24) NOT NULL DEFAULT 'service',
 linked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(contract_id,project_id),
 KEY idx_erp_contract_project_project(project_id),
 CONSTRAINT fk_erp_contract_project_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_contract_project_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_contract_project_type CHECK(relation_type IN ('service','renewal','supplement','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO erp_contract_project(contract_id,project_id,relation_type)
 SELECT contract_id,id,'service' FROM erp_project WHERE contract_id IS NOT NULL;

ALTER TABLE erp_project
 ADD COLUMN technical_lead_employee_id BIGINT NULL,
 ADD CONSTRAINT fk_erp_project_technical_lead FOREIGN KEY(technical_lead_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL;

CREATE TABLE erp_contract_file (
 id BIGINT NOT NULL AUTO_INCREMENT,
 contract_id BIGINT NOT NULL,
 file_id BIGINT NOT NULL,
 file_type VARCHAR(32) NOT NULL DEFAULT 'contract',
 title VARCHAR(240) NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_contract_file(contract_id,file_id),
 CONSTRAINT fk_erp_contract_file_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_contract_file_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_contract_file_actor FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_contract_file_type CHECK(file_type IN ('contract','maintenance_card','project_material','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_project (
 project_id BIGINT NOT NULL,
 honeybee_contract_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
 honeybee_project_created BOOLEAN NOT NULL DEFAULT FALSE,
 project_materials_completed BOOLEAN NOT NULL DEFAULT FALSE,
 public_card_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
 current_contract_id BIGINT NULL,
 notes TEXT NULL,
 updated_by BIGINT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(project_id),
 CONSTRAINT fk_erp_maintenance_project_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_maintenance_current_contract FOREIGN KEY(current_contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_maintenance_updated_by FOREIGN KEY(updated_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_maintenance_card_status CHECK(public_card_status IN ('not_started','preparing','sent','delivered'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_visit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 project_id BIGINT NOT NULL,
 contract_id BIGINT NULL,
 visit_date DATE NOT NULL,
 notes VARCHAR(1000) NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_maintenance_visit_project(project_id,visit_date),
 CONSTRAINT fk_erp_maintenance_visit_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_maintenance_visit_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE SET NULL,
 CONSTRAINT fk_erp_maintenance_visit_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_visit_staff (
 visit_id BIGINT NOT NULL,
 employee_id BIGINT NOT NULL,
 PRIMARY KEY(visit_id,employee_id),
 CONSTRAINT fk_erp_maintenance_staff_visit FOREIGN KEY(visit_id) REFERENCES erp_maintenance_visit(id) ON DELETE CASCADE,
 CONSTRAINT fk_erp_maintenance_staff_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('partner:read','查看合作方'),('partner:write','维护合作方'),
 ('maintenance:read','查看维保项目'),('maintenance:write','维护维保项目')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('partner:read','partner:write','maintenance:read','maintenance:write','contract:read','contract:write','project:read','project:write')
 WHERE r.code='project_manager';
