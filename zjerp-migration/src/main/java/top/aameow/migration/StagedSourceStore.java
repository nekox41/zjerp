package top.aameow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Private, filesystem-backed staging for legacy SQL dumps.
 *
 * <p>Uploaded SQL is treated as untrusted data. This class stores bytes only;
 * it never executes SQL or places files under the public upload directory.</p>
 */
public final class StagedSourceStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE_TYPE = "MYSQL_DUMP";
    private static final String STATUS = "STAGED_UNSCANNED";

    private final Path root;
    private final long maxBytes;

    public StagedSourceStore(Path root, long maxBytes) throws IOException {
        if (root == null || maxBytes <= 0) {
            throw new IllegalArgumentException("A staging directory and positive size limit are required");
        }
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("The staging directory must not be a symbolic link");
        }
        this.root = root.toRealPath();
        restrictPermissions(this.root, true);
        this.maxBytes = maxBytes;
    }

    public StagedSource stage(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("请选择非空的 .sql 数据库备份文件");
        }
        String originalName = safeDisplayName(upload.getOriginalFilename());
        if (!originalName.toLowerCase().endsWith(".sql")) {
            throw new IllegalArgumentException("当前仅支持未压缩的 .sql 数据库备份文件");
        }
        if (upload.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过允许大小（" + maxBytes + " 字节）");
        }

        String id = UUID.randomUUID().toString();
        Path temporary = Files.createTempFile(root, ".incoming-", ".tmp");
        restrictPermissions(temporary, false);
        Path payload = resolve(id, ".sql");
        Path metadata = resolve(id, ".json");
        boolean payloadCommitted = false;
        try {
            DigestResult digest = copyAndDigest(upload, temporary);
            moveAtomically(temporary, payload);
            payloadCommitted = true;

            StagedSource source = new StagedSource(
                    id, SOURCE_TYPE, STATUS, originalName, digest.size(),
                    digest.sha256(), Instant.now().toString());

            Path metadataTemp = Files.createTempFile(root, ".metadata-", ".tmp");
            restrictPermissions(metadataTemp, false);
            try {
                JSON.writeValue(metadataTemp.toFile(), source);
                moveAtomically(metadataTemp, metadata);
            } finally {
                Files.deleteIfExists(metadataTemp);
            }
            return source;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            if (payloadCommitted) {
                Files.deleteIfExists(payload);
            }
            Files.deleteIfExists(metadata);
            throw e;
        }
    }

    public StagedSource get(String sourceId) throws IOException {
        String id = validateId(sourceId);
        Path metadata = resolve(id, ".json");
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        StagedSource source = JSON.readValue(metadata.toFile(), StagedSource.class);
        if (!id.equals(source.id())) {
            throw new IOException("Staging metadata identity mismatch");
        }
        return source;
    }

    public List<StagedSource> list() throws IOException {
        List<StagedSource> sources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.json")) {
            for (Path path : stream) {
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                StagedSource source = JSON.readValue(path.toFile(), StagedSource.class);
                if (source.id() != null && source.id().matches("[0-9a-fA-F-]{36}")) {
                    sources.add(source);
                }
            }
        }
        sources.sort(Comparator.comparing(StagedSource::uploadedAt).reversed());
        return sources;
    }

    public Path payloadPath(String sourceId) throws IOException {
        String id = validateId(sourceId);
        Path payload = resolve(id, ".sql");
        if (!Files.isRegularFile(payload, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(payload)) {
            throw new IOException("Staged source payload is missing");
        }
        return payload;
    }

    private DigestResult copyAndDigest(MultipartFile upload, Path destination) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }

        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = upload.getInputStream();
             var output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("文件超过允许大小（" + maxBytes + " 字节）");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw new IllegalArgumentException("数据库备份文件不能为空");
        }
        return new DigestResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private Path resolve(String id, String suffix) throws IOException {
        Path path = root.resolve(validateId(id) + suffix).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid staging source identifier");
        }
        return path;
    }

    private static String validateId(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid source identifier");
        }
        return id;
    }

    private static String safeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("数据库备份文件名无效");
        }
        String normalized = name.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (baseName.isBlank() || baseName.length() > 255 || baseName.equals(".") || baseName.equals("..")) {
            throw new IllegalArgumentException("数据库备份文件名无效");
        }
        return baseName;
    }

    private static void restrictPermissions(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(
                    directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX filesystems rely on their configured ACLs.
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private record DigestResult(long size, String sha256) {
    }

    public record StagedSource(
            String id,
            String sourceType,
            String status,
            String originalFileName,
            long sizeBytes,
            String sha256,
            String uploadedAt) {
    }
}
