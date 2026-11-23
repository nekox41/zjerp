-- V8: complete technical-service workflow, durable photo batches and delivery registration.
-- Existing report versions remain immutable; every generated DOCX is stored as a version artifact.

CREATE TABLE erp_service_visit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 work_package_id BIGINT NOT NULL,
 visit_no INT NOT NULL,
 visit_date DATE NOT NULL,
 visit_type VARCHAR(30) NOT NULL DEFAULT 'inspection',
 title VARCHAR(240) NOT NULL,
 summary TEXT NULL,
 result_summary TEXT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'completed',
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_service_visit_no(work_package_id,visit_no),
 KEY idx_erp_service_visit_date(work_package_id,visit_date),
 CONSTRAINT fk_erp_visit_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_visit_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_visit_type CHECK(visit_type IN ('inspection','rectification_review','supplement','maintenance','other')),
 CONSTRAINT ck_erp_visit_status CHECK(status IN ('planned','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit_staff (
 visit_id BIGINT NOT NULL,
 employee_id BIGINT NOT NULL,
 staff_role VARCHAR(40) NOT NULL DEFAULT 'field_operator',
 PRIMARY KEY(visit_id,employee_id),
 CONSTRAINT fk_erp_visit_staff_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE CASCADE,
 CONSTRAINT fk_erp_visit_staff_employee FOREIGN KEY(employee_id) REFERENCES erp_employee(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_visit_staff_role CHECK(staff_role IN ('field_operator','engineer','team_lead','observer'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_visit_issue (
 visit_id BIGINT NOT NULL,
 issue_id BIGINT NOT NULL,
 relation_type VARCHAR(24) NOT NULL DEFAULT 'found',
 notes VARCHAR(1000) NULL,
 PRIMARY KEY(visit_id,issue_id),
 CONSTRAINT fk_erp_visit_issue_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_visit_issue_issue FOREIGN KEY(issue_id) REFERENCES erp_project_issue(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_visit_issue_relation CHECK(relation_type IN ('found','reviewed','resolved'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo_batch (
 id BIGINT NOT NULL AUTO_INCREMENT,
 work_package_id BIGINT NOT NULL,
 visit_id BIGINT NULL,
 report_id BIGINT NULL,
 title VARCHAR(240) NOT NULL,
 visit_date DATE NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'collecting',
 created_by BIGINT NOT NULL,
 reviewed_by BIGINT NULL,
 reviewed_at DATETIME(3) NULL,
 locked_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 KEY idx_erp_photo_batch_package(work_package_id,visit_date),
 KEY idx_erp_photo_batch_report(report_id,status),
 CONSTRAINT fk_erp_photo_batch_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_photo_batch_visit FOREIGN KEY(visit_id) REFERENCES erp_service_visit(id) ON DELETE SET NULL,
 CONSTRAINT fk_erp_photo_batch_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_photo_batch_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_photo_batch_reviewer FOREIGN KEY(reviewed_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_photo_batch_status CHECK(status IN ('collecting','submitted','reviewed','locked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_report_photo (
 id BIGINT NOT NULL AUTO_INCREMENT,
 batch_id BIGINT NOT NULL,
 file_id BIGINT NOT NULL,
 system_code VARCHAR(80) NULL,
 taken_on DATE NULL,
 caption VARCHAR(500) NULL,
 sort_order INT NOT NULL DEFAULT 0,
 confirmation_status VARCHAR(20) NOT NULL DEFAULT 'pending',
 confirmed_by BIGINT NULL,
 confirmed_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_erp_report_photo_file(batch_id,file_id),
 KEY idx_erp_report_photo_system(batch_id,system_code,sort_order),
 CONSTRAINT fk_erp_report_photo_batch FOREIGN KEY(batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_photo_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_photo_confirmer FOREIGN KEY(confirmed_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_report_photo_confirmation CHECK(confirmation_status IN ('pending','confirmed','rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_artifact (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 file_id BIGINT NOT NULL,
 artifact_type VARCHAR(30) NOT NULL DEFAULT 'docx',
 artifact_revision_no INT NOT NULL DEFAULT 1,
 sha256 CHAR(64) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_erp_report_artifact_version(report_id,version_no,artifact_type,artifact_revision_no),
 CONSTRAINT fk_erp_report_artifact_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_artifact_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_artifact_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_report_artifact_type CHECK(artifact_type IN ('docx','pdf','source_docx'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_delivery (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 delivery_method VARCHAR(24) NOT NULL DEFAULT 'courier',
 recipient_name VARCHAR(160) NOT NULL,
 recipient_phone VARCHAR(40) NULL,
 recipient_organization VARCHAR(240) NULL,
 recipient_address VARCHAR(800) NOT NULL,
 courier_name VARCHAR(120) NULL,
 tracking_no VARCHAR(120) NULL,
 dispatched_at DATETIME(3) NULL,
 delivered_at DATETIME(3) NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'prepared',
 notes TEXT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 KEY idx_erp_delivery_report(report_id,version_no),
 KEY idx_erp_delivery_tracking(tracking_no),
 CONSTRAINT fk_erp_delivery_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_delivery_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_delivery_method CHECK(delivery_method IN ('courier','hand_delivery','electronic')),
 CONSTRAINT ck_erp_delivery_status CHECK(status IN ('prepared','dispatched','delivered','returned','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_delivery_event (
 id BIGINT NOT NULL AUTO_INCREMENT,
 delivery_id BIGINT NOT NULL,
 from_status VARCHAR(24) NULL,
 to_status VARCHAR(24) NOT NULL,
 actor_identity_id BIGINT NOT NULL,
 notes TEXT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_delivery_event(delivery_id,created_at),
 CONSTRAINT fk_erp_delivery_event_delivery FOREIGN KEY(delivery_id) REFERENCES erp_service_delivery(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_delivery_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('service:visit:read','查看现场服务记录'),('service:visit:write','登记现场服务记录'),
 ('report:photo:read','查看报告照片批次'),('report:photo:write','整理报告照片'),
 ('delivery:read','查看报告交付记录'),('delivery:write','登记报告交付')
 ON DUPLICATE KEY UPDATE name=VALUES(name);

INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN
 ('service:visit:read','service:visit:write','report:photo:read','report:photo:write','delivery:read','delivery:write','report:read','report:write')
 WHERE r.code='project_manager';
