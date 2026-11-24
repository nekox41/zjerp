-- Read-only historical documents. They are not newly approved/issued service reports.
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
