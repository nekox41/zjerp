-- V6: auditable expense reimbursement, independent finance review and payment registration.
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
 PRIMARY KEY(id), UNIQUE KEY uk_erp_expense_claim_no(claim_no),
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
 PRIMARY KEY(id), KEY idx_erp_expense_line_claim(claim_id,sort_order),
 CONSTRAINT fk_erp_expense_line_claim FOREIGN KEY(claim_id) REFERENCES erp_expense_claim(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_expense_line_amount CHECK(amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_expense_attachment (
 id BIGINT NOT NULL AUTO_INCREMENT,
 claim_id BIGINT NOT NULL,
 file_id BIGINT NOT NULL,
 added_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_expense_attachment(claim_id,file_id),
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
 PRIMARY KEY(id), KEY idx_erp_expense_event_claim(claim_id,id),
 CONSTRAINT fk_erp_expense_event_claim FOREIGN KEY(claim_id) REFERENCES erp_expense_claim(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_expense_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('expense:read','查看费用报账'),
 ('expense:write','创建和维护本人报账'),
 ('expense:approve','财务审批报账'),
 ('expense:pay','登记报账付款')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('expense:read','expense:write')
 WHERE r.code IN ('employee','project_manager');
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('expense:read','expense:write','expense:approve','expense:pay')
 WHERE r.code='finance';
