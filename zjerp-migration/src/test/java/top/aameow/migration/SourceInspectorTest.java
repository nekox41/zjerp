package top.aameow.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceInspectorTest {

    @Test
    void parsesMysqlSourceOptionsWithoutInlinePassword() {
        SourceInspector.Options options = SourceInspector.parseArgs(new String[]{
                "--url", "jdbc:mysql://localhost:3306/server",
                "--user", "migration_reader",
                "--password-env", "SOURCE_PASSWORD",
                "--database", "server",
                "--output", "build/source-manifest.json"
        });

        assertEquals("jdbc:mysql://localhost:3306/server", options.url());
        assertEquals("migration_reader", options.user());
        assertEquals("SOURCE_PASSWORD", options.passwordEnv());
        assertEquals("server", options.database());
        assertEquals("build/source-manifest.json", options.output());
        assertFalse(options.help());
    }

    @Test
    void defaultsPasswordEnvironmentVariable() {
        SourceInspector.Options options = SourceInspector.parseArgs(new String[]{
                "--url", "jdbc:mysql://localhost:3306/server",
                "--user", "reader"
        });

        assertEquals("ZJERP_SOURCE_DB_PASSWORD", options.passwordEnv());
    }

    @Test
    void rejectsInlinePassword() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SourceInspector.parseArgs(new String[]{
                        "--url", "jdbc:mysql://localhost:3306/server",
                        "--user", "reader",
                        "--password", "do-not-accept"
                }));

        assertTrue(error.getMessage().contains("environment variable"));
    }

    @Test
    void rejectsPasswordEmbeddedInJdbcUrl() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SourceInspector.parseArgs(new String[]{
                        "--url", "jdbc:mysql://localhost:3306/server?password=secret",
                        "--user", "reader"
                }));

        assertTrue(error.getMessage().contains("JDBC URL"));
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceInspector.parseArgs(new String[]{
                        "--url", "jdbc:mysql://localhost:3306/server",
                        "--user", "reader",
                        "--drop-source"
                }));
    }

    @Test
    void quotesSqlIdentifiersSafely() {
        assertEquals("`normal_name`", SourceInspector.quoteIdentifier("normal_name"));
        assertEquals("`odd``name`", SourceInspector.quoteIdentifier("odd`name"));
        assertThrows(IllegalArgumentException.class,
                () -> SourceInspector.quoteIdentifier(" "));
    }
}
