-- New ERP only. Run through Flyway against an EMPTY, isolated erp_next database.
-- Do not run against the legacy 'server' database.
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
  CONSTRAINT ck_erp_identity_status CHECK (status IN ('active', 'disabled', 'locked'))
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
  CONSTRAINT fk_erp_identity_role_role FOREIGN KEY (role_id) REFERENCES erp_role(id)
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
  CONSTRAINT ck_erp_employee_status CHECK (status IN ('active', 'inactive'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_audit_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_identity_id BIGINT NULL,
  action VARCHAR(100) NOT NULL,
  resource_type VARCHAR(100) NOT NULL,
  resource_id VARCHAR(120) NULL,
  request_id VARCHAR(100) NULL,
  summary_json JSON NULL,
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_audit_resource (resource_type, resource_id, occurred_at),
  KEY idx_erp_audit_actor (actor_identity_id, occurred_at),
  CONSTRAINT fk_erp_audit_actor FOREIGN KEY (actor_identity_id) REFERENCES erp_identity(id) ON DELETE SET NULL
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
