-- V5: private files and immutable technical-service report versions/workflow.
CREATE TABLE erp_file_object (
 id BIGINT NOT NULL AUTO_INCREMENT,
 storage_key CHAR(36) NOT NULL,
 original_name VARCHAR(255) NOT NULL,
 media_type VARCHAR(120) NOT NULL,
 file_size BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 uploaded_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_file_storage_key(storage_key),
 KEY idx_erp_file_uploader(uploaded_by,created_at),
 CONSTRAINT fk_erp_file_uploader FOREIGN KEY(uploaded_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT ck_erp_file_size CHECK(file_size>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_no VARCHAR(100) NOT NULL,
 work_package_id BIGINT NOT NULL,
 title VARCHAR(240) NOT NULL,
 report_type VARCHAR(60) NOT NULL DEFAULT 'service',
 status VARCHAR(24) NOT NULL DEFAULT 'draft',
 revision_no INT NOT NULL DEFAULT 1,
 current_version_no INT NOT NULL DEFAULT 1,
 created_by BIGINT NOT NULL,
 reviewer_id BIGINT NULL,
 issuer_id BIGINT NULL,
 return_reason TEXT NULL,
 issued_at DATETIME(3) NULL,
 archived_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_report_no(report_no),
 KEY idx_erp_report_work_package(work_package_id,status),
 KEY idx_erp_report_status(status,updated_at),
 CONSTRAINT fk_erp_report_work_package FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_reviewer FOREIGN KEY(reviewer_id) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT fk_erp_report_issuer FOREIGN KEY(issuer_id) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_erp_report_status CHECK(status IN ('draft','submitted','reviewing','returned','approved','issued','archived','withdrawn')),
 CONSTRAINT ck_erp_report_revision CHECK(revision_no>0 AND current_version_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_version (
 report_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 content_json LONGTEXT NOT NULL,
 content_sha256 CHAR(64) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(report_id,version_no),
 CONSTRAINT fk_erp_report_version_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_version_creator FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_attachment (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 file_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_erp_report_attachment(report_id,version_no,file_id),
 KEY idx_erp_report_attachment_file(file_id),
 CONSTRAINT fk_erp_report_attachment_version FOREIGN KEY(report_id,version_no) REFERENCES erp_service_report_version(report_id,version_no) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_attachment_file FOREIGN KEY(file_id) REFERENCES erp_file_object(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_attachment_actor FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE erp_service_report_event (
 id BIGINT NOT NULL AUTO_INCREMENT,
 report_id BIGINT NOT NULL,
 from_status VARCHAR(24) NULL,
 to_status VARCHAR(24) NOT NULL,
 actor_identity_id BIGINT NOT NULL,
 reason TEXT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_erp_report_event(report_id,created_at),
 CONSTRAINT fk_erp_report_event_report FOREIGN KEY(report_id) REFERENCES erp_service_report(id) ON DELETE RESTRICT,
 CONSTRAINT fk_erp_report_event_actor FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO erp_permission(code,name) VALUES
 ('file:upload','上传业务附件'),('file:read','下载业务附件'),
 ('report:read','查看技术服务报告'),('report:write','编制技术服务报告'),
 ('report:review','审核技术服务报告'),('report:issue','签发技术服务报告')
 ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT IGNORE INTO erp_role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('file:upload','file:read','report:read','report:write','report:review')
 WHERE r.code='project_manager';
