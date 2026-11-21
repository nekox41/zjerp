package top.aameow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects a MySQL dump without executing it.
 *
 * <p>Only DDL metadata and INSERT tuple boundaries are retained. Row values are
 * scanned to find tuple boundaries but are never decoded or written to the manifest.</p>
 */
public final class DumpInspector {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*ENGINE\\s*=");
    private static final Pattern INSERT_VALUES = Pattern.compile(
            "(?is)INSERT\\s+INTO\\s+`([^`]+)`(?:\\s*\\([^)]*\\))?\\s+VALUES\\s*");
    private static final Pattern COLUMN = Pattern.compile(
            "(?m)^\\s*`([^`]+)`\\s+([a-zA-Z0-9_]+(?:\\s*\\([^)]*\\))?)");
    private static final Pattern PRIMARY_KEY = Pattern.compile(
            "(?is)PRIMARY\\s+KEY\\s*\\(([^)]*)\\)");
    private static final Pattern FOREIGN_KEY = Pattern.compile(
            "(?is)CONSTRAINT\\s+`([^`]+)`\\s+FOREIGN\\s+KEY\\s*\\(([^)]*)\\)\\s+REFERENCES\\s+`([^`]+)`\\s*\\(([^)]*)\\)");
    private static final Pattern DATABASE = Pattern.compile(
            "(?m)^-- Host: .*?Database:\\s*([^\\s]+)");
    private static final Pattern SERVER_VERSION = Pattern.compile(
            "(?m)^-- Server version\\s+(.+)$");

    private DumpInspector() {
    }

