-- V7: receivables/payables invoice register and partial settlements.
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
 PRIMARY KEY(id), KEY idx_erp_invoice_event(invoice_id,id),
 CONSTRAINT fk_erp_invoice_event_invoice FOREIGN KEY(invoice_id) REFERENCES erp_invoice(id),
 CONSTRAINT fk_erp_invoice_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('invoice:read','查看应收应付台账'),
 ('invoice:write','登记和编辑发票'),
 ('invoice:approve','审核发票'),
 ('invoice:settle','登记收付款核销')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('invoice:read','invoice:write','invoice:approve','invoice:settle')
 WHERE r.code='finance';
