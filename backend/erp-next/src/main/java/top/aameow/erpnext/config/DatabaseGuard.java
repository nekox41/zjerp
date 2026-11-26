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
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!"erp_next".equals(database)) {
            throw new IllegalStateException("ERP Next must connect only to the isolated 'erp_next' database; actual database: " + database);
        }
        Integer versions = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND script='V1__initial_schema.sql' AND success=TRUE AND (SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL OR success=FALSE)=1", Integer.class);
        if (versions == null || versions != 1) {
            throw new IllegalStateException("ERP Next initial schema is required; pre-release development databases must be freshly deployed");
        }
    }
}