    public static void main(String[] args) {
        try {
            Options options = parseArgs(args);
            String sql = Files.readString(Path.of(options.file()));
            Map<String, Object> manifest = inspectSql(sql, Path.of(options.file()).getFileName().toString());
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
                System.out.println("Dump manifest written. Table count: "
                        + manifest.get("tableCount") + "; row count: " + manifest.get("rowCount"));
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid dump-inspector arguments: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Dump inspection failed: " + e.getClass().getSimpleName());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Dump inspection failed: " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }

    static Options parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!"--file".equals(key) && !"--output".equals(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            if (values.putIfAbsent(key, args[++i]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }
        String file = values.get("--file");
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Required option missing: --file");
        }
        return new Options(file, values.get("--output"));
    }

    static Map<String, Object> inspectSql(String sql, String sourceFileName) {
        Map<String, TableInfo> tables = new LinkedHashMap<>();
        Matcher createMatcher = CREATE_TABLE.matcher(sql);
        while (createMatcher.find()) {
            String tableName = createMatcher.group(1);
            String body = createMatcher.group(2);
            tables.put(tableName, parseTable(tableName, body));
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("No MySQL CREATE TABLE statements were found");
        }

        Matcher insertMatcher = INSERT_VALUES.matcher(sql);
        while (insertMatcher.find()) {
            String tableName = insertMatcher.group(1);
            TableInfo table = tables.get(tableName);
            if (table == null) {
                throw new IllegalArgumentException("INSERT references a table without CREATE TABLE: " + tableName);
            }
            table.rowCount += countValueTuples(sql, insertMatcher.end());
        }

        List<Map<String, Object>> tableResults = new ArrayList<>();
        long totalRows = 0;
        List<TableInfo> sorted = new ArrayList<>(tables.values());
        sorted.sort(Comparator.comparing(info -> info.name));
        for (TableInfo info : sorted) {
            tableResults.add(info.toMap());
            totalRows += info.rowCount;
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", 1);
        manifest.put("parser", "mysql-dump-structure-v1");
        manifest.put("inspectedAt", Instant.now().toString());
        manifest.put("sourceFileName", sourceFileName);
        manifest.put("database", firstGroup(DATABASE, sql));
        manifest.put("serverVersion", firstGroup(SERVER_VERSION, sql));
        manifest.put("rowCountMode", "exact_insert_tuple_count");
        manifest.put("sourceModified", false);
        manifest.put("tableCount", tableResults.size());
        manifest.put("rowCount", totalRows);
        manifest.put("tables", tableResults);
        return manifest;
    }

    private static TableInfo parseTable(String tableName, String body) {
        TableInfo table = new TableInfo(tableName);

        Matcher columnMatcher = COLUMN.matcher(body);
        while (columnMatcher.find()) {
            int lineStart = body.lastIndexOf('\n', columnMatcher.start()) + 1;
            int lineEnd = body.indexOf('\n', columnMatcher.end());
            if (lineEnd < 0) {
                lineEnd = body.length();
            }
            String line = body.substring(lineStart, lineEnd);
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("name", columnMatcher.group(1));
            column.put("type", columnMatcher.group(2).trim());
            column.put("nullable", !line.toUpperCase().contains("NOT NULL"));
            table.columns.add(column);
        }

        Matcher primaryMatcher = PRIMARY_KEY.matcher(body);
        if (primaryMatcher.find()) {
            table.primaryKey.addAll(splitIdentifiers(primaryMatcher.group(1)));
        }

        Matcher foreignMatcher = FOREIGN_KEY.matcher(body);
        while (foreignMatcher.find()) {
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("name", foreignMatcher.group(1));
            key.put("columns", splitIdentifiers(foreignMatcher.group(2)));
            key.put("referencedTable", foreignMatcher.group(3));
            key.put("referencedColumns", splitIdentifiers(foreignMatcher.group(4)));
            table.foreignKeys.add(key);
        }
        return table;
    }

    private static List<String> splitIdentifiers(String value) {
        List<String> identifiers = new ArrayList<>();
        for (String part : value.split(",")) {
            identifiers.add(part.trim().replace("`", ""));
        }
        return identifiers;
    }

    /**
     * Counts top-level row tuples after VALUES while respecting SQL quotes,
     * escaped quote characters, doubled quotes and nested parentheses.
     */
    static long countValueTuples(String sql, int start) {
        long count = 0;
        int index = start;
        while (index < sql.length()) {
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            if (index >= sql.length() || sql.charAt(index) != '(') {
                break;
            }

            count++;
            int depth = 1;
            char quote = 0;
            while (index < sql.length() && depth > 0) {
                char current = sql.charAt(index);
                if (quote != 0) {
                    if (current == '\\') {
                        index += Math.min(2, sql.length() - index);
                        continue;
                    }
                    if (current == quote) {
                        if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                            index += 2;
                            continue;
                        }
                        quote = 0;
                    }
                } else if (current == '\'' || current == '"' || current == '`') {
                    quote = current;
                } else if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth--;
                }
                index++;
            }
            if (depth != 0 || quote != 0) {
                throw new IllegalArgumentException("Unterminated INSERT tuple in SQL dump");
            }

            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
                index++;
            }
            if (index < sql.length() && sql.charAt(index) == ',') {
                index++;
                continue;
            }
            break;
        }
        return count;
    }

    private static String firstGroup(Pattern pattern, String sql) {
        Matcher matcher = pattern.matcher(sql);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar zjerp-migration.jar --dump --file <mysqldump.sql> [--output <manifest.json>]

                The dump parser does not execute SQL or export row values.
                It records DDL metadata and counts INSERT tuples only.
                Treat the source dump as sensitive and store the manifest securely.
                """);
    }

    record Options(String file, String output) {
    }

    private static final class TableInfo {
        private final String name;
        private long rowCount;
        private final List<Map<String, Object>> columns = new ArrayList<>();
        private final List<String> primaryKey = new ArrayList<>();
        private final List<Map<String, Object>> foreignKeys = new ArrayList<>();

        private TableInfo(String name) {
            this.name = name;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("rowCount", rowCount);
            map.put("columns", columns);
            map.put("primaryKey", primaryKey);
            map.put("foreignKeys", foreignKeys);
            return map;
        }
    }
}
