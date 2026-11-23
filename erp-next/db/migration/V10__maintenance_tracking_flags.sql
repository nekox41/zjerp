-- Separate the four operational milestones that were previously collapsed into broad booleans.
ALTER TABLE erp_project
  ADD COLUMN honeybee_project_created TINYINT(1) NOT NULL DEFAULT 0 AFTER honeybee_uploaded,
  ADD COLUMN maintenance_card_prepared TINYINT(1) NOT NULL DEFAULT 0 AFTER documents_ready;
