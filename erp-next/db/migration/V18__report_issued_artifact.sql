-- V18: retain the exact submitted/issued report file and each version's title.
-- Earlier versions lack evidence of which artifact was approved: do not guess/backfill.
ALTER TABLE erp_service_report_version
 ADD COLUMN title_snapshot VARCHAR(240) NULL,
 ADD COLUMN submitted_artifact_id BIGINT NULL,
 ADD COLUMN submitted_by BIGINT NULL,
 ADD COLUMN reviewed_by BIGINT NULL,
 ADD COLUMN issued_by BIGINT NULL,
 ADD COLUMN version_issued_at DATETIME(3) NULL,
 ADD CONSTRAINT fk_report_version_selected_artifact FOREIGN KEY(submitted_artifact_id) REFERENCES erp_service_report_artifact(id) ON DELETE RESTRICT,
 ADD CONSTRAINT fk_report_version_submitter FOREIGN KEY(submitted_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 ADD CONSTRAINT fk_report_version_reviewer FOREIGN KEY(reviewed_by) REFERENCES erp_identity(id) ON DELETE RESTRICT,
 ADD CONSTRAINT fk_report_version_issuer FOREIGN KEY(issued_by) REFERENCES erp_identity(id) ON DELETE RESTRICT;

ALTER TABLE erp_service_delivery
 ADD COLUMN artifact_id BIGINT NULL,
 ADD CONSTRAINT fk_delivery_issued_artifact FOREIGN KEY(artifact_id) REFERENCES erp_service_report_artifact(id) ON DELETE RESTRICT;
