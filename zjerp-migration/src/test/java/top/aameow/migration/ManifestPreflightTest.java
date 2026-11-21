package top.aameow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ManifestPreflightTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void reportsRowCountDriftButDoesNotBlockSourceReview() throws Exception {
        var manifest = json.readTree("""
                {"tables":[
                  {"name":"contracts","rowCount":11,"primaryKey":["id"]},
                  {"name":"new_table","rowCount":2,"primaryKey":["id"]}
                ]}
                """);
        var baseline = json.readTree("""
                {"expectedTableCount":2,"tables":[
                  {"name":"contracts","expectedRows":10,"requirePrimaryKey":true},
                  {"name":"projects","expectedRows":0,"requirePrimaryKey":true}
                ]}
                """);

        Map<String, Object> report = ManifestPreflight.validate(manifest, baseline);

        assertEquals("blocked", report.get("status"));
        assertEquals(1, report.get("missingTables"));
        assertEquals(1, report.get("rowCountDriftTables"));
        assertEquals(1, report.get("unexpectedTables"));
        assertEquals(false, report.get("businessDataInspected"));
        assertEquals(false, report.get("sourceModified"));
    }

    @Test
    void allowsReviewedTableSetWithRowDriftAsReviewRequired() throws Exception {
        var manifest = json.readTree("""
                {"tables":[
                  {"name":"contracts","rowCount":11,"primaryKey":["id"]},
                  {"name":"projects","rowCount":20,"primaryKey":["id"]}
                ]}
                """);
        var baseline = json.readTree("""
                {"expectedTableCount":2,"tables":[
                  {"name":"contracts","expectedRows":10,"requirePrimaryKey":true},
                  {"name":"projects","expectedRows":20,"requirePrimaryKey":true}
                ]}
                """);

        Map<String, Object> report = ManifestPreflight.validate(manifest, baseline);

        assertEquals("review_required", report.get("status"));
        assertEquals(1, report.get("rowCountDriftTables"));
    }

    @Test
    void blocksMissingPrimaryKeyOnCriticalBusinessTable() throws Exception {
        var manifest = json.readTree("""
                {"tables":[{"name":"contracts","rowCount":10,"primaryKey":[]}]}
                """);
        var baseline = json.readTree("""
                {"expectedTableCount":1,"tables":[
                  {"name":"contracts","expectedRows":10,"requirePrimaryKey":true}
                ]}
                """);

        Map<String, Object> report = ManifestPreflight.validate(manifest, baseline);
        assertEquals("blocked", report.get("status"));
        assertEquals(1, report.get("tablesMissingRequiredPrimaryKey"));
    }

    @Test
    void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> ManifestPreflight.parseArgs(new String[]{"--manifest", "a.json", "--oops"}));
    }
}
