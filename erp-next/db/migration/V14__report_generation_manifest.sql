-- Record exact source template, profile version, binding plan and selected photo batches for generated artifacts.
ALTER TABLE erp_service_report_artifact ADD COLUMN generation_manifest JSON NULL;
