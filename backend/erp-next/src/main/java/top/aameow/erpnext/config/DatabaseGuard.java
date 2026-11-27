package top.aameow.erpnext.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Protect the isolated ERP database and fail fast when its canonical schema
 * has not been installed completely. Schema creation is handled exclusively
 * by the packaged db/schema.sql, not by a migration framework.
 */
@Component
public class DatabaseGuard implements ApplicationRunner {
    private static final List<String> REQUIRED_TABLES = List.of(
        "erp_identity",
        "erp_employee",
        "erp_customer",
        "erp_contract",
        "erp_project",
        "erp_service_report",
        "erp_number_reservation"
    );

    private final JdbcTemplate jdbc;

    public DatabaseGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!"erp_next".equals(database)) {
            throw new IllegalStateException(
                "ERP Next must connect only to the isolated 'erp_next' database; actual database: " + database
            );
        }

        List<String> missing = new ArrayList<>();
        for (String table : REQUIRED_TABLES) {
            Boolean exists = jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                "public." + table
            );
            if (!Boolean.TRUE.equals(exists)) {
                missing.add(table);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "The canonical ERP database schema is incomplete; missing tables: "
                    + String.join(", ", missing)
                    + ". Recreate the preview database using the bundled db/schema.sql."
            );
        }
    }
}
