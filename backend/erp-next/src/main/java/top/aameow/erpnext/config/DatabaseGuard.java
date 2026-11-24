package top.aameow.erpnext.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuse to start if the new ERP is accidentally pointed at a legacy or
 * otherwise unexpected schema. The new application must only use erp_next.
 */
@Component
public class DatabaseGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DatabaseGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!"erp_next".equals(database)) {
            throw new IllegalStateException("ERP Next must connect only to the isolated 'erp_next' database; actual database: " + database);
        }
        Integer versions = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7','8','9','10','11','12','13','14','15','16','17','18','19','20') AND success=1", Integer.class);
        if (versions == null || versions != 20) {
            throw new IllegalStateException("ERP Next database schema V1–V20 is required before the API can start");
        }
    }
}