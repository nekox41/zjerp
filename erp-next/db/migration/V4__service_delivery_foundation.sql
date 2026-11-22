-- V4: flexible projects, immutable contract amendments, staff credentials and service execution.
ALTER TABLE erp_project MODIFY contract_id BIGINT NULL;

CREATE TABLE erp_contract_amendment (
 id BIGINT NOT NULL AUTO_INCREMENT, contract_id BIGINT NOT NULL, amendment_no VARCHAR(80) NOT NULL,
 title VARCHAR(240) NOT NULL, change_summary TEXT NOT NULL, previous_amount DECIMAL(18,2) NOT NULL,
 revised_amount DECIMAL(18,2) NOT NULL, effective_on DATE NULL, status VARCHAR(20) NOT NULL DEFAULT 'draft',
 created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_amendment_no(contract_id,amendment_no), KEY idx_erp_amendment_contract(contract_id),
 CONSTRAINT fk_erp_amendment_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_amendment_actor FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_amendment_amount CHECK(previous_amount>=0 AND revised_amount>=0),
 CONSTRAINT ck_erp_amendment_status CHECK(status IN ('draft','approved','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_employee_qualification (
 id BIGINT NOT NULL AUTO_INCREMENT, employee_id BIGINT NOT NULL, qualification_name VARCHAR(200) NOT NULL,
 certificate_no VARCHAR(120) NULL, issuing_authority VARCHAR(200) NULL, issued_on DATE NULL, expires_on DATE NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'valid', notes TEXT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_qualification_employee(employee_id), KEY idx_erp_qualification_expiry(expires_on),
 CONSTRAINT fk_erp_qualification_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_qualification_status CHECK(status IN ('valid','expired','revoked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_issue (
 id BIGINT NOT NULL AUTO_INCREMENT, project_id BIGINT NOT NULL, title VARCHAR(240) NOT NULL,
 description TEXT NULL, severity VARCHAR(20) NOT NULL DEFAULT 'medium', owner_employee_id BIGINT NULL,
 due_on DATE NULL, status VARCHAR(20) NOT NULL DEFAULT 'open', resolution TEXT NULL,
 closed_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_project_issue(project_id,status), KEY idx_erp_issue_due(due_on),
 CONSTRAINT fk_erp_issue_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
 CONSTRAINT fk_erp_issue_owner FOREIGN KEY(owner_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_issue_severity CHECK(severity IN ('low','medium','high','critical')),
 CONSTRAINT ck_erp_issue_status CHECK(status IN ('open','in_progress','resolved','closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_work_package (
 id BIGINT NOT NULL AUTO_INCREMENT, project_id BIGINT NOT NULL, package_no VARCHAR(80) NOT NULL,
 title VARCHAR(240) NOT NULL, description TEXT NULL, assignee_employee_id BIGINT NULL,
 planned_start DATE NULL, planned_end DATE NULL, status VARCHAR(24) NOT NULL DEFAULT 'planned',
 progress DECIMAL(5,2) NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_work_package_no(project_id,package_no),
 KEY idx_erp_work_package_project(project_id,status),
 CONSTRAINT fk_erp_work_package_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
 CONSTRAINT fk_erp_work_package_assignee FOREIGN KEY(assignee_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_work_package_progress CHECK(progress BETWEEN 0 AND 100),
 CONSTRAINT ck_erp_work_package_status CHECK(status IN ('planned','in_progress','blocked','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_record (
 id BIGINT NOT NULL AUTO_INCREMENT, work_package_id BIGINT NOT NULL, record_type VARCHAR(40) NOT NULL,
 title VARCHAR(240) NOT NULL, content TEXT NOT NULL, performed_on DATETIME(3) NOT NULL,
 recorded_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_service_record_package(work_package_id,performed_on),
 CONSTRAINT fk_erp_service_record_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE CASCADE,
 CONSTRAINT fk_erp_service_record_actor FOREIGN KEY(recorded_by) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('service:read','查看服务工作包'),('service:write','维护服务工作包')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('service:read','service:write','employee:read')
 WHERE r.code='project_manager';
