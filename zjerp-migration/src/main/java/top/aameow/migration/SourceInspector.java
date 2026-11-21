package top.aameow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Read-only source database inspector.
 *
 * <p>This first migration utility only reads schema metadata and exact row counts.
 * It never reads business column values and never writes to the source database.</p>
 */
public final class SourceInspector {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private SourceInspector() {
    }

    public static void main(String[] args) {
        try {
            Options options = parseArgs(args);
            if (options.help()) {
                printUsage();
                return;
            }

            String password = System.getenv(options.passwordEnv());
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException(
                        "Environment variable " + options.passwordEnv() + " is not set");
            }

            Properties properties = new Properties();
            properties.setProperty("user", options.user());
            properties.setProperty("password", password);
            properties.setProperty("allowMultiQueries", "false");
            properties.setProperty("readOnlyPropagatesToServer", "true");
            properties.setProperty("connectTimeout", "10000");
            properties.setProperty("socketTimeout", "120000");

            Map<String, Object> manifest;
            try (Connection connection = DriverManager.getConnection(options.url(), properties)) {
                if (options.database() != null) {
                    connection.setCatalog(options.database());
                }
                manifest = inspect(connection);
            }

            String output = JSON.writeValueAsString(manifest) + System.lineSeparator();
            if (options.output() == null) {
                System.out.print(output);
            } else {
                Path destination = Path.of(options.output()).toAbsolutePath().normalize();
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(destination, output);
                System.out.println("Source manifest written. Table count: "
                        + manifest.get("tableCount") + "; row count: " + manifest.get("rowCount"));
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception e) {
            // Do not print JDBC URLs, connection properties, row values or credentials.
            String sqlState = e instanceof SQLException sqlException
                    ? " (SQLState " + sqlException.getSQLState() + ")" : "";
            System.err.println("Source inspection failed: "
                    + e.getClass().getSimpleName() + sqlState);
            System.exit(1);
        }
    }

    static Options parseArgs(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            return new Options(null, null, "ZJERP_SOURCE_DB_PASSWORD", null, null, true);
        }

        Map<String, String> values = new LinkedHashMap<>();
        Set<String> allowed = Set.of("--url", "--user", "--password-env", "--database", "--output");
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!allowed.contains(key) && !"--password".equals(key) && !"--password-value".equals(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if ("--password".equals(key) || "--password-value".equals(key)) {
                throw new IllegalArgumentException(
                        "Passwords must be supplied through an environment variable, not command-line arguments");
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            String value = args[++i];
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }

        String url = required(values, "--url");
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("Only MySQL JDBC URLs are supported");
        }
        if (url.matches("(?is).*([?&;])(password|pwd)=.*")) {
            throw new IllegalArgumentException(
                    "Do not put passwords in the JDBC URL; use --password-env");
        }

        return new Options(
                url,
                required(values, "--user"),
                values.getOrDefault("--password-env", "ZJERP_SOURCE_DB_PASSWORD"),
                values.get("--database"),
                values.get("--output"),
                false);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required option missing: " + key);
        }
        return value;
    }

    static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    static Map<String, Object> inspect(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        if (!connection.isReadOnly()) {
            throw new SQLException("JDBC driver did not enable read-only mode");
        }
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);

        try {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<Map<String, Object>> tables = new ArrayList<>();
            long totalRows = 0;

            try (ResultSet tableResults = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tableResults.next()) {
                    String tableName = tableResults.getString("TABLE_NAME");
                    Map<String, Object> table = inspectTable(connection, metadata, catalog, tableName);
                    totalRows += (Long) table.get("rowCount");
                    tables.add(table);
                }
            }

            tables.sort(Comparator.comparing(item -> (String) item.get("name")));

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("manifestVersion", 1);
            manifest.put("inspectedAt", Instant.now().toString());
            manifest.put("databaseProduct", metadata.getDatabaseProductName());
            manifest.put("databaseProductVersion", metadata.getDatabaseProductVersion());
            manifest.put("catalog", catalog);
            manifest.put("readOnly", true);
            manifest.put("rowCountMode", "exact");
            manifest.put("tableCount", tables.size());
            manifest.put("rowCount", totalRows);
            manifest.put("tables", tables);

            connection.commit();
            return manifest;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        }
    }

    private static Map<String, Object> inspectTable(
            Connection connection,
            DatabaseMetaData metadata,
            String catalog,
            String tableName) throws SQLException {

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", tableName);
        table.put("rowCount", countRows(connection, catalog, tableName));

        List<Map<String, Object>> columns = new ArrayList<>();
        try (ResultSet result = metadata.getColumns(catalog, null, tableName, "%")) {
            while (result.next()) {
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("name", result.getString("COLUMN_NAME"));
                column.put("ordinal", result.getInt("ORDINAL_POSITION"));
                column.put("type", result.getString("TYPE_NAME"));
                column.put("size", result.getInt("COLUMN_SIZE"));
                column.put("scale", result.getInt("DECIMAL_DIGITS"));
                column.put("nullable", result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                columns.add(column);
            }
        }
        table.put("columns", columns);

        List<Map<String, Object>> primaryKey = new ArrayList<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, null, tableName)) {
            while (result.next()) {
                Map<String, Object> key = new LinkedHashMap<>();
                key.put("name", result.getString("PK_NAME"));
                key.put("column", result.getString("COLUMN_NAME"));
                key.put("sequence", result.getShort("KEY_SEQ"));
                primaryKey.add(key);
            }
        }
        primaryKey.sort(Comparator.comparingInt(item -> ((Number) item.get("sequence")).intValue()));
        table.put("primaryKey", primaryKey);

        List<Map<String, Object>> foreignKeys = new ArrayList<>();
        try (ResultSet result = metadata.getImportedKeys(catalog, null, tableName)) {
            while (result.next()) {
                Map<String, Object> key = new LinkedHashMap<>();
                key.put("name", result.getString("FK_NAME"));
                key.put("column", result.getString("FKCOLUMN_NAME"));
                key.put("referencedTable", result.getString("PKTABLE_NAME"));
                key.put("referencedColumn", result.getString("PKCOLUMN_NAME"));
                key.put("sequence", result.getShort("KEY_SEQ"));
                foreignKeys.add(key);
            }
        }
        table.put("foreignKeys", foreignKeys);
        return table;
    }

    private static long countRows(Connection connection, String catalog, String tableName)
            throws SQLException {
        String qualifiedName = catalog == null || catalog.isBlank()
                ? quoteIdentifier(tableName)
                : quoteIdentifier(catalog) + "." + quoteIdentifier(tableName);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + qualifiedName)) {
            if (!result.next()) {
                throw new SQLException("Could not read table count");
            }
            return result.getLong(1);
        }
    }

    private static void printUsage() {
        System.out.println("""
                ZJERP read-only source inspector

                Usage:
                  java -jar zjerp-migration.jar --url <mysql-jdbc-url> --user <read-only-user>
                    [--password-env ZJERP_SOURCE_DB_PASSWORD]
                    [--database <database-name>]
                    [--output <manifest.json>]

                The password is read only from the named environment variable.
                The tool reads schema metadata and exact table row counts only.
                It does not read business row values or modify the source database.
                Use a database account granted SELECT/SHOW VIEW only.
                """);
    }

    record Options(
            String url,
            String user,
            String passwordEnv,
            String database,
            String output,
            boolean help) {
    }
}
