-- The standard revisions associated with a Word template are suggestions, not applicability findings.
CREATE TABLE erp_inspection_template_standard (
 template_id BIGINT NOT NULL,
 standard_id BIGINT NOT NULL,
 PRIMARY KEY(template_id,standard_id),
 FOREIGN KEY(template_id) REFERENCES erp_inspection_template(id) ON DELETE RESTRICT,
 FOREIGN KEY(standard_id) REFERENCES erp_fire_standard(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
