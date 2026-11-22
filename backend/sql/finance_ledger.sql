CREATE TABLE IF NOT EXISTS zj_finance_account (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(120) NOT NULL,holder VARCHAR(120),account_number VARCHAR(120),
 opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0,active BOOLEAN NOT NULL DEFAULT TRUE,remark VARCHAR(2000),version BIGINT NOT NULL DEFAULT 0,
 created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by VARCHAR(64),updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_finance_invoice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,client_token CHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,invoice_no VARCHAR(100) NOT NULL,
 buyer_name VARCHAR(200) NOT NULL,buyer_tax_id VARCHAR(100),invoice_type VARCHAR(100),amount DECIMAL(18,2) NOT NULL,tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 invoice_date DATE NOT NULL,contract_id BIGINT NULL,project_id BIGINT NULL,files TEXT,remark VARCHAR(2000),status VARCHAR(16) NOT NULL DEFAULT 'issued',
 active_invoice_no VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN status='issued' THEN invoice_no ELSE NULL END) STORED UNIQUE,
 void_reason VARCHAR(500),void_by VARCHAR(64),void_at DATETIME NULL,version BIGINT NOT NULL DEFAULT 0,created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_invoice_contract(contract_id),INDEX idx_invoice_project(project_id),INDEX idx_invoice_date(invoice_date,id),
 FOREIGN KEY(contract_id) REFERENCES zj_contract_identity(id) ON DELETE RESTRICT,FOREIGN KEY(project_id) REFERENCES zj_work_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_finance_transaction (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,client_token CHAR(36) NOT NULL UNIQUE,request_hash CHAR(64) NOT NULL,account_id BIGINT NOT NULL,
 direction VARCHAR(16) NOT NULL,amount DECIMAL(18,2) NOT NULL,transaction_date DATE NOT NULL,purpose VARCHAR(2000) NOT NULL,counterparty VARCHAR(200),
 contract_id BIGINT NULL,project_id BIGINT NULL,reimbursement_id BIGINT NULL,invoice_id BIGINT NULL,transfer_group CHAR(36),files TEXT,remark VARCHAR(2000),
 status VARCHAR(16) NOT NULL DEFAULT 'posted',void_reason VARCHAR(500),void_by VARCHAR(64),void_at DATETIME NULL,version BIGINT NOT NULL DEFAULT 0,
 created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_tx_account(account_id,status),INDEX idx_tx_contract(contract_id),INDEX idx_tx_project(project_id),INDEX idx_tx_date(transaction_date,id),INDEX idx_tx_transfer(transfer_group),INDEX idx_tx_claim(reimbursement_id),
 FOREIGN KEY(account_id) REFERENCES zj_finance_account(id) ON DELETE RESTRICT,FOREIGN KEY(contract_id) REFERENCES zj_contract_identity(id) ON DELETE RESTRICT,
 FOREIGN KEY(project_id) REFERENCES zj_work_project(id) ON DELETE RESTRICT,FOREIGN KEY(reimbursement_id) REFERENCES zj_reimbursement(id) ON DELETE RESTRICT,
 FOREIGN KEY(invoice_id) REFERENCES zj_finance_invoice(id) ON DELETE RESTRICT,
 CHECK(direction IN ('income','expense','transfer_in','transfer_out')),CHECK(amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
