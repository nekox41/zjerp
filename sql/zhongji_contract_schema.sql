CREATE TABLE zj_intermediary (
id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) NOT NULL,phone VARCHAR(50),company VARCHAR(200),email VARCHAR(200),remark VARCHAR(2000),INDEX idx_intermediary_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_identity(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,contract_type VARCHAR(16) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_maintenance (
id BIGINT NOT NULL PRIMARY KEY,contract_id VARCHAR(255),contract_type VARCHAR(16) NOT NULL DEFAULT 'WB',contract_name VARCHAR(255),start_time DATE,end_time DATE,manager VARCHAR(255),amount DECIMAL(30,16),create_by VARCHAR(255),create_time DATETIME,update_by VARCHAR(255),update_time DATETIME,remark TEXT,contract_file TEXT,ownership VARCHAR(16) NOT NULL DEFAULT 'pending',intermediary_id BIGINT NULL,payment_plan TEXT,legacy_type VARCHAR(16),classification_pending BOOLEAN NOT NULL DEFAULT FALSE,
INDEX idx_contract_number(contract_id),INDEX idx_contract_start(start_time),INDEX idx_contract_owner(ownership),
FOREIGN KEY(id) REFERENCES zj_contract_identity(id) ON DELETE CASCADE,
FOREIGN KEY(intermediary_id) REFERENCES zj_intermediary(id) ON DELETE RESTRICT,
CHECK(contract_type='WB'),CHECK(ownership IN ('pending','company','intermediary')),
CHECK((ownership='intermediary' AND intermediary_id IS NOT NULL) OR (ownership<>'intermediary' AND intermediary_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_inspection (
id BIGINT NOT NULL PRIMARY KEY,contract_id VARCHAR(255),contract_type VARCHAR(16) NOT NULL DEFAULT 'JC',contract_name VARCHAR(255),start_time DATE,end_time DATE,manager VARCHAR(255),amount DECIMAL(30,16),create_by VARCHAR(255),create_time DATETIME,update_by VARCHAR(255),update_time DATETIME,remark TEXT,contract_file TEXT,ownership VARCHAR(16) NOT NULL DEFAULT 'pending',intermediary_id BIGINT NULL,payment_plan TEXT,legacy_type VARCHAR(16),classification_pending BOOLEAN NOT NULL DEFAULT FALSE,
INDEX idx_contract_number(contract_id),INDEX idx_contract_start(start_time),INDEX idx_contract_owner(ownership),
FOREIGN KEY(id) REFERENCES zj_contract_identity(id) ON DELETE CASCADE,
FOREIGN KEY(intermediary_id) REFERENCES zj_intermediary(id) ON DELETE RESTRICT,
CHECK(contract_type='JC'),CHECK(ownership IN ('pending','company','intermediary')),
CHECK((ownership='intermediary' AND intermediary_id IS NOT NULL) OR (ownership<>'intermediary' AND intermediary_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_rating (
id BIGINT NOT NULL PRIMARY KEY,contract_id VARCHAR(255),contract_type VARCHAR(16) NOT NULL DEFAULT 'PD',contract_name VARCHAR(255),start_time DATE,end_time DATE,manager VARCHAR(255),amount DECIMAL(30,16),create_by VARCHAR(255),create_time DATETIME,update_by VARCHAR(255),update_time DATETIME,remark TEXT,contract_file TEXT,ownership VARCHAR(16) NOT NULL DEFAULT 'pending',intermediary_id BIGINT NULL,payment_plan TEXT,legacy_type VARCHAR(16),classification_pending BOOLEAN NOT NULL DEFAULT FALSE,
INDEX idx_contract_number(contract_id),INDEX idx_contract_start(start_time),INDEX idx_contract_owner(ownership),
FOREIGN KEY(id) REFERENCES zj_contract_identity(id) ON DELETE CASCADE,
FOREIGN KEY(intermediary_id) REFERENCES zj_intermediary(id) ON DELETE RESTRICT,
CHECK(contract_type='PD'),CHECK(ownership IN ('pending','company','intermediary')),
CHECK((ownership='intermediary' AND intermediary_id IS NOT NULL) OR (ownership<>'intermediary' AND intermediary_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_assessment (
id BIGINT NOT NULL PRIMARY KEY,contract_id VARCHAR(255),contract_type VARCHAR(16) NOT NULL DEFAULT 'PG',contract_name VARCHAR(255),start_time DATE,end_time DATE,manager VARCHAR(255),amount DECIMAL(30,16),create_by VARCHAR(255),create_time DATETIME,update_by VARCHAR(255),update_time DATETIME,remark TEXT,contract_file TEXT,ownership VARCHAR(16) NOT NULL DEFAULT 'pending',intermediary_id BIGINT NULL,payment_plan TEXT,legacy_type VARCHAR(16),classification_pending BOOLEAN NOT NULL DEFAULT FALSE,
INDEX idx_contract_number(contract_id),INDEX idx_contract_start(start_time),INDEX idx_contract_owner(ownership),
FOREIGN KEY(id) REFERENCES zj_contract_identity(id) ON DELETE CASCADE,
FOREIGN KEY(intermediary_id) REFERENCES zj_intermediary(id) ON DELETE RESTRICT,
CHECK(contract_type='PG'),CHECK(ownership IN ('pending','company','intermediary')),
CHECK((ownership='intermediary' AND intermediary_id IS NOT NULL) OR (ownership<>'intermediary' AND intermediary_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE zj_contract_other (
id BIGINT NOT NULL PRIMARY KEY,contract_id VARCHAR(255),contract_type VARCHAR(16) NOT NULL DEFAULT 'QT',contract_name VARCHAR(255),start_time DATE,end_time DATE,manager VARCHAR(255),amount DECIMAL(30,16),create_by VARCHAR(255),create_time DATETIME,update_by VARCHAR(255),update_time DATETIME,remark TEXT,contract_file TEXT,ownership VARCHAR(16) NOT NULL DEFAULT 'pending',intermediary_id BIGINT NULL,payment_plan TEXT,legacy_type VARCHAR(16),classification_pending BOOLEAN NOT NULL DEFAULT FALSE,
INDEX idx_contract_number(contract_id),INDEX idx_contract_start(start_time),INDEX idx_contract_owner(ownership),
FOREIGN KEY(id) REFERENCES zj_contract_identity(id) ON DELETE CASCADE,
FOREIGN KEY(intermediary_id) REFERENCES zj_intermediary(id) ON DELETE RESTRICT,
CHECK(contract_type='QT'),CHECK(ownership IN ('pending','company','intermediary')),
CHECK((ownership='intermediary' AND intermediary_id IS NOT NULL) OR (ownership<>'intermediary' AND intermediary_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE VIEW zj_contract AS SELECT id,contract_id,contract_type,contract_name,start_time,end_time,manager,amount,create_by,create_time,update_by,update_time,remark,contract_file,ownership,intermediary_id,payment_plan,legacy_type,classification_pending FROM zj_contract_maintenance UNION ALL SELECT id,contract_id,contract_type,contract_name,start_time,end_time,manager,amount,create_by,create_time,update_by,update_time,remark,contract_file,ownership,intermediary_id,payment_plan,legacy_type,classification_pending FROM zj_contract_inspection UNION ALL SELECT id,contract_id,contract_type,contract_name,start_time,end_time,manager,amount,create_by,create_time,update_by,update_time,remark,contract_file,ownership,intermediary_id,payment_plan,legacy_type,classification_pending FROM zj_contract_rating UNION ALL SELECT id,contract_id,contract_type,contract_name,start_time,end_time,manager,amount,create_by,create_time,update_by,update_time,remark,contract_file,ownership,intermediary_id,payment_plan,legacy_type,classification_pending FROM zj_contract_assessment UNION ALL SELECT id,contract_id,contract_type,contract_name,start_time,end_time,manager,amount,create_by,create_time,update_by,update_time,remark,contract_file,ownership,intermediary_id,payment_plan,legacy_type,classification_pending FROM zj_contract_other;
