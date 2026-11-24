-- Keep original numbers, including old deleted/unlinked cards, unavailable for new generation.
CREATE TABLE erp_maintenance_card_legacy_number (
 code VARCHAR(100) NOT NULL PRIMARY KEY,
 source_namespace VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
