-- V8: company-specific business model. Existing V1-V7 migrations remain immutable.
-- Employee records are independent from login identities and departments are not required in daily work.
ALTER TABLE erp_employee
  ADD COLUMN employee_type VARCHAR(24) NOT NULL DEFAULT 'employee' AFTER full_name,
  ADD CONSTRAINT ck_erp_employee_type CHECK (employee_type IN ('engineer','operator','employee','manager'));

ALTER TABLE erp_employee_qualification
  ADD COLUMN qualification_type VARCHAR(40) NOT NULL DEFAULT 'other' AFTER qualification_name,
  ADD CONSTRAINT ck_erp_qualification_type CHECK (qualification_type IN ('engineer','other'));

CREATE TABLE erp_partner (
  id BIGINT NOT NULL AUTO_INCREMENT,
  partner_code VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  contact_name VARCHAR(160) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(190) NULL,
  address VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_partner_code (partner_code),
  KEY idx_erp_partner_name (name),
  CONSTRAINT ck_erp_partner_status CHECK (status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE erp_contract
  ADD COLUMN signing_mode VARCHAR(24) NOT NULL DEFAULT 'direct' AFTER contract_type,
  ADD COLUMN intermediary_id BIGINT NULL AFTER signing_mode,
  ADD COLUMN intermediary_share_percent DECIMAL(5,2) NULL AFTER intermediary_id,
  ADD COLUMN revision_no INT NOT NULL DEFAULT 1 AFTER status,
  ADD CONSTRAINT fk_erp_contract_partner FOREIGN KEY (intermediary_id) REFERENCES erp_partner(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_erp_contract_signing_mode CHECK (signing_mode IN ('direct','intermediary')),
  ADD CONSTRAINT ck_erp_contract_intermediary_share CHECK (
    intermediary_share_percent IS NULL OR intermediary_share_percent BETWEEN 0 AND 100
  );

-- Snapshot every contract's initial terms; future edits append revisions instead of erasing history.
CREATE TABLE erp_contract_revision (
  contract_id BIGINT NOT NULL,
  revision_no INT NOT NULL,
  snapshot_json JSON NOT NULL,
  snapshot_sha256 CHAR(64) NOT NULL,
  change_reason VARCHAR(1000) NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (contract_id, revision_no),
  CONSTRAINT fk_erp_contract_revision_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_revision_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_contract_revision(contract_id,revision_no,snapshot_json,snapshot_sha256,change_reason)
SELECT id,1,
  JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
    'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,
    'status',status,'ownerEmployeeId',owner_employee_id,'notes',notes),
  SHA2(CAST(JSON_OBJECT('contractNo',contract_no,'title',title,'customerId',customer_id,'contractType',contract_type,
    'amount',amount,'currency',currency,'signedOn',signed_on,'startsOn',starts_on,'endsOn',ends_on,
    'status',status,'ownerEmployeeId',owner_employee_id,'notes',notes) AS CHAR),256),
  'V8 初始合同快照'
FROM erp_contract;

-- One contract can cover multiple projects; a project can retain multiple annual/renewal contracts.
CREATE TABLE erp_project_contract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  relation_type VARCHAR(24) NOT NULL DEFAULT 'initial',
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  linked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_project_contract (project_id,contract_id),
  KEY idx_erp_project_contract_current (project_id,is_current),
  KEY idx_erp_project_contract_contract (contract_id),
  CONSTRAINT fk_erp_project_contract_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_project_contract_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_project_contract_relation CHECK (relation_type IN ('initial','renewal','supplemental'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO erp_project_contract(project_id,contract_id,relation_type,is_current)
SELECT id,contract_id,'initial',TRUE FROM erp_project WHERE contract_id IS NOT NULL;

ALTER TABLE erp_project
  ADD COLUMN technical_lead_employee_id BIGINT NULL AFTER manager_employee_id,
  ADD COLUMN honeybee_uploaded BOOLEAN NOT NULL DEFAULT FALSE AFTER progress,
  ADD COLUMN honeybee_uploaded_on DATE NULL AFTER honeybee_uploaded,
  ADD COLUMN documents_ready BOOLEAN NOT NULL DEFAULT FALSE AFTER honeybee_uploaded_on,
  ADD COLUMN documents_ready_on DATE NULL AFTER documents_ready,
  ADD COLUMN maintenance_card_sent BOOLEAN NOT NULL DEFAULT FALSE AFTER documents_ready_on,
  ADD COLUMN maintenance_card_sent_on DATE NULL AFTER maintenance_card_sent,
  ADD CONSTRAINT fk_erp_project_technical_lead FOREIGN KEY (technical_lead_employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_erp_project_distinct_leads CHECK (
    manager_employee_id IS NULL OR technical_lead_employee_id IS NULL OR manager_employee_id <> technical_lead_employee_id
  );

-- Contract-specific documents and maintenance cards remain attached to the exact renewal they belong to.
CREATE TABLE erp_contract_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  document_type VARCHAR(40) NOT NULL,
  label VARCHAR(240) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_contract_document (contract_id,file_id,document_type),
  KEY idx_erp_contract_document_file (file_id),
  CONSTRAINT fk_erp_contract_document_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_document_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_document_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_contract_document_type CHECK (document_type IN ('contract','maintenance_card','project_material','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Simplify the visible role labels while preserving existing role IDs and assignments.
UPDATE erp_role SET name='管理人员' WHERE code='finance';
UPDATE erp_role SET name='工程师' WHERE code='project_manager';
UPDATE erp_role SET name='普通员工' WHERE code='employee';
INSERT INTO erp_role(code,name) VALUES ('operator','操作员')
  ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT IGNORE INTO erp_permission(code,name) VALUES
 ('partner:read','查看合作方'),
 ('partner:write','维护合作方'),
 ('maintenance:read','查看维保项目'),
 ('maintenance:write','维护维保项目'),
 ('contract_document:read','查看合同资料'),
 ('contract_document:write','维护合同资料'),
 ('site_visit:read','查看现场服务'),
 ('site_visit:write','维护现场服务'),
 ('project_issue:read','查看问题整改'),
 ('project_issue:write','维护问题整改'),
 ('report_delivery:read','查看报告邮寄'),
 ('report_delivery:write','登记报告邮寄'),
 ('report_photo:read','查看照片整理批次'),
 ('report_photo:write','维护照片整理批次');

-- Everyone who works with projects can read the business records. Write access follows the actual role.
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('employee:read','customer:read','contract:read','project:read','partner:read','maintenance:read',
  'contract_document:read','site_visit:read','project_issue:read','report:read','report_delivery:read','report_photo:read','file:read')
WHERE r.code IN ('employee','operator','project_manager','finance','admin');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('site_visit:write','project_issue:write','report_photo:write','report:write','report_delivery:write','file:upload')
WHERE r.code IN ('employee','operator','project_manager','finance','admin');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('customer:write','contract:write','project:write','partner:write','maintenance:write','contract_document:write',
  'employee:write')
WHERE r.code IN ('project_manager','finance','admin');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('report:review','report:issue')
WHERE r.code IN ('project_manager','finance','admin');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
