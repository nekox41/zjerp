package top.aameow.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares an inspected source manifest with the reviewed production snapshot baseline.
 * This is a structural preflight only; it never reads or validates business row values.
 */
public final class ManifestPreflight {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ManifestPreflight() {
    }

    public static void main(String[] args) {
        try {
            Options options = parseArgs(args);
            JsonNode manifest = JSON.readTree(Files.readString(Path.of(options.manifest())));
            JsonNode baseline = JSON.readTree(Files.readString(Path.of(options.baseline())));
            Map<String, Object> report = validate(manifest, baseline);
            String output = JSON.writeValueAsString(report) + System.lineSeparator();

            if (options.output() == null) {
                System.out.print(output);
            } else {
                Path destination = Path.of(options.output()).toAbsolutePath().normalize();
                if (destination.getParent() != null) {
                    Files.createDirectories(destination.getParent());
                }
                Files.writeString(destination, output);
                System.out.println("Preflight report written. Status: " + report.get("status"));
            }

            if ("blocked".equals(report.get("status"))) {
                System.exit(3);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid preflight arguments: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Preflight failed: " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }

    static Options parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!"--manifest".equals(key) && !"--baseline".equals(key) && !"--output".equals(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            if (values.putIfAbsent(key, args[++i]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }
        String manifest = values.get("--manifest");
        String baseline = values.get("--baseline");
        if (manifest == null || manifest.isBlank() || baseline == null || baseline.isBlank()) {
            throw new IllegalArgumentException("--manifest and --baseline are required");
        }
        return new Options(manifest, baseline, values.get("--output"));
    }

    static Map<String, Object> validate(JsonNode manifest, JsonNode baseline) {
        List<Map<String, Object>> checks = new ArrayList<>();
        JsonNode manifestTables = manifest.path("tables");
        JsonNode baselineTables = baseline.path("tables");
        Map<String, JsonNode> actualByName = new LinkedHashMap<>();

        if (!manifestTables.isArray()) {
            throw new IllegalArgumentException("Manifest must contain a tables array");
        }
        for (JsonNode table : manifestTables) {
            String name = table.path("name").asText("");
            if (name.isBlank() || actualByName.putIfAbsent(name, table) != null) {
                checks.add(check("DUPLICATE_OR_INVALID_TABLE", "error",
                        "Manifest contains a blank or duplicate table name", name));
            }
        }

        int expectedCount = baseline.path("expectedTableCount").asInt(-1);
        addCheck(checks, "TABLE_COUNT", expectedCount >= 0
                        && actualByName.size() == expectedCount ? "pass" : "error",
                "Expected " + expectedCount + " tables; found " + actualByName.size(), null);

        int missing = 0;
        int drifted = 0;
        int missingPrimaryKey = 0;
        long expectedRows = 0;
        long actualRows = 0;

        Iterator<JsonNode> expected = baselineTables.elements();
        while (expected.hasNext()) {
            JsonNode item = expected.next();
            String name = item.path("name").asText();
            long rows = item.path("expectedRows").asLong(-1);
            expectedRows += Math.max(rows, 0);
            JsonNode actual = actualByName.get(name);
            if (actual == null) {
                missing++;
                addCheck(checks, "MISSING_TABLE", "error", "Required source table is absent", name);
                continue;
            }

            long actualCount = actual.path("rowCount").asLong(-1);
            actualRows += Math.max(actualCount, 0);
            if (rows >= 0 && actualCount != rows) {
                drifted++;
                addCheck(checks, "ROW_COUNT_DRIFT", "warning",
                        "Reviewed snapshot has " + rows + " rows; current manifest has " + actualCount, name);
            }

            if (item.path("requirePrimaryKey").asBoolean(false)
                    && (!actual.path("primaryKey").isArray() || actual.path("primaryKey").isEmpty())) {
                missingPrimaryKey++;
                addCheck(checks, "PRIMARY_KEY_MISSING", "error",
                        "Business source table has no primary key in manifest", name);
            }
        }

        int unexpected = 0;
        for (String name : actualByName.keySet()) {
            boolean known = false;
            for (JsonNode item : baselineTables) {
                if (name.equals(item.path("name").asText())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                unexpected++;
                addCheck(checks, "UNEXPECTED_TABLE", "warning",
                        "Source table is not in the reviewed production baseline", name);
            }
        }

        boolean blocked = missing > 0 || missingPrimaryKey > 0
                || checks.stream().anyMatch(item -> "error".equals(item.get("severity")));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportVersion", 1);
        report.put("generatedAt", Instant.now().toString());
        report.put("scope", "schema_and_row_count_preflight_only");
        report.put("status", blocked ? "blocked" : "review_required");
        report.put("expectedTableCount", expectedCount);
        report.put("actualTableCount", actualByName.size());
        report.put("expectedRowsForBaselinedTables", expectedRows);
        report.put("actualRowsForBaselinedTables", actualRows);
        report.put("missingTables", missing);
        report.put("rowCountDriftTables", drifted);
        report.put("tablesMissingRequiredPrimaryKey", missingPrimaryKey);
        report.put("unexpectedTables", unexpected);
        report.put("checks", checks);
        report.put("businessDataInspected", false);
        report.put("sourceModified", false);
        return report;
    }

    private static void addCheck(List<Map<String, Object>> checks, String code,
                                 String severity, String message, String table) {
        checks.add(check(code, severity, message, table));
    }

    private static Map<String, Object> check(String code, String severity,
                                             String message, String table) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("severity", severity);
        item.put("message", message);
        if (table != null) {
            item.put("table", table);
        }
        return item;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar zjerp-migration.jar --preflight
                    --manifest <source-manifest.json>
                    --baseline <reviewed-baseline.json>
                    [--output <preflight-report.json>]

                This command checks table presence, row-count drift and required primary keys.
                It does not inspect business values or modify the source.
                """);
    }

    record Options(String manifest, String baseline, String output) {
    }
}
