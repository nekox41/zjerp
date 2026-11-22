CREATE TABLE IF NOT EXISTS zj_employee (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) NOT NULL,employee_type VARCHAR(100) NOT NULL DEFAULT '待确认',active BOOLEAN NOT NULL DEFAULT TRUE,
 remark VARCHAR(2000),version BIGINT NOT NULL DEFAULT 0,source_user_id BIGINT NULL UNIQUE,source_credential_id BIGINT NULL UNIQUE,
 created_by VARCHAR(64),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_employee_name(name),INDEX idx_employee_type(employee_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS zj_employee_migration(version VARCHAR(64) PRIMARY KEY,report JSON NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f776f726b5f70726f6a6563742041444420434f4c554d4e206c65616465725f656d706c6f7965655f696420424947494e54204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_work_project' AND column_name='leader_employee_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f776f726b5f70726f6a6563742041444420434f4c554d4e20746563686e6963616c5f656d706c6f7965655f696420424947494e54204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_work_project' AND column_name='technical_employee_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f776f726b5f70726f6a6563745f706c616e2041444420434f4c554d4e2061737369676e65655f656d706c6f7965655f696420424947494e54204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_work_project_plan' AND column_name='assignee_employee_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f7265696d62757273656d656e742041444420434f4c554d4e20656d706c6f7965655f696420424947494e54204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_reimbursement' AND column_name='employee_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f7265706f72745f63726564656e7469616c2041444420434f4c554d4e20656d706c6f7965655f696420424947494e54204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_report_credential' AND column_name='employee_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f7265706f72745f63726564656e7469616c2041444420434f4c554d4e2076616c75655f74797065205641524348415228313629204e4f54204e554c4c2044454641554c54202766696c6527' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_report_credential' AND column_name='value_type');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f7265706f72745f63726564656e7469616c2041444420434f4c554d4e20636f6e74656e742054455854204e554c4c' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_report_credential' AND column_name='content');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;

SET @employee_ddl=(SELECT IF(COUNT(*)=0,CONVERT(X'414c544552205441424c45207a6a5f7265706f72745f63726564656e7469616c2041444420434f4c554d4e206c65676163795f757365725f6d6174657269616c5f696420424947494e54204e554c4c20554e49515545' USING utf8mb4),'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zj_report_credential' AND column_name='legacy_user_material_id');
PREPARE employee_stmt FROM @employee_ddl; EXECUTE employee_stmt; DEALLOCATE PREPARE employee_stmt;
