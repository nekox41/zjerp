package top.aameow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProductionBaselineTest {
    @Test
    void reviewedProductionBaselineMatchesAuditedSnapshotTotals() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/baselines/production-2026-11-21.json")) {
            assertNotNull(stream);
            var baseline = new ObjectMapper().readTree(stream);
            assertEquals(68, baseline.path("expectedTableCount").asInt());
            assertEquals(68, baseline.path("tables").size());
            long total = 0;
            for (var table : baseline.path("tables")) {
                total += table.path("expectedRows").asLong();
            }
            assertEquals(10978L, total);
        }
    }
}
