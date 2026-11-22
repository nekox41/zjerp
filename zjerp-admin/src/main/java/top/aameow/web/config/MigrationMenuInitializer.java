package top.aameow.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds the migration-center navigation entry for the super-admin role.
 * Idempotent: the menu is identified by its stable permission key.
 */
@Component
public class MigrationMenuInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(MigrationMenuInitializer.class);
    private static final String PERMISSION = "system:migration:list";

    private final JdbcTemplate jdbcTemplate;

    public MigrationMenuInitializer(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        Long menuId = findMenuId();
        if (menuId == null)
        {
            Long parentId = jdbcTemplate.query(
                    "select menu_id from sys_menu where menu_name = ? and parent_id = 0 order by menu_id limit 1",
                    rs -> rs.next() ? rs.getLong(1) : null,
                    "系统管理");
            if (parentId == null)
            {
                log.warn("System menu parent was not found; migration navigation was not installed");
                return;
            }

            jdbcTemplate.update("""
                    insert into sys_menu
                    (menu_name, parent_id, order_num, path, component, route_name,
                     is_frame, is_cache, menu_type, visible, status, perms, icon,
                     create_by, create_time, remark)
                    values (?, ?, ?, ?, ?, ?, 1, 1, 'C', '0', '0', ?, ?, 'system', now(),
                            '新版 ERP 内置历史数据迁移中心')
                    """,
                    "数据迁移", parentId, 10, "migration", "system/migration/index",
                    "MigrationCenter", PERMISSION, "database");
            menuId = findMenuId();
        }

        if (menuId == null)
        {
            log.warn("Migration navigation could not be resolved after initialization");
            return;
        }

        int inserted = jdbcTemplate.update("""
                insert into sys_role_menu (role_id, menu_id)
                select r.role_id, ?
                from sys_role r
                where r.role_key = 'admin'
                  and r.status = '0'
                  and r.del_flag = '0'
                  and not exists (
                    select 1 from sys_role_menu rm
                    where rm.role_id = r.role_id and rm.menu_id = ?
                  )
                """, menuId, menuId);
        if (inserted > 0)
        {
            log.info("Installed migration-center menu for the super-admin role");
        }
    }

    private Long findMenuId()
    {
        return jdbcTemplate.query(
                "select menu_id from sys_menu where perms = ? and status = '0' order by menu_id limit 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                PERMISSION);
    }
}
