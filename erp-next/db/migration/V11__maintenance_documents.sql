-- Isolated V11. Original card resources are migrated separately; no legacy database access.
CREATE TABLE erp_maintenance_card_sequence (
 id TINYINT NOT NULL PRIMARY KEY, last_number BIGINT NOT NULL
) ENGINE=InnoDB;
INSERT INTO erp_maintenance_card_sequence VALUES (1,0);

CREATE TABLE erp_maintenance_card (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, file_id BIGINT NOT NULL,
 code VARCHAR(100) NOT NULL, project_name VARCHAR(255) NOT NULL, company VARCHAR(255) NOT NULL,
 leader VARCHAR(100) NOT NULL, phone VARCHAR(255) NOT NULL, systems TEXT NOT NULL,
 starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 printed BOOLEAN NOT NULL DEFAULT FALSE, sent BOOLEAN NOT NULL DEFAULT FALSE,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_maintenance_card_code(code), UNIQUE KEY uk_maintenance_card_file(file_id),
 KEY idx_card_scope(contract_id,project_id),
 FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
 FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_contact (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, phone VARCHAR(50) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_material (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200) NOT NULL,
 file_id BIGINT NOT NULL, notes VARCHAR(2000), active BOOLEAN NOT NULL DEFAULT TRUE,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_maintenance_material_archive (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, contract_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 material_id BIGINT NOT NULL, file_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_maintenance_material_archive(contract_id,project_id,material_id),
 FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
 FOREIGN KEY(material_id) REFERENCES erp_maintenance_material(id) ON DELETE RESTRICT,
 FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
