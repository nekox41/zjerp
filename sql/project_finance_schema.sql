CREATE TABLE IF NOT EXISTS zj_project_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id BIGINT NOT NULL,name VARCHAR(255) NOT NULL,
 kind VARCHAR(32) NOT NULL DEFAULT 'other',files TEXT,legacy_report_id BIGINT,legacy_role VARCHAR(32),legacy_snapshot JSON,
 created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_legacy_report(legacy_report_id,legacy_role),INDEX idx_project_docs(project_id,id),
 FOREIGN KEY(project_id) REFERENCES zj_work_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_project_material (
 project_id BIGINT PRIMARY KEY,material_id BIGINT NULL,card_id INT NULL,version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(project_id) REFERENCES zj_work_project(id) ON DELETE RESTRICT,
 FOREIGN KEY(material_id) REFERENCES zj_wbcard_material(id) ON DELETE RESTRICT,
 FOREIGN KEY(card_id) REFERENCES zj_wbcard(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_reimbursement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,receipt CHAR(36) NOT NULL UNIQUE,client_token CHAR(36) NOT NULL UNIQUE,
 request_hash CHAR(64),amount DECIMAL(18,2) NOT NULL,purpose VARCHAR(2000) NOT NULL,project_id BIGINT NULL,user_id BIGINT NULL,
 claimant_name VARCHAR(100),expense_date DATE,remark VARCHAR(2000),invoices TEXT,
 source VARCHAR(16) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'pending',version BIGINT NOT NULL DEFAULT 0,
 legacy_cost_id INT NULL UNIQUE,legacy_snapshot JSON,created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_reimbursement_date(expense_date,id),INDEX idx_reimbursement_project(project_id),INDEX idx_reimbursement_user(user_id),
 FOREIGN KEY(project_id) REFERENCES zj_work_project(id) ON DELETE RESTRICT,
 FOREIGN KEY(user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_wbcard_sequence(sequence_key INT PRIMARY KEY,sequence_value BIGINT NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS zj_wbcard_number(code VARCHAR(100) PRIMARY KEY,card_id INT NULL,FOREIGN KEY(card_id) REFERENCES zj_wbcard(id) ON DELETE SET NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_project_finance_migration(version VARCHAR(64) PRIMARY KEY,report JSON NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
