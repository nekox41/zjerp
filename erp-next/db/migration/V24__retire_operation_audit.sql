CREATE TABLE erp_photo_review_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id BIGINT NOT NULL,
 actor_identity_id BIGINT NULL,
 action VARCHAR(100) NOT NULL,
 summary_json JSON NULL,
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_photo_review_batch(batch_id,id),
 FOREIGN KEY(batch_id) REFERENCES erp_report_photo_batch(id) ON DELETE CASCADE,
 FOREIGN KEY(actor_identity_id) REFERENCES erp_identity(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO erp_photo_review_event(batch_id,actor_identity_id,action,summary_json,occurred_at)
 SELECT b.id,a.actor_identity_id,a.action,a.summary_json,a.occurred_at
 -- Match canonical decimal identifiers with explicit character set and collation,
 -- independent of the Flyway connection's collation; avoid numeric coercion of non-ID text.
 FROM erp_audit_event a JOIN erp_report_photo_batch b
 ON a.resource_id COLLATE utf8mb4_0900_ai_ci = CAST(b.id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_0900_ai_ci
 WHERE a.resource_type='erp_report_photo_batch'
 AND a.action IN ('report.photo_batch.submitted','report.photo_batch.reviewed','report.photo_batch.returned') ORDER BY a.id;
-- Preserve existing records; no live code reads or writes the retired audit store.
RENAME TABLE erp_audit_event TO erp_retired_audit_event;
