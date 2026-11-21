package top.aameow.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DumpInspectorTest {

    @Test
    void countsRowsWithoutBeingConfusedByQuotedCommasParenthesesOrSemicolons() {
        String sql = """
                -- Host: localhost    Database: sample
                -- Server version 9.6.0
                CREATE TABLE `sample` (
                  `id` int NOT NULL,
                  `payload` text,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB;
                CREATE TABLE `empty_table` (
                  `id` bigint NOT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB;
                INSERT INTO `sample` VALUES
                  (1, 'comma, parentheses (inside) and ; semicolon'),
                  (2, 'it''s a doubled quote');
                INSERT INTO `sample` (`id`, `payload`) VALUES (3, 'another row');
                """;

        Map<String, Object> manifest = DumpInspector.inspectSql(sql, "fixture.sql");

        assertEquals(2, manifest.get("tableCount"));
        assertEquals(3L, manifest.get("rowCount"));
        assertEquals("sample", manifest.get("database"));
        assertEquals("9.6.0", manifest.get("serverVersion"));
        assertEquals(false, manifest.get("sourceModified"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) manifest.get("tables");
        Map<String, Object> sample = tables.stream()
                .filter(table -> "sample".equals(table.get("name")))
                .findFirst().orElseThrow();
        Map<String, Object> empty = tables.stream()
                .filter(table -> "empty_table".equals(table.get("name")))
                .findFirst().orElseThrow();

        assertEquals(3L, sample.get("rowCount"));
        assertEquals(0L, empty.get("rowCount"));
        assertFalse(manifest.toString().contains("comma, parentheses"));
    }

    @Test
    void extractsPrimaryAndForeignKeys() {
        String sql = """
                CREATE TABLE `parent` (
                  `id` bigint NOT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB;
                CREATE TABLE `child` (
                  `id` bigint NOT NULL,
                  `parent_id` bigint DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  CONSTRAINT `fk_child_parent` FOREIGN KEY (`parent_id`) REFERENCES `parent` (`id`)
                ) ENGINE=InnoDB;
                """;

        Map<String, Object> manifest = DumpInspector.inspectSql(sql, "keys.sql");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) manifest.get("tables");
        Map<String, Object> child = tables.stream()
                .filter(table -> "child".equals(table.get("name")))
                .findFirst().orElseThrow();

        assertEquals(List.of("id"), child.get("primaryKey"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) child.get("foreignKeys");
        assertEquals(1, foreignKeys.size());
        assertEquals("parent", foreignKeys.get(0).get("referencedTable"));
    }

    @Test
    void rejectsDumpWithoutCreateTable() {
        assertThrows(IllegalArgumentException.class,
                () -> DumpInspector.inspectSql("INSERT INTO `x` VALUES (1);", "bad.sql"));
    }

    @Test
    void requiresDumpFileArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> DumpInspector.parseArgs(new String[]{"--output", "manifest.json"}));
    }
}
