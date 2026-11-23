-- V9: repeatable site visits, rectification history, project reports, mailing and persistent photo workbench.
CREATE TABLE erp_site_visit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  visit_no VARCHAR(80) NULL,
  visit_type VARCHAR(24) NOT NULL DEFAULT 'inspection',
  title VARCHAR(240) NOT NULL,
  performed_on DATETIME(3) NOT NULL,
  content TEXT NULL,
  summary TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'completed',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_site_visit_no (project_id,visit_no),
  KEY idx_erp_site_visit_project_date (project_id,performed_on),
  KEY idx_erp_site_visit_status (status,performed_on),
  CONSTRAINT fk_erp_site_visit_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_site_visit_creator FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_site_visit_type CHECK (visit_type IN ('maintenance','inspection','rating','assessment','other')),
  CONSTRAINT ck_erp_site_visit_status CHECK (status IN ('planned','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_site_visit_employee (
  site_visit_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  visit_role VARCHAR(80) NULL,
  PRIMARY KEY (site_visit_id,employee_id),
  KEY idx_erp_site_visit_employee (employee_id,site_visit_id),
  CONSTRAINT fk_erp_site_visit_employee_visit FOREIGN KEY (site_visit_id) REFERENCES erp_site_visit(id) ON DELETE CASCADE,
  CONSTRAINT fk_erp_site_visit_employee_employee FOREIGN KEY (employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_site_visit_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  site_visit_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  label VARCHAR(240) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_site_visit_attachment (site_visit_id,file_id),
  KEY idx_erp_site_visit_attachment_file (file_id),
  CONSTRAINT fk_erp_site_visit_attachment_visit FOREIGN KEY (site_visit_id) REFERENCES erp_site_visit(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_site_visit_attachment_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_site_visit_attachment_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE erp_project_issue
  ADD COLUMN source_site_visit_id BIGINT NULL AFTER project_id,
  ADD COLUMN reported_to_customer_on DATE NULL AFTER due_on,
  ADD COLUMN rectification_note TEXT NULL AFTER resolution,
  ADD COLUMN resolved_at DATETIME(3) NULL AFTER rectification_note,
  ADD COLUMN verified_at DATETIME(3) NULL AFTER resolved_at,
  ADD COLUMN verified_by BIGINT NULL AFTER verified_at,
  ADD CONSTRAINT fk_erp_project_issue_visit FOREIGN KEY (source_site_visit_id) REFERENCES erp_site_visit(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_erp_project_issue_verifier FOREIGN KEY (verified_by) REFERENCES erp_identity(id) ON DELETE SET NULL;

CREATE TABLE erp_project_issue_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  content TEXT NULL,
  actor_identity_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_project_issue_event (issue_id,created_at),
  CONSTRAINT fk_erp_project_issue_event_issue FOREIGN KEY (issue_id) REFERENCES erp_project_issue(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_project_issue_event_actor FOREIGN KEY (actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Reports are project deliverables; legacy work-package reports are backfilled and remain readable.
ALTER TABLE erp_service_report
  ADD COLUMN project_id BIGINT NULL AFTER work_package_id;

UPDATE erp_service_report r
JOIN erp_service_work_package w ON w.id=r.work_package_id
SET r.project_id=w.project_id
WHERE r.project_id IS NULL;

ALTER TABLE erp_service_report
  MODIFY COLUMN work_package_id BIGINT NULL,
  MODIFY COLUMN project_id BIGINT NOT NULL,
  ADD KEY idx_erp_report_project (project_id,status),
  ADD CONSTRAINT fk_erp_report_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT;

CREATE TABLE erp_service_report_site_visit (
  report_id BIGINT NOT NULL,
  site_visit_id BIGINT NOT NULL,
  PRIMARY KEY (report_id,site_visit_id),
  CONSTRAINT fk_erp_report_visit_report FOREIGN KEY (report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_visit_visit FOREIGN KEY (site_visit_id) REFERENCES erp_site_visit(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_delivery (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  recipient_name VARCHAR(200) NULL,
  recipient_address VARCHAR(600) NULL,
  courier_name VARCHAR(120) NULL,
  tracking_no VARCHAR(120) NULL,
  sent_on DATE NULL,
  delivery_status VARCHAR(24) NOT NULL DEFAULT 'pending',
  delivered_on DATE NULL,
  notes VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_report_delivery (report_id,version_no,created_at),
  KEY idx_erp_report_delivery_tracking (tracking_no),
  CONSTRAINT fk_erp_report_delivery_report FOREIGN KEY (report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_delivery_version FOREIGN KEY (report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_delivery_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_report_delivery_status CHECK (delivery_status IN ('pending','sent','delivered','returned'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Each batch is tied to one project/report and keeps its original DOCX template and source photos private.
CREATE TABLE erp_report_photo_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  site_visit_id BIGINT NULL,
  report_id BIGINT NOT NULL,
  template_file_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'draft',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_photo_batch_project (project_id,created_at),
  KEY idx_erp_photo_batch_report (report_id,created_at),
  CONSTRAINT fk_erp_photo_batch_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_visit FOREIGN KEY (site_visit_id) REFERENCES erp_site_visit(id) ON DELETE SET NULL,
  CONSTRAINT fk_erp_photo_batch_report FOREIGN KEY (report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_template FOREIGN KEY (template_file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_photo_batch_creator FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_photo_batch_status CHECK (status IN ('draft','exported','archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  system_code VARCHAR(60) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  notes VARCHAR(1000) NULL,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_erp_report_photo_file (batch_id,file_id),
  KEY idx_erp_report_photo_sort (batch_id,system_code,sort_order),
  CONSTRAINT fk_erp_report_photo_batch FOREIGN KEY (batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT ck_erp_report_photo_system CHECK (
    system_code IS NULL OR system_code IN ('fire_alarm','fire_water','hydrant','sprinkler','smoke_control',
      'emergency_lighting','fire_separation','electrical_fire','fire_power_monitoring','fire_alarm_broadcast',
      'fire_phone','fire_elevator','fire_distribution','extinguisher','other')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo_export (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  report_id BIGINT NOT NULL,
  report_version_no INT NOT NULL,
  file_id BIGINT NOT NULL,
  mode_json JSON NOT NULL,
  manifest_json JSON NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_erp_report_photo_export_batch (batch_id,created_at),
  KEY idx_erp_report_photo_export_report (report_id,report_version_no),
  CONSTRAINT fk_erp_report_photo_export_batch FOREIGN KEY (batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_export_report FOREIGN KEY (report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_export_version FOREIGN KEY (report_id,report_version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_export_file FOREIGN KEY (file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
  CONSTRAINT fk_erp_report_photo_export_actor FOREIGN KEY (created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
