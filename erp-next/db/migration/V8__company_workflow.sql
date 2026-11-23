-- V8: model the company's real customer / intermediary / contract / project / delivery workflow.
-- Keep V1-V7 immutable. This migration only targets the isolated erp_next database.

ALTER TABLE erp_employee
  ADD COLUMN employee_type VARCHAR(24) NOT NULL DEFAULT 'employee' AFTER full_name,
  ADD CONSTRAINT ck_erp_employee_type
    CHECK (employee_type IN ('engineer','operator','employee','manager'));

CREATE TABLE erp_intermediary (
  id BIGINT NOT NULL AUTO_INCREMENT,
  intermediary_code VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  contact_name VARCHAR(160) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(190) NULL,
  address VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  notes TEXT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_intermediary_code (intermediary_code),
  KEY idx_erp_intermediary_name (name),
  KEY idx_erp_intermediary_status (status),
  CONSTRAINT fk_erp_intermediary_creator FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_intermediary_status CHECK (status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE erp_contract
  ADD COLUMN signing_mode VARCHAR(24) NOT NULL DEFAULT 'direct' AFTER contract_type,
  ADD COLUMN intermediary_id BIGINT NULL AFTER signing_mode,
  ADD COLUMN intermediary_name_snapshot VARCHAR(200) NULL AFTER intermediary_id,
  ADD COLUMN intermediary_share_percent DECIMAL(5,2) NULL AFTER intermediary_name_snapshot,
  ADD KEY idx_erp_contract_intermediary (intermediary_id),
  ADD CONSTRAINT fk_erp_contract_intermediary FOREIGN KEY (intermediary_id) REFERENCES erp_intermediary(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_erp_contract_signing_mode CHECK (signing_mode IN ('direct','intermediary')),
  ADD CONSTRAINT ck_erp_contract_intermediary_terms CHECK (
    (signing_mode='direct' AND intermediary_id IS NULL AND intermediary_share_percent IS NULL)
    OR
    (signing_mode='intermediary' AND intermediary_id IS NOT NULL AND intermediary_share_percent BETWEEN 0 AND 100)
  );

ALTER TABLE erp_project
  MODIFY COLUMN contract_id BIGINT NULL,
  ADD COLUMN technical_lead_employee_id BIGINT NULL AFTER manager_employee_id,
  ADD COLUMN honeybee_uploaded BOOLEAN NOT NULL DEFAULT FALSE AFTER progress,
  ADD COLUMN documents_ready BOOLEAN NOT NULL DEFAULT FALSE AFTER honeybee_uploaded,
  ADD COLUMN maintenance_card_sent BOOLEAN NOT NULL DEFAULT FALSE AFTER documents_ready,
  ADD CONSTRAINT fk_erp_project_technical_lead FOREIGN KEY (technical_lead_employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_erp_project_leads_distinct CHECK (
    manager_employee_id IS NULL OR technical_lead_employee_id IS NULL OR manager_employee_id <> technical_lead_employee_id
  );

-- One project can have no contract or several contracts across renewals.
-- A contract can cover multiple projects.
CREATE TABLE erp_project_contract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  relation_type VARCHAR(24) NOT NULL DEFAULT 'initial',
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  current_project_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_current THEN project_id ELSE NULL END) STORED,
  linked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_project_contract (project_id,contract_id),
  UNIQUE KEY uk_erp_project_current_contract (current_project_id),
  KEY idx_erp_project_contract_contract (contract_id),
  CONSTRAINT ck_erp_project_contract_relation CHECK (relation_type IN ('initial','renewal','supplemental'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO erp_project_contract(project_id,contract_id,relation_type,is_current)
SELECT id,contract_id,'initial',TRUE FROM erp_project WHERE contract_id IS NOT NULL;

-- Report samples contain multiple buildings / parking phases and shared facilities.
-- These are project-specific scope units, not a forced one-project/one-building relation.
CREATE TABLE erp_project_scope_unit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  unit_name VARCHAR(240) NOT NULL,
  unit_type VARCHAR(32) NOT NULL DEFAULT 'building',
  structure_type VARCHAR(120) NULL,
  usage_name VARCHAR(160) NULL,
  fire_resistance_grade VARCHAR(80) NULL,
  above_ground_floors VARCHAR(40) NULL,
  underground_floors VARCHAR(40) NULL,
  building_height DECIMAL(10,2) NULL,
  total_area DECIMAL(14,2) NULL,
  above_ground_area DECIMAL(14,2) NULL,
  underground_area DECIMAL(14,2) NULL,
  location_description VARCHAR(500) NULL,
  is_shared_facility BOOLEAN NOT NULL DEFAULT FALSE,
  service_scope TEXT NULL,
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_project_scope (project_id,unit_type),
  CONSTRAINT fk_erp_project_scope_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT ck_erp_project_scope_type CHECK (unit_type IN ('building','parking_phase','shared_facility','other')),
  CONSTRAINT ck_erp_project_scope_area CHECK (
    (total_area IS NULL OR total_area >= 0) AND
    (above_ground_area IS NULL OR above_ground_area >= 0) AND
    (underground_area IS NULL OR underground_area >= 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_contract_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  attachment_type VARCHAR(32) NOT NULL DEFAULT 'contract',
  title VARCHAR(240) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_contract_attachment (contract_id,file_id),
  KEY idx_erp_contract_attachment_file (file_id),
  CONSTRAINT fk_erp_contract_attachment_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_attachment_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_attachment_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_contract_attachment_type CHECK (attachment_type IN ('signed_contract','amendment','maintenance_card','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Maintenance visits are optional, primarily for out-of-town projects; do not force monthly records.
CREATE TABLE erp_maintenance_visit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NULL,
  visited_on DATE NOT NULL,
  summary TEXT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_maintenance_visit_project (project_id,visited_on),
  CONSTRAINT fk_erp_maintenance_visit_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_maintenance_visit_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_maintenance_visit_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_visit_employee (
  visit_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  PRIMARY KEY (visit_id,employee_id),
  CONSTRAINT fk_erp_maintenance_visit_employee_visit FOREIGN KEY (visit_id) REFERENCES erp_maintenance_visit(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_maintenance_visit_employee_employee FOREIGN KEY (employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Technical service can have multiple site visits; no generic work-package/department workflow is required.
CREATE TABLE erp_service_visit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  visit_no VARCHAR(80) NOT NULL,
  visit_type VARCHAR(32) NOT NULL DEFAULT 'onsite',
  visited_at DATETIME(3) NOT NULL,
  summary TEXT NOT NULL,
  follow_up TEXT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_service_visit_no (project_id,visit_no),
  KEY idx_erp_service_visit_project (project_id,visited_at),
  CONSTRAINT fk_erp_service_visit_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_service_visit_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_service_visit_type CHECK (visit_type IN ('onsite','remote','meeting','recheck'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit_employee (
  visit_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  PRIMARY KEY (visit_id,employee_id),
  CONSTRAINT fk_erp_service_visit_employee_visit FOREIGN KEY (visit_id) REFERENCES erp_service_visit(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_service_visit_employee_employee FOREIGN KEY (employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_issue_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  note TEXT NULL,
  actor_identity_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_project_issue_event (issue_id,created_at),
  CONSTRAINT fk_erp_project_issue_event_issue FOREIGN KEY (issue_id) REFERENCES erp_project_issue(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_issue_event_actor FOREIGN KEY (actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Reports belong directly to projects. The old work-package relation remains nullable for
-- compatibility with existing records and older API clients.
ALTER TABLE erp_service_report
  MODIFY COLUMN work_package_id BIGINT NULL,
  ADD COLUMN project_id BIGINT NULL AFTER work_package_id,
  ADD KEY idx_erp_service_report_project (project_id,status),
  ADD CONSTRAINT fk_erp_service_report_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_erp_service_report_owner CHECK (project_id IS NOT NULL OR work_package_id IS NOT NULL);

CREATE TABLE erp_report_delivery (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  recipient_name VARCHAR(240) NOT NULL,
  contact_name VARCHAR(160) NULL,
  contact_phone VARCHAR(40) NULL,
  delivery_address VARCHAR(800) NOT NULL,
  courier_name VARCHAR(120) NULL,
  tracking_no VARCHAR(120) NULL,
  sent_at DATETIME(3) NULL,
  delivery_status VARCHAR(24) NOT NULL DEFAULT 'prepared',
  delivered_at DATETIME(3) NULL,
  notes TEXT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_report_delivery_report (report_id,version_no,created_at),
  KEY idx_erp_report_delivery_tracking (tracking_no),
  CONSTRAINT fk_erp_report_delivery_version FOREIGN KEY (report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_delivery_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_report_delivery_status CHECK (delivery_status IN ('prepared','sent','delivered','returned','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('intermediary:read','查看中间人'),('intermediary:write','维护中间人'),
 ('maintenance:read','查看维护保养'),('maintenance:write','维护维护保养'),
 ('service:read','查看现场服务'),('service:write','维护现场服务'),
 ('report:read','查看技术服务报告'),('report:write','编制技术服务报告'),
 ('report:review','审核技术服务报告'),('report:issue','签发技术服务报告'),
 ('file:upload','上传业务附件'),('file:read','下载业务附件'),
 ('photo:read','查看照片整理'),('photo:write','整理并插入照片'),
 ('project:scope:read','查看项目检测范围'),('project:scope:write','维护项目检测范围')
 ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Managers have full access. Keep legacy role codes stable so existing account assignments survive.
INSERT INTO erp_role(code,name) VALUES ('operator','操作员') ON DUPLICATE KEY UPDATE name=VALUES(name);
UPDATE erp_role SET name='普通员工' WHERE code='employee';
UPDATE erp_role SET name='工程师' WHERE code='project_manager';
UPDATE erp_role SET name='管理人员' WHERE code='finance';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code IN ('admin','finance');
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
    ('workspace:read','employee:read','customer:read','contract:read','project:read',
     'intermediary:read','maintenance:read','service:read','report:read','file:read','photo:read')
  WHERE r.code='employee';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
    ('workspace:read','employee:read','customer:read','contract:read','project:read','project:write',
     'intermediary:read','maintenance:read','maintenance:write','service:read','service:write',
     'report:read','report:write','report:review','file:upload','file:read','photo:read','photo:write',
     'project:scope:read','project:scope:write')
  WHERE r.code='project_manager';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
  SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
    ('workspace:read','employee:read','customer:read','contract:read','project:read',
     'maintenance:read','maintenance:write','service:read','service:write','file:upload','file:read')
  WHERE r.code='operator';
