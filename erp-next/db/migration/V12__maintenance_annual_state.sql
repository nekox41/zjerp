-- Preserve only known current-contract states; never invent historical annual statuses.
CREATE TABLE erp_maintenance_contract_state (
 contract_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 honeybee_contract_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
 honeybee_project_created BOOLEAN NOT NULL DEFAULT FALSE,
 project_materials_completed BOOLEAN NOT NULL DEFAULT FALSE,
 public_card_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
 notes TEXT NULL, updated_by BIGINT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(contract_id,project_id),
 FOREIGN KEY(contract_id,project_id) REFERENCES erp_contract_project(contract_id,project_id) ON DELETE RESTRICT,
 FOREIGN KEY(updated_by) REFERENCES erp_identity(id) ON DELETE SET NULL,
 CONSTRAINT ck_maintenance_annual_card_status CHECK(public_card_status IN ('not_started','preparing','sent','delivered'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO erp_maintenance_contract_state(contract_id,project_id,honeybee_contract_uploaded,honeybee_project_created,project_materials_completed,public_card_status,notes,updated_by,updated_at)
 SELECT m.current_contract_id,m.project_id,m.honeybee_contract_uploaded,m.honeybee_project_created,m.project_materials_completed,m.public_card_status,m.notes,m.updated_by,m.updated_at
 FROM erp_maintenance_project m JOIN erp_contract_project cp ON cp.project_id=m.project_id AND cp.contract_id=m.current_contract_id
 JOIN erp_contract c ON c.id=cp.contract_id WHERE c.service_type='maintenance';
