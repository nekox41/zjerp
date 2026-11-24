-- Clause references are manually checked against the exact standard revision.
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
