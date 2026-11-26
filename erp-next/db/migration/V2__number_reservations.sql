CREATE TABLE erp_number_reservation (
  reservation_id UUID PRIMARY KEY,
  service_type VARCHAR(40) NOT NULL,
  number_year INTEGER NOT NULL,
  sequence_no INTEGER NOT NULL,
  reservation_no VARCHAR(80) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  reserved_by BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'reserved',
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  contract_id BIGINT NULL,
  project_id BIGINT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  consumed_at TIMESTAMP WITH TIME ZONE NULL,
  released_at TIMESTAMP WITH TIME ZONE NULL,
  CONSTRAINT ck_erp_number_reservation_type CHECK (service_type IN ('maintenance','fire_inspection','fire_assessment','fire_evaluation','other')),
  CONSTRAINT ck_erp_number_reservation_purpose CHECK (purpose IN ('contract','project','maintenance_renewal')),
  CONSTRAINT ck_erp_number_reservation_status CHECK (status IN ('reserved','consumed','released')),
  CONSTRAINT ck_erp_number_reservation_value CHECK (number_year >= 2000 AND sequence_no > 0),
  CONSTRAINT fk_erp_number_reservation_contract FOREIGN KEY (contract_id) REFERENCES erp_contract(id) ON DELETE SET NULL,
  CONSTRAINT fk_erp_number_reservation_project FOREIGN KEY (project_id) REFERENCES erp_project(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_erp_number_reservation_active
  ON erp_number_reservation(service_type, number_year, sequence_no)
  WHERE status IN ('reserved','consumed');

CREATE INDEX ix_erp_number_reservation_owner
  ON erp_number_reservation(reserved_by, status, expires_at);
