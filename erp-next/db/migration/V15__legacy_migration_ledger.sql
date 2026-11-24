-- Source-to-target migration mapping, independent of original raw archive.
CREATE TABLE erp_legacy_migration_map (
 source_namespace VARCHAR(64) NOT NULL,
 source_table VARCHAR(64) NOT NULL,
 source_key VARCHAR(160) NOT NULL,
 source_sha256 CHAR(64) NOT NULL,
 decision_sha256 CHAR(64) NOT NULL,
 target_table VARCHAR(64) NOT NULL,
 target_id BIGINT NOT NULL,
 archive_sha256 CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(source_namespace,source_table,source_key),
 KEY idx_legacy_target(target_table,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE erp_legacy_migration_run (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 source_namespace VARCHAR(64) NOT NULL,
 archive_sha256 CHAR(64) NOT NULL,
 decision_sha256 CHAR(64) NOT NULL,
 summary_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_legacy_run(source_namespace,archive_sha256,decision_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
