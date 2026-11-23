-- V8: align the business model with the company's actual workflow.
-- Keep the legacy organization table/columns for compatibility, but no longer require or expose departments in daily UI.
ALTER TABLE erp_employee
  ADD COLUMN employee_type VARCHAR(24) NOT NULL DEFAULT 'employee' AFTER full_name,
  ADD CONSTRAINT ck_erp_employee_type CHECK (employee_type IN ('engineer','operator','employee','manager'));

ALTER TABLE erp_contract
  ADD COLUMN signing_mode VARCHAR(24) NOT NULL DEFAULT 'direct' AFTER contract_type,
  ADD COLUMN intermediary_name VARCHAR(200) NULL AFTER signing_mode,
  ADD COLUMN intermediary_share_percent DECIMAL(5,2) NULL AFTER intermediary_name,
  ADD CONSTRAINT ck_erp_contract_signing_mode CHECK (signing_mode IN ('direct','intermediary')),
  ADD CONSTRAINT ck_erp_contract_intermediary_share CHECK (
    intermediary_share_percent IS NULL OR intermediary_share_percent BETWEEN 0 AND 100
  );

-- A project may be created before a contract exists, or remain uncontracted.
ALTER TABLE erp_project
  MODIFY COLUMN contract_id BIGINT NULL,
  ADD COLUMN technical_lead_employee_id BIGINT NULL AFTER manager_employee_id,
  ADD COLUMN honeybee_uploaded BOOLEAN NOT NULL DEFAULT FALSE AFTER progress,
  ADD COLUMN documents_ready BOOLEAN NOT NULL DEFAULT FALSE AFTER honeybee_uploaded,
  ADD COLUMN maintenance_card_sent BOOLEAN NOT NULL DEFAULT FALSE AFTER documents_ready,
  ADD CONSTRAINT fk_erp_project_technical_lead FOREIGN KEY (technical_lead_employee_id) REFERENCES erp_employee(id);

-- A maintenance project can span multiple annual contracts (renewals).
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
  CONSTRAINT fk_erp_project_contract_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_project_contract_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_project_contract_relation CHECK (relation_type IN ('initial','renewal','supplemental'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO erp_project_contract(project_id,contract_id,relation_type,is_current)
 SELECT id,contract_id,'initial',TRUE FROM erp_project WHERE contract_id IS NOT NULL;

-- Keep the existing role IDs and account assignments; simplify their visible names.
UPDATE erp_role SET name='管理人员' WHERE code='finance';
UPDATE erp_role SET name='工程师' WHERE code='project_manager';
UPDATE erp_role SET name='普通员工' WHERE code='employee';
INSERT INTO erp_role(code,name) VALUES ('operator','操作员')
  ON DUPLICATE KEY UPDATE name=VALUES(name);

-- All staff can see core business records and create their own expense claims.
INSERT IGNORE INTO erp_permission(code,name) VALUES
 ('employee:read','查看员工'),
 ('customer:read','查看客户'),
 ('contract:read','查看合同'),
 ('project:read','查看项目'),
 ('project:write','维护项目'),
 ('expense:read','查看报账'),
 ('expense:write','提交报账'),
 ('expense:approve','审批报账'),
 ('expense:pay','登记付款'),
 ('invoice:read','查看财务台账'),
 ('invoice:write','维护财务台账'),
 ('invoice:approve','审核财务台账'),
 ('invoice:settle','登记收付款');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('workspace:read','employee:read','customer:read','contract:read','project:read','expense:read','expense:write','file:upload','file:read')
 WHERE r.code IN ('employee','operator');

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('workspace:read','employee:read','customer:read','contract:read','contract:write','project:read','project:write','expense:read','expense:write','file:upload','file:read')
 WHERE r.code='project_manager';

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('workspace:read','employee:read','customer:read','customer:write','contract:read','contract:write','project:read','project:write',
  'expense:read','expense:write','expense:approve','expense:pay','invoice:read','invoice:write','invoice:approve','invoice:settle',
  'identity:read','identity:write','audit:read','file:upload','file:read')
 WHERE r.code='finance';

-- Administrators retain all permissions; ordinary roles never gain finance/invoice permissions.
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
