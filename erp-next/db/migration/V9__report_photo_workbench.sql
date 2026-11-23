-- V9: persistent photo batches and auditable DOCX insertions into report versions.
CREATE TABLE erp_report_photo_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_no VARCHAR(80) NOT NULL,
  project_id BIGINT NOT NULL,
  service_visit_id BIGINT NULL,
  title VARCHAR(240) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'draft',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_report_photo_batch_no (batch_no),
  KEY idx_erp_report_photo_batch_project (project_id,status,created_at),
  CONSTRAINT fk_erp_report_photo_batch_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_batch_visit FOREIGN KEY (service_visit_id) REFERENCES erp_service_visit(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_batch_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_report_photo_batch_status CHECK (status IN ('draft','ready','inserted','archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  system_code VARCHAR(100) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  captured_at DATETIME(3) NULL,
  metadata_json JSON NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_report_photo_batch_file (batch_id,file_id),
  KEY idx_erp_report_photo_order (batch_id,system_code,sort_order,id),
  KEY idx_erp_report_photo_file (file_id),
  CONSTRAINT fk_erp_report_photo_batch FOREIGN KEY (batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_report_photo_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo_insertion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  template_file_id BIGINT NOT NULL,
  output_file_id BIGINT NOT NULL,
  system_counts_json JSON NOT NULL,
  inserted_by BIGINT NOT NULL,
  inserted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_report_photo_insertion_batch_report (batch_id,report_id),
  KEY idx_erp_report_photo_insertion_report (report_id,version_no),
  CONSTRAINT fk_erp_report_photo_insertion_batch FOREIGN KEY (batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_insertion_version FOREIGN KEY (report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_insertion_template FOREIGN KEY (template_file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_insertion_output FOREIGN KEY (output_file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_insertion_actor FOREIGN KEY (inserted_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
