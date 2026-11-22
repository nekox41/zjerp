package top.aameow.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StagedSourceStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void stagesSqlInPrivateDirectoryAndPersistsOnlySafeMetadata() throws Exception {
        byte[] bytes = "CREATE TABLE example (id BIGINT);".getBytes();
        StagedSourceStore store = new StagedSourceStore(tempDir.resolve("private"), 1024);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../production-backup.sql", "application/octet-stream", bytes);

        var staged = store.stage(upload);

        assertEquals("production-backup.sql", staged.originalFileName());
        assertEquals("MYSQL_DUMP", staged.sourceType());
        assertEquals("STAGED_UNSCANNED", staged.status());
        assertEquals(bytes.length, staged.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                staged.sha256());
        assertArrayEquals(bytes, Files.readAllBytes(store.payloadPath(staged.id())));
        assertEquals(staged, store.get(staged.id()));
        assertEquals(1, store.list().size());
        assertTrue(Files.isRegularFile(tempDir.resolve("private").resolve(staged.id() + ".json")));
    }

    @Test
    void rejectsNonSqlFilesAndEmptyFiles() throws Exception {
        StagedSourceStore store = new StagedSourceStore(tempDir.resolve("private"), 1024);
        assertThrows(IllegalArgumentException.class, () -> store.stage(
                new MockMultipartFile("file", "backup.zip", "application/zip", new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> store.stage(
                new MockMultipartFile("file", "empty.sql", "text/plain", new byte[0])));
    }

    @Test
    void enforcesSizeLimitWhileStreaming() throws Exception {
        StagedSourceStore store = new StagedSourceStore(tempDir.resolve("private"), 4);
        assertThrows(IllegalArgumentException.class, () -> store.stage(
                new MockMultipartFile("file", "large.sql", "text/plain", new byte[5])));
        try (var files = Files.list(tempDir.resolve("private"))) {
            assertEquals(0, files.count(), "failed upload must leave no staged files");
        }
    }

    @Test
    void rejectsInvalidIdentifiersAndDoesNotExposeFilesystemPaths() throws Exception {
        StagedSourceStore store = new StagedSourceStore(tempDir.resolve("private"), 1024);
        assertThrows(IllegalArgumentException.class, () -> store.get("../../etc/passwd"));
        var staged = store.stage(new MockMultipartFile("file", "backup.sql", "text/plain", new byte[]{1}));
        assertFalse(staged.toString().contains(tempDir.toString()));
    }
}
