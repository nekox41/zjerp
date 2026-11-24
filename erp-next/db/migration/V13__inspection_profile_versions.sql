-- E06: versioned inspection working data, scoped to service work package.
CREATE TABLE erp_inspection_profile_version (
 work_package_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 content_json JSON NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(work_package_id,version_no),
 FOREIGN KEY(work_package_id) REFERENCES erp_service_work_package(id) ON DELETE RESTRICT,
 FOREIGN KEY(created_by) REFERENCES erp_identity(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
