-- Stop application writers and make a full restorable backup before applying.
-- Only retired organization metadata and menu permissions are removed.
CREATE TEMPORARY TABLE retired_org_menu_ids AS
WITH RECURSIVE retired AS (
    SELECT menu_id FROM sys_menu
    WHERE perms LIKE 'system:dept:%' OR perms LIKE 'system:post:%'
       OR component IN ('system/dept/index', 'system/post/index')
    UNION ALL
    SELECT child.menu_id FROM sys_menu child JOIN retired parent ON child.parent_id=parent.menu_id
)
SELECT DISTINCT menu_id FROM retired;
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM retired_org_menu_ids);
DELETE FROM sys_menu WHERE menu_id IN (SELECT menu_id FROM retired_org_menu_ids);
DROP TEMPORARY TABLE retired_org_menu_ids;
DELETE FROM sys_config WHERE config_key='sys.account.captchaEnabled';
DROP TABLE IF EXISTS sys_user_post;
DROP TABLE IF EXISTS sys_role_dept;
DROP TABLE IF EXISTS sys_post;
DROP TABLE IF EXISTS sys_dept;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='dept_id'), 'ALTER TABLE sys_user DROP COLUMN dept_id', 'SELECT 1');
PREPARE role_only_stmt FROM @ddl;
EXECUTE role_only_stmt;
DEALLOCATE PREPARE role_only_stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_role' AND column_name='data_scope'), 'ALTER TABLE sys_role DROP COLUMN data_scope', 'SELECT 1');
PREPARE role_only_stmt FROM @ddl;
EXECUTE role_only_stmt;
DEALLOCATE PREPARE role_only_stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_role' AND column_name='dept_check_strictly'), 'ALTER TABLE sys_role DROP COLUMN dept_check_strictly', 'SELECT 1');
PREPARE role_only_stmt FROM @ddl;
EXECUTE role_only_stmt;
DEALLOCATE PREPARE role_only_stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_oper_log' AND column_name='dept_name'), 'ALTER TABLE sys_oper_log DROP COLUMN dept_name', 'SELECT 1');
PREPARE role_only_stmt FROM @ddl;
EXECUTE role_only_stmt;
DEALLOCATE PREPARE role_only_stmt;
