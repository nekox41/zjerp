-- Fixed account tiers. Retain identity IDs, passwords and business references.
-- Fail before changes if an existing installation has ambiguous administrator ownership.
CREATE TEMPORARY TABLE tmp_admin_guard(n INT CHECK(n=1));
INSERT INTO tmp_admin_guard SELECT CASE
 WHEN EXISTS(SELECT 1 FROM erp_identity WHERE username='admin') THEN
   (SELECT COUNT(*) FROM erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id WHERE i.username='admin' AND i.status='active' AND r.code='admin')
 WHEN (SELECT COUNT(*) FROM erp_identity)=0 THEN 1
 ELSE (SELECT COUNT(*) FROM erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id WHERE i.status='active' AND r.code='admin')
 END;
DROP TEMPORARY TABLE tmp_admin_guard;
SET @has_named_admin=(SELECT COUNT(*) FROM erp_identity WHERE username='admin');
UPDATE erp_identity i JOIN erp_identity_role ir ON ir.identity_id=i.id JOIN erp_role r ON r.id=ir.role_id
 SET i.username='admin' WHERE @has_named_admin=0 AND r.code='admin' AND i.status='active';
INSERT INTO erp_role(code,name) VALUES('manager','管理人员') ON DUPLICATE KEY UPDATE name=VALUES(name);
UPDATE erp_role SET name='超级账户' WHERE code='admin';
UPDATE erp_role SET name='普通员工' WHERE code='employee';
CREATE TEMPORARY TABLE tmp_fixed_roles AS
 SELECT i.id,CASE WHEN i.username='admin' THEN 'admin'
 WHEN MAX(CASE WHEN r.code IN ('admin','project_manager','finance','manager') THEN 1 ELSE 0 END)=1 THEN 'manager'
 ELSE 'employee' END AS role_code,
 MAX(CASE WHEN r.code IS NULL OR r.code NOT IN ('admin','project_manager','finance','manager','employee') THEN 1 ELSE 0 END) AS needs_review
 FROM erp_identity i LEFT JOIN erp_identity_role ir ON ir.identity_id=i.id LEFT JOIN erp_role r ON r.id=ir.role_id GROUP BY i.id,i.username;
UPDATE erp_identity i JOIN tmp_fixed_roles f ON f.id=i.id SET i.status='disabled' WHERE f.needs_review=1 AND f.role_code<>'admin';
DELETE FROM erp_identity_role;
DELETE FROM erp_role_permission;
DELETE FROM erp_role WHERE code NOT IN ('admin','employee','manager');
DELETE FROM erp_permission WHERE code='audit:read';
INSERT INTO erp_identity_role(identity_id,role_id) SELECT f.id,r.id FROM tmp_fixed_roles f JOIN erp_role r ON r.code=f.role_code;
DROP TEMPORARY TABLE tmp_fixed_roles;
ALTER TABLE erp_identity_role ADD UNIQUE KEY uk_one_fixed_role(identity_id);
INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='admin';
INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r CROSS JOIN erp_permission p WHERE r.code='manager' AND p.code NOT LIKE 'identity:%' AND p.code NOT LIKE 'organization:write';
INSERT INTO erp_role_permission(role_id,permission_id) SELECT r.id,p.id FROM erp_role r JOIN erp_permission p ON p.code IN ('workspace:read','project:read','service:read','service:visit:read','service:visit:write','report:read','report:write','report:photo:read','report:photo:write','file:read','file:upload','expense:read','expense:write','maintenance:read','maintenance:write','employee:read') WHERE r.code='employee';
UPDATE erp_auth_session SET revoked_at=CURRENT_TIMESTAMP(3) WHERE revoked_at IS NULL;
