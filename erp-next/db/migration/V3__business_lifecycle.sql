-- Business lifecycle domain: organization, employee, customer, contract and project.
ALTER TABLE erp_employee
  ADD COLUMN phone VARCHAR(40) NULL,
  ADD COLUMN email VARCHAR(190) NULL,
  ADD COLUMN position_name VARCHAR(120) NULL,
  ADD COLUMN joined_on DATE NULL,
  ADD COLUMN left_on DATE NULL;

CREATE TABLE erp_customer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_code VARCHAR(64) NOT NULL,
  name VARCHAR(240) NOT NULL,
  credit_code VARCHAR(32) NULL,
  customer_type VARCHAR(32) NOT NULL DEFAULT 'enterprise',
  industry VARCHAR(100) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(190) NULL,
  address VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  owner_employee_id BIGINT NULL,
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), UNIQUE KEY uk_erp_customer_code(customer_code),
  UNIQUE KEY uk_erp_customer_credit_code(credit_code),
  KEY idx_erp_customer_name(name), KEY idx_erp_customer_status(status),
  CONSTRAINT fk_erp_customer_owner FOREIGN KEY(owner_employee_id) REFERENCES erp_employee(id),
  CONSTRAINT ck_erp_customer_status CHECK(status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_customer_contact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  position_name VARCHAR(120) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(190) NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  notes VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), KEY idx_erp_contact_customer(customer_id),
  CONSTRAINT fk_erp_contact_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_contract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_no VARCHAR(80) NOT NULL,
  title VARCHAR(240) NOT NULL,
  customer_id BIGINT NOT NULL,
  contract_type VARCHAR(60) NULL,
  amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  signed_on DATE NULL,
  starts_on DATE NULL,
  ends_on DATE NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'draft',
  owner_employee_id BIGINT NULL,
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), UNIQUE KEY uk_erp_contract_no(contract_no),
  KEY idx_erp_contract_customer(customer_id), KEY idx_erp_contract_status(status),
  CONSTRAINT fk_erp_contract_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_owner FOREIGN KEY(owner_employee_id) REFERENCES erp_employee(id),
  CONSTRAINT ck_erp_contract_amount CHECK(amount >= 0),
  CONSTRAINT ck_erp_contract_status CHECK(status IN ('draft','pending','active','completed','terminated'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_no VARCHAR(80) NOT NULL,
  name VARCHAR(240) NOT NULL,
  contract_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  description TEXT NULL,
  manager_employee_id BIGINT NULL,
  planned_start DATE NULL,
  planned_end DATE NULL,
  actual_start DATE NULL,
  actual_end DATE NULL,
  progress DECIMAL(5,2) NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'planned',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), UNIQUE KEY uk_erp_project_no(project_no),
  KEY idx_erp_project_customer(customer_id), KEY idx_erp_project_contract(contract_id),
  KEY idx_erp_project_status(status),
  CONSTRAINT fk_erp_project_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_manager FOREIGN KEY(manager_employee_id) REFERENCES erp_employee(id),
  CONSTRAINT ck_erp_project_progress CHECK(progress BETWEEN 0 AND 100),
  CONSTRAINT ck_erp_project_status CHECK(status IN ('planned','in_progress','paused','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_member (
  project_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  member_role VARCHAR(80) NULL,
  joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(project_id,employee_id),
  CONSTRAINT fk_erp_project_member_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_project_member_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_milestone (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NULL,
  due_on DATE NULL,
  completed_at DATETIME(3) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), KEY idx_erp_milestone_project(project_id,sort_order),
  CONSTRAINT fk_erp_milestone_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT ck_erp_milestone_status CHECK(status IN ('pending','completed','skipped'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  actor_identity_id BIGINT NULL,
  log_type VARCHAR(60) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id), KEY idx_erp_project_log(project_id,created_at),
  CONSTRAINT fk_erp_project_log_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_project_log_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('organization:read','查看组织'),('organization:write','维护组织'),
 ('employee:read','查看员工'),('employee:write','维护员工'),
 ('customer:read','查看客户'),('customer:write','维护客户'),
 ('contract:read','查看合同'),('contract:write','维护合同'),
 ('project:read','查看项目'),('project:write','维护项目')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('organization:read','employee:read','customer:read','contract:read','project:read','project:write')
 WHERE r.code='project_manager';
