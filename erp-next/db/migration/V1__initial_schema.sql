-- ERP V1 initial schema. Only for a new erp_next database.
-- Source: pre-release V1–V24 final model; no development-version data upgrades.
-- Legacy server data is imported separately after explicit confirmation.
-- The sole deferred foreign key resolves the report version/artifact circular reference.

CREATE TABLE erp_organization (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  parent_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_org_parent (parent_id),
  CONSTRAINT fk_erp_org_parent FOREIGN KEY (parent_id) REFERENCES erp_organization(id),
  CONSTRAINT ck_erp_org_status CHECK (status IN ('active', 'disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_identity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  organization_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_identity_username (username),
  KEY idx_erp_identity_org (organization_id),
  CONSTRAINT fk_erp_identity_org FOREIGN KEY (organization_id) REFERENCES erp_organization(id),
  CONSTRAINT ck_erp_identity_status CHECK (status IN ('active', 'disabled', 'locked')),
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(150) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_identity_role (
  identity_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (identity_id, role_id),
  CONSTRAINT fk_erp_identity_role_identity FOREIGN KEY (identity_id) REFERENCES erp_identity(id),
  CONSTRAINT fk_erp_identity_role_role FOREIGN KEY (role_id) REFERENCES erp_role(id),
  UNIQUE KEY uk_one_fixed_role(identity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_employee (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_code VARCHAR(100) NULL,
  full_name VARCHAR(200) NOT NULL,
  organization_id BIGINT NULL,
  identity_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_employee_code (employee_code),
  UNIQUE KEY uk_erp_employee_identity (identity_id),
  KEY idx_erp_employee_org (organization_id),
  CONSTRAINT fk_erp_employee_org FOREIGN KEY (organization_id) REFERENCES erp_organization(id),
  CONSTRAINT fk_erp_employee_identity FOREIGN KEY (identity_id) REFERENCES erp_identity(id),
  CONSTRAINT ck_erp_employee_status CHECK (status IN ('active', 'inactive')),
  phone VARCHAR(40) NULL,
  email VARCHAR(190) NULL,
  position_name VARCHAR(120) NULL,
  joined_on DATE NULL,
  left_on DATE NULL,
  employee_type VARCHAR(24) NOT NULL DEFAULT 'general',
  CONSTRAINT ck_erp_employee_type CHECK(employee_type IN ('general','operator','engineer','manager'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_file_object (
  id BIGINT NOT NULL AUTO_INCREMENT,
  storage_key VARCHAR(500) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  byte_size BIGINT NOT NULL,
  media_type VARCHAR(255) NULL,
  original_name VARCHAR(255) NOT NULL,
  uploaded_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_file_storage_key (storage_key),
  KEY idx_erp_file_sha256 (sha256),
  CONSTRAINT fk_erp_file_uploader FOREIGN KEY (uploaded_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_file_size CHECK (byte_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_file_reference (
  id BIGINT NOT NULL AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  owner_type VARCHAR(100) NOT NULL,
  owner_id BIGINT NOT NULL,
  purpose VARCHAR(100) NOT NULL DEFAULT 'attachment',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_file_ref (file_id, owner_type, owner_id, purpose),
  KEY idx_erp_file_owner (owner_type, owner_id),
  CONSTRAINT fk_erp_file_ref_object FOREIGN KEY (file_id) REFERENCES erp_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_customer_code(customer_code),
  UNIQUE KEY uk_erp_customer_credit_code(credit_code),
  KEY idx_erp_customer_name(name),
  KEY idx_erp_customer_status(status),
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
  PRIMARY KEY(id),
  KEY idx_erp_contact_customer(customer_id),
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
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_contract_no(contract_no),
  KEY idx_erp_contract_customer(customer_id),
  KEY idx_erp_contract_status(status),
  CONSTRAINT fk_erp_contract_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_owner FOREIGN KEY(owner_employee_id) REFERENCES erp_employee(id),
  CONSTRAINT ck_erp_contract_amount CHECK(amount >= 0),
  CONSTRAINT ck_erp_contract_status CHECK(status IN ('draft','pending','active','completed','terminated')),
  service_type VARCHAR(40) NOT NULL DEFAULT 'other',
  signing_source VARCHAR(24) NOT NULL DEFAULT 'direct',
  partner_share_percent DECIMAL(5,2) NULL,
  current_flag BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT ck_erp_contract_service_type CHECK(service_type IN ('maintenance','fire_inspection','fire_assessment','fire_evaluation','other')),
  CONSTRAINT ck_erp_contract_signing_source CHECK(signing_source IN ('direct','partner')),
  CONSTRAINT ck_erp_contract_partner_share CHECK(partner_share_percent IS NULL OR (partner_share_percent>=0 AND partner_share_percent<=100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_no VARCHAR(80) NOT NULL,
  name VARCHAR(240) NOT NULL,
  contract_id BIGINT NULL,
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
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_project_no(project_no),
  KEY idx_erp_project_customer(customer_id),
  KEY idx_erp_project_contract(contract_id),
  KEY idx_erp_project_status(status),
  CONSTRAINT fk_erp_project_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_manager FOREIGN KEY(manager_employee_id) REFERENCES erp_employee(id),
  CONSTRAINT ck_erp_project_progress CHECK(progress BETWEEN 0 AND 100),
  CONSTRAINT ck_erp_project_status CHECK(status IN ('planned','in_progress','paused','completed','cancelled')),
  technical_lead_employee_id BIGINT NULL,
  CONSTRAINT fk_erp_project_technical_lead FOREIGN KEY(technical_lead_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL
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
  PRIMARY KEY(id),
  KEY idx_erp_milestone_project(project_id,sort_order),
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
  PRIMARY KEY(id),
  KEY idx_erp_project_log(project_id,created_at),
  CONSTRAINT fk_erp_project_log_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_project_log_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_contract_amendment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  amendment_no VARCHAR(80) NOT NULL,
  title VARCHAR(240) NOT NULL,
  change_summary TEXT NOT NULL,
  previous_amount DECIMAL(18,2) NOT NULL,
  revised_amount DECIMAL(18,2) NOT NULL,
  effective_on DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_amendment_no(contract_id,amendment_no),
  KEY idx_erp_amendment_contract(contract_id),
  CONSTRAINT fk_erp_amendment_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_amendment_actor FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_amendment_amount CHECK(previous_amount>=0 AND revised_amount>=0),
  CONSTRAINT ck_erp_amendment_status CHECK(status IN ('draft','approved','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_employee_qualification (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  qualification_name VARCHAR(200) NOT NULL,
  certificate_no VARCHAR(120) NULL,
  issuing_authority VARCHAR(200) NULL,
  issued_on DATE NULL,
  expires_on DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'valid',
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_qualification_employee(employee_id),
  KEY idx_erp_qualification_expiry(expires_on),
  CONSTRAINT fk_erp_qualification_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_qualification_status CHECK(status IN ('valid','expired','revoked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_project_issue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NULL,
  severity VARCHAR(20) NOT NULL DEFAULT 'medium',
  owner_employee_id BIGINT NULL,
  due_on DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'open',
  resolution TEXT NULL,
  closed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_project_issue(project_id,status),
  KEY idx_erp_issue_due(due_on),
  CONSTRAINT fk_erp_issue_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_issue_owner FOREIGN KEY(owner_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_issue_severity CHECK(severity IN ('low','medium','high','critical')),
  CONSTRAINT ck_erp_issue_status CHECK(status IN ('open','in_progress','resolved','closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_work_package (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  package_no VARCHAR(80) NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NULL,
  assignee_employee_id BIGINT NULL,
  planned_start DATE NULL,
  planned_end DATE NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'planned',
  progress DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_work_package_no(project_id,package_no),
  KEY idx_erp_work_package_project(project_id,status),
  CONSTRAINT fk_erp_work_package_project FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_work_package_assignee FOREIGN KEY(assignee_employee_id) REFERENCES erp_employee(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_work_package_progress CHECK(progress BETWEEN 0 AND 100),
  CONSTRAINT ck_erp_work_package_status CHECK(status IN ('planned','in_progress','blocked','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  work_package_id BIGINT NOT NULL,
  record_type VARCHAR(40) NOT NULL,
  title VARCHAR(240) NOT NULL,
  content TEXT NOT NULL,
  performed_on DATETIME(3) NOT NULL,
  recorded_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_service_record_package(work_package_id,performed_on),
  CONSTRAINT fk_erp_service_record_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_service_record_actor FOREIGN KEY(recorded_by) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_no VARCHAR(100) NOT NULL,
  work_package_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  report_type VARCHAR(60) NOT NULL DEFAULT 'service',
  status VARCHAR(24) NOT NULL DEFAULT 'draft',
  revision_no INT NOT NULL DEFAULT 1,
  current_version_no INT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL,
  reviewer_id BIGINT NULL,
  issuer_id BIGINT NULL,
  return_reason TEXT NULL,
  archive_manifest LONGTEXT NULL,
  issued_at DATETIME(3) NULL,
  archived_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_report_no(report_no),
  KEY idx_erp_report_work_package(work_package_id,status),
  KEY idx_erp_report_status(status,updated_at),
  CONSTRAINT fk_erp_report_work_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_reviewer FOREIGN KEY(reviewer_id) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT fk_erp_report_issuer FOREIGN KEY(issuer_id) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_report_status CHECK(status IN ('draft','submitted','reviewing','returned','approved','issued','archived','withdrawn')),
  CONSTRAINT ck_erp_report_revision CHECK(revision_no>0 AND current_version_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_version (
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  content_json LONGTEXT NOT NULL,
  content_sha256 CHAR(64) NOT NULL,
  archive_manifest LONGTEXT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(report_id,version_no),
  CONSTRAINT fk_erp_report_version_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_version_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  title_snapshot VARCHAR(240) NULL,
  submitted_artifact_id BIGINT NULL,
  submitted_by BIGINT NULL,
  reviewed_by BIGINT NULL,
  issued_by BIGINT NULL,
  version_issued_at DATETIME(3) NULL,
  CONSTRAINT fk_report_version_submitter FOREIGN KEY(submitted_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT fk_report_version_reviewer FOREIGN KEY(reviewed_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT fk_report_version_issuer FOREIGN KEY(issued_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  file_id BIGINT NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_report_attachment(report_id,version_no,file_id),
  KEY idx_erp_report_attachment_file(file_id),
  CONSTRAINT fk_erp_report_attachment_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_attachment_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_attachment_actor FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  actor_identity_id BIGINT NOT NULL,
  reason TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_report_event(report_id,created_at),
  CONSTRAINT fk_erp_report_event_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_expense_claim (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_no VARCHAR(80) NOT NULL,
  title VARCHAR(240) NOT NULL,
  applicant_identity_id BIGINT NOT NULL,
  employee_id BIGINT NULL,
  project_id BIGINT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(24) NOT NULL DEFAULT 'draft',
  version_no INT NOT NULL DEFAULT 1,
  submitted_at DATETIME(3) NULL,
  reviewer_identity_id BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  return_reason VARCHAR(1000) NULL,
  payment_reference VARCHAR(120) NULL,
  paid_amount DECIMAL(18,2) NULL,
  paid_at DATETIME(3) NULL,
  payer_identity_id BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_expense_claim_no(claim_no),
  UNIQUE KEY uk_erp_expense_payment_ref(payment_reference),
  KEY idx_erp_expense_applicant(applicant_identity_id,status),
  KEY idx_erp_expense_status(status,created_at),
  KEY idx_erp_expense_project(project_id),
  CONSTRAINT fk_erp_expense_applicant FOREIGN KEY(applicant_identity_id) REFERENCES erp_identity(id),
  CONSTRAINT fk_erp_expense_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id),
  CONSTRAINT fk_erp_expense_project FOREIGN KEY(project_id) REFERENCES erp_project(id),
  CONSTRAINT fk_erp_expense_reviewer FOREIGN KEY(reviewer_identity_id) REFERENCES erp_identity(id),
  CONSTRAINT fk_erp_expense_payer FOREIGN KEY(payer_identity_id) REFERENCES erp_identity(id),
  CONSTRAINT ck_erp_expense_total CHECK(total_amount>=0),
  CONSTRAINT ck_erp_expense_paid CHECK(paid_amount IS NULL OR paid_amount>=0),
  CONSTRAINT ck_erp_expense_status CHECK(status IN ('draft','submitted','approved','returned','rejected','paid')),
  CONSTRAINT ck_erp_expense_version CHECK(version_no>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_expense_line (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_id BIGINT NOT NULL,
  description VARCHAR(500) NOT NULL,
  category VARCHAR(60) NOT NULL,
  incurred_on DATE NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY(id),
  KEY idx_erp_expense_line_claim(claim_id,sort_order),
  CONSTRAINT fk_erp_expense_line_claim FOREIGN KEY(claim_id) REFERENCES erp_expense_claim(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_expense_line_amount CHECK(amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_expense_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  added_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_expense_attachment(claim_id,file_id),
  CONSTRAINT fk_erp_expense_attachment_claim FOREIGN KEY(claim_id) REFERENCES erp_expense_claim(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_expense_attachment_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_expense_attachment_actor FOREIGN KEY(added_by) REFERENCES erp_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_expense_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_id BIGINT NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  actor_identity_id BIGINT NOT NULL,
  reason VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_expense_event_claim(claim_id,id),
  CONSTRAINT fk_erp_expense_event_claim FOREIGN KEY(claim_id) REFERENCES erp_expense_claim(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_expense_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_invoice (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_no VARCHAR(100) NOT NULL,
  direction VARCHAR(12) NOT NULL,
  counterparty_name VARCHAR(240) NOT NULL,
  customer_id BIGINT NULL,
  contract_id BIGINT NULL,
  project_id BIGINT NULL,
  title VARCHAR(240) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  amount DECIMAL(18,2) NOT NULL,
  settled_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  issued_on DATE NOT NULL,
  due_on DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  notes VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  approved_by BIGINT NULL,
  approved_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_invoice_direction_number(direction,invoice_no),
  KEY idx_erp_invoice_customer(customer_id),
  KEY idx_erp_invoice_contract(contract_id),
  KEY idx_erp_invoice_project(project_id),
  KEY idx_erp_invoice_status(direction,status,due_on),
  CONSTRAINT fk_erp_invoice_customer FOREIGN KEY(customer_id) REFERENCES erp_customer(id),
  CONSTRAINT fk_erp_invoice_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id),
  CONSTRAINT fk_erp_invoice_project FOREIGN KEY(project_id) REFERENCES erp_project(id),
  CONSTRAINT fk_erp_invoice_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id),
  CONSTRAINT fk_erp_invoice_approver FOREIGN KEY(approved_by) REFERENCES erp_identity(id),
  CONSTRAINT ck_erp_invoice_direction CHECK(direction IN ('receivable','payable')),
  CONSTRAINT ck_erp_invoice_amount CHECK(amount>0 AND settled_amount>=0 AND settled_amount<=amount),
  CONSTRAINT ck_erp_invoice_status CHECK(status IN ('draft','open','partial','settled','void'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_invoice_settlement (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_id BIGINT NOT NULL,
  payment_reference VARCHAR(120) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  settled_on DATE NOT NULL,
  notes VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_invoice_settlement_ref(payment_reference),
  KEY idx_erp_invoice_settlement_invoice(invoice_id,id),
  CONSTRAINT fk_erp_invoice_settlement_invoice FOREIGN KEY(invoice_id) REFERENCES erp_invoice(id),
  CONSTRAINT fk_erp_invoice_settlement_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id),
  CONSTRAINT ck_erp_invoice_settlement_amount CHECK(amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_invoice_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  actor_identity_id BIGINT NOT NULL,
  details VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_invoice_event(invoice_id,id),
  CONSTRAINT fk_erp_invoice_event_invoice FOREIGN KEY(invoice_id) REFERENCES erp_invoice(id),
  CONSTRAINT fk_erp_invoice_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  work_package_id BIGINT NOT NULL,
  visit_no INT NOT NULL,
  visit_date DATE NOT NULL,
  visit_type VARCHAR(30) NOT NULL DEFAULT 'inspection',
  title VARCHAR(240) NOT NULL,
  summary TEXT NULL,
  result_summary TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'completed',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_service_visit_no(work_package_id,visit_no),
  KEY idx_erp_service_visit_date(work_package_id,visit_date),
  CONSTRAINT fk_erp_visit_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_visit_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_visit_type CHECK(visit_type IN ('inspection','rectification_review','supplement','maintenance','other')),
  CONSTRAINT ck_erp_visit_status CHECK(status IN ('planned','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit_staff (
  visit_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  staff_role VARCHAR(40) NOT NULL DEFAULT 'field_operator',
  PRIMARY KEY(visit_id,employee_id),
  CONSTRAINT fk_erp_visit_staff_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_visit_staff_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_visit_staff_role CHECK(staff_role IN ('field_operator','engineer','team_lead','observer'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit_issue (
  visit_id BIGINT NOT NULL,
  issue_id BIGINT NOT NULL,
  relation_type VARCHAR(24) NOT NULL DEFAULT 'found',
  notes VARCHAR(1000) NULL,
  PRIMARY KEY(visit_id,issue_id),
  CONSTRAINT fk_erp_visit_issue_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_visit_issue_issue FOREIGN KEY(issue_id) REFERENCES erp_project_issue(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_visit_issue_relation CHECK(relation_type IN ('found','reviewed','resolved'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  work_package_id BIGINT NOT NULL,
  visit_id BIGINT NULL,
  report_id BIGINT NULL,
  title VARCHAR(240) NOT NULL,
  visit_date DATE NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'collecting',
  created_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  locked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_photo_batch_package(work_package_id,visit_date),
  KEY idx_erp_photo_batch_report(report_id,status),
  CONSTRAINT fk_erp_photo_batch_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE SET NULL,
  CONSTRAINT fk_erp_photo_batch_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_reviewer FOREIGN KEY(reviewed_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_photo_batch_status CHECK(status IN ('collecting','submitted','reviewed','locked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  system_code VARCHAR(80) NULL,
  taken_on DATE NULL,
  caption VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  confirmation_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  confirmed_by BIGINT NULL,
  confirmed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_report_photo_file(batch_id,file_id),
  KEY idx_erp_report_photo_system(batch_id,system_code,sort_order),
  CONSTRAINT fk_erp_report_photo_batch FOREIGN KEY(batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_confirmer FOREIGN KEY(confirmed_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_erp_report_photo_confirmation CHECK(confirmation_status IN ('pending','confirmed','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_artifact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  file_id BIGINT NOT NULL,
  artifact_type VARCHAR(30) NOT NULL DEFAULT 'docx',
  artifact_revision_no INT NOT NULL DEFAULT 1,
  sha256 CHAR(64) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_report_artifact_version(report_id,version_no,artifact_type,artifact_revision_no),
  CONSTRAINT fk_erp_report_artifact_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_artifact_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_artifact_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_report_artifact_type CHECK(artifact_type IN ('docx','pdf','source_docx')),
  generation_manifest JSON NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_delivery (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  delivery_method VARCHAR(24) NOT NULL DEFAULT 'courier',
  recipient_name VARCHAR(160) NOT NULL,
  recipient_phone VARCHAR(40) NULL,
  recipient_organization VARCHAR(240) NULL,
  recipient_address VARCHAR(800) NOT NULL,
  courier_name VARCHAR(120) NULL,
  tracking_no VARCHAR(120) NULL,
  dispatched_at DATETIME(3) NULL,
  delivered_at DATETIME(3) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'prepared',
  notes TEXT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_delivery_report(report_id,version_no),
  KEY idx_erp_delivery_tracking(tracking_no),
  CONSTRAINT fk_erp_delivery_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_delivery_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_delivery_method CHECK(delivery_method IN ('courier','hand_delivery','electronic')),
  CONSTRAINT ck_erp_delivery_status CHECK(status IN ('prepared','dispatched','delivered','returned','cancelled')),
  artifact_id BIGINT NULL,
  CONSTRAINT fk_delivery_issued_artifact FOREIGN KEY(artifact_id) REFERENCES erp_service_report_artifact(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_delivery_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  delivery_id BIGINT NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  actor_identity_id BIGINT NOT NULL,
  notes TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY idx_erp_delivery_event(delivery_id,created_at),
  CONSTRAINT fk_erp_delivery_event_delivery FOREIGN KEY(delivery_id) REFERENCES erp_service_delivery(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_delivery_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_partner_code(partner_code),
  KEY idx_erp_partner_name(name),
  CONSTRAINT ck_erp_partner_status CHECK(status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_contract_partner (
  contract_id BIGINT NOT NULL,
  partner_id BIGINT NOT NULL,
  share_percent DECIMAL(5,2) NOT NULL,
  notes VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(contract_id,partner_id),
  CONSTRAINT fk_erp_contract_partner_contract FOREIGN KEY(contract_id) REFERENCES erp_contract(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_contract_partner_partner FOREIGN KEY(partner_id) REFERENCES erp_partner(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_contract_partner_link_share CHECK(share_percent>=0 AND share_percent<=100)
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

CREATE TABLE erp_contract_file (
  id BIGINT NOT NULL AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  file_type VARCHAR(32) NOT NULL DEFAULT 'contract',
  title VARCHAR(240) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_erp_contract_file(contract_id,file_id),
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
  PRIMARY KEY(id),
  KEY idx_erp_maintenance_visit_project(project_id,visit_date),
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

CREATE TABLE erp_maintenance_card_sequence (
  id TINYINT NOT NULL PRIMARY KEY,
  last_number BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_card (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  code VARCHAR(100) NOT NULL,
  project_name VARCHAR(255) NOT NULL,
  company VARCHAR(255) NOT NULL,
  leader VARCHAR(100) NOT NULL,
  phone VARCHAR(255) NOT NULL,
  systems TEXT NOT NULL,
  starts_on DATE NOT NULL,
  ends_on DATE NOT NULL,
  printed BOOLEAN NOT NULL DEFAULT FALSE,
  sent BOOLEAN NOT NULL DEFAULT FALSE,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_maintenance_card_code(code),
  UNIQUE KEY uk_maintenance_card_file(file_id),
  KEY idx_card_scope(contract_id,project_id),
  FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
  FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_contact (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_material (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  file_id BIGINT NOT NULL,
  notes VARCHAR(2000),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_material_archive (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_maintenance_material_archive(contract_id,project_id,material_id),
  FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
  FOREIGN KEY(material_id) REFERENCES erp_maintenance_material(id) ON DELETE RESTRICT,
  FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_contract_state (
  contract_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  honeybee_contract_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
  honeybee_project_created BOOLEAN NOT NULL DEFAULT FALSE,
  project_materials_completed BOOLEAN NOT NULL DEFAULT FALSE,
  public_card_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
  notes TEXT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(contract_id,project_id),
  FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
  FOREIGN KEY(updated_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
  CONSTRAINT ck_maintenance_annual_card_status CHECK(public_card_status IN ('not_started','preparing','sent','delivered'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_inspection_profile_version (
  work_package_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  content_json JSON NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(work_package_id,version_no),
  FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_legacy_migration_map (
  source_namespace VARCHAR(64) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(160) NOT NULL,
  source_sha256 CHAR(64) NOT NULL,
  decision_sha256 CHAR(64) NOT NULL,
  target_table VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  archive_sha256 CHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(source_namespace,source_table,source_key),
  KEY idx_legacy_target(target_table,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_legacy_migration_run (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_namespace VARCHAR(64) NOT NULL,
  archive_sha256 CHAR(64) NOT NULL,
  decision_sha256 CHAR(64) NOT NULL,
  summary_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_legacy_run(source_namespace,archive_sha256,decision_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_card_legacy_number (
  code VARCHAR(100) NOT NULL PRIMARY KEY,
  source_namespace VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_legacy_project_document (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  document_kind VARCHAR(32) NOT NULL,
  report_no VARCHAR(100) NULL,
  source_namespace VARCHAR(64) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(160) NOT NULL,
  original_created_at DATETIME(3) NULL,
  original_status VARCHAR(200) NULL,
  archive_note VARCHAR(2000) NULL,
  archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_legacy_project_document(source_namespace,source_table,source_key),
  KEY idx_legacy_document_project(project_id,id),
  FOREIGN KEY(project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CHECK(document_kind IN ('historical_report','project_document','other'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_legacy_project_document_file (
  document_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  PRIMARY KEY(document_id,file_id),
  KEY idx_legacy_document_file(file_id),
  FOREIGN KEY(document_id) REFERENCES erp_legacy_project_document(id) ON DELETE RESTRICT,
  FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

CREATE TABLE erp_fire_standard (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  standard_no VARCHAR(80) NOT NULL,
  edition VARCHAR(80) NOT NULL DEFAULT '',
  title VARCHAR(240) NOT NULL,
  category VARCHAR(100) NOT NULL,
  publication_year SMALLINT NULL,
  effective_on DATE NULL,
  withdrawn_on DATE NULL,
  validity VARCHAR(24) NOT NULL DEFAULT 'unknown',
  aliases VARCHAR(240) NOT NULL DEFAULT '',
  source_url VARCHAR(1000) NOT NULL,
  access_url VARCHAR(1000) NULL,
  notes VARCHAR(2000) NOT NULL DEFAULT '',
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  revision_no INT NOT NULL DEFAULT 1,
  file_id BIGINT NULL,
  verified_by BIGINT NULL,
  verified_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_fire_standard_revision(standard_no,edition,revision_no),
  KEY idx_fire_standard_category(category,active,verified),
  FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  FOREIGN KEY(verified_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CHECK(validity IN ('unknown','current','partial','historical','withdrawn'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_inspection_template_standard (
  template_id BIGINT NOT NULL,
  standard_id BIGINT NOT NULL,
  PRIMARY KEY(template_id,standard_id),
  FOREIGN KEY(template_id) REFERENCES erp_inspection_template(id) ON DELETE RESTRICT,
  FOREIGN KEY(standard_id) REFERENCES erp_fire_standard(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_fire_standard_clause (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  standard_id BIGINT NOT NULL,
  clause_no VARCHAR(80) NOT NULL,
  requirement_summary VARCHAR(4000) NOT NULL,
  source_locator VARCHAR(500) NOT NULL,
  revision_no INT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_fire_clause_revision(standard_id,clause_no,revision_no),
  FOREIGN KEY(standard_id) REFERENCES erp_fire_standard(id) ON DELETE RESTRICT,
  FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_inspection_template_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL,
  sort_order INT NOT NULL,
  item_name VARCHAR(240) NOT NULL,
  clause_id BIGINT NOT NULL,
  UNIQUE KEY uk_inspection_item_order(template_id,sort_order),
  FOREIGN KEY(template_id) REFERENCES erp_inspection_template(id) ON DELETE RESTRICT,
  FOREIGN KEY(clause_id) REFERENCES erp_fire_standard_clause(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_photo_review_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  actor_identity_id BIGINT NULL,
  action VARCHAR(100) NOT NULL,
  summary_json JSON NULL,
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_photo_review_batch(batch_id,id),
  FOREIGN KEY(batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE CASCADE,
  FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE erp_service_report_version ADD CONSTRAINT fk_report_version_selected_artifact FOREIGN KEY(submitted_artifact_id) REFERENCES erp_service_report_artifact(id) ON DELETE RESTRICT;

INSERT INTO erp_role(code,name) VALUES ('admin','超级账户'),('manager','管理人员'),('employee','普通员工');

INSERT INTO erp_permission(code,name) VALUES
  ('workspace:read','访问工作台'),
  ('identity:read','查看账号与角色'),
  ('identity:write','创建与维护账号'),
  ('project:read','查看项目'),
  ('project:write','维护项目'),
  ('contract:read','查看合同'),
  ('contract:write','维护合同'),
  ('finance:read','查看财务'),
  ('finance:write','维护财务'),
  ('organization:read','查看组织'),
  ('organization:write','维护组织'),
  ('employee:read','查看员工'),
  ('employee:write','维护员工'),
  ('customer:read','查看客户'),
  ('customer:write','维护客户'),
  ('service:read','查看服务工作包'),
  ('service:write','维护服务工作包'),
  ('file:upload','上传业务附件'),
  ('file:read','下载业务附件'),
  ('report:read','查看技术服务报告'),
  ('report:write','编制技术服务报告'),
  ('report:review','审核技术服务报告'),
  ('report:issue','签发技术服务报告'),
  ('expense:read','查看费用报账'),
  ('expense:write','创建和维护本人报账'),
  ('expense:approve','财务审批报账'),
  ('expense:pay','登记报账付款'),
  ('invoice:read','查看应收应付台账'),
  ('invoice:write','登记和编辑发票'),
  ('invoice:approve','审核发票'),
  ('invoice:settle','登记收付款核销'),
  ('service:visit:read','查看现场服务记录'),
  ('service:visit:write','登记现场服务记录'),
  ('report:photo:read','查看报告照片批次'),
  ('report:photo:write','整理报告照片'),
  ('delivery:read','查看报告交付记录'),
  ('delivery:write','登记报告交付'),
  ('partner:read','查看合作方'),
  ('partner:write','维护合作方'),
  ('maintenance:read','查看维保项目'),
  ('maintenance:write','维护维保项目'),
  ('repair:read','查看维修记录'),
  ('repair:write','登记与维护维修记录'),
  ('report:template:write','管理检测报告模板库'),
  ('report:standard:write','维护消防规范资料库');

INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';

INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='manager' AND p.code NOT LIKE 'identity:%' AND p.code NOT LIKE 'organization:write';

INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('workspace:read','project:read','service:read','service:visit:read','service:visit:write','report:read','report:write','report:photo:read','report:photo:write','file:read','file:upload','expense:read','expense:write','maintenance:read','maintenance:write','employee:read') WHERE r.code='employee';

INSERT INTO erp_maintenance_card_sequence VALUES (1,0);

INSERT INTO erp_fire_standard(standard_no,edition,title,category,publication_year,effective_on,source_url,aliases,notes) VALUES
('XF 503-2004','','建筑消防设施检测技术规程','检测通用',2004,NULL,'https://b.1190119.com/article-423.htm','GA 503-2004','行业标准重新编号公告；状态待核'),
('GB 50016-2014','2018年版','建筑设计防火规范','建筑防火',2014,NULL,'https://www.1190119.com/alink/?url=https%3A%2F%2Fgf.cabr-fire.com%2Flist-991.htm','','注意部分强制条文废止；核对版本及条文'),
('GB 55036-2022','','消防设施通用规范','检测通用',2022,'2023-03-01','https://b.1190119.com/article-12737.htm','','核对公告及适用范围'),
('GB 55037-2022','','建筑防火通用规范','建筑防火',2022,'2023-06-01','https://b.1190119.com/article-12737.htm','','核对公告及适用范围'),
('GB 51309-2018','','消防应急照明和疏散指示系统技术标准','应急照明',2018,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50222-2017','','建筑内部装修设计防火规范','建筑防火',2017,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50116-2013','','火灾自动报警系统设计规范','火灾报警',2013,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50166-2019','','火灾自动报警系统施工及验收标准','火灾报警',2019,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 29364-2012','','防火门监控器','防火分隔',2012,NULL,'https://b.1190119.com/article-11709.htm','','问答索引；待复核产品标准原文'),
('GB 50974-2014','','消防给水及消火栓系统技术规范','消防给水',2014,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50877-2014','','防火卷帘、防火门、防火窗施工及验收规范','防火分隔',2014,NULL,'https://b.1190119.com/article-7757.htm','','问答索引；待复核原文'),
('GB 51348-2019','','民用建筑电气设计标准','消防供配电',2019,'2020-08-01','https://b.1190119.com/dayi/m/article-12303.htm','','答疑索引；待复核原文'),
('GB 50052-2009','','供配电系统设计规范','消防供配电',2009,NULL,'https://www.1190119.com/','','用户提供目录候选，站内原文待定位，不可直接选用'),
('GB 25506-2010','','消防控制室通用技术要求','火灾报警',2010,NULL,'https://b.1190119.com/article-12422.htm','','问答索引；待复核原文'),
('GB 50140-2005','','建筑灭火器配置设计规范','灭火器',2005,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50444-2008','','建筑灭火器配置验收及检查规范','灭火器',2008,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50016-2006','','建筑设计防火规范','建筑防火',2006,NULL,'https://b.1190119.com/article-12254.htm','','历史版本线索，不能自动判定适用'),
('GB 50045-95','2005年版','高层民用建筑设计防火规范','建筑防火',1995,NULL,'https://b.1190119.com/article-12254.htm','','历史版本线索，不能自动判定适用'),
('GB 50084-2017','','自动喷水灭火系统设计规范','自动喷水',2017,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50261-2017','','自动喷水灭火系统施工及验收规范','自动喷水',2017,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 51251-2017','','建筑防烟排烟系统技术标准','防烟排烟',2017,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50370-2005','','气体灭火系统设计规范','气体灭火',2005,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50263-2007','','气体灭火系统施工及验收规范','气体灭火',2007,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 50151-2021','','泡沫灭火系统技术标准','泡沫灭火',2021,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文'),
('GB 55022-2021','','既有建筑维护与改造通用规范','建筑防火',2021,NULL,'https://gf.1190119.com/dropdown/','','目录索引；待复核原文');
