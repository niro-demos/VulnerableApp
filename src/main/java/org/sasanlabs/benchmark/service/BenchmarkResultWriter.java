package org.sasanlabs.benchmark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.sasanlabs.benchmark.model.BenchmarkResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists a {@link BenchmarkResult} to a JSON file under the configured benchmarks directory.
 * Filename is derived from the (sanitised) tool name; existing files are overwritten so callers
 * always see the latest run for a given tool.
 */
@Component
public class BenchmarkResultWriter {

    private final ObjectMapper objectMapper;
    private final String defaultBenchmarksDir;
    private final int maxResultFiles;
    private final long maxTotalBytes;

    public BenchmarkResultWriter(
            ObjectMapper objectMapper,
            @Value("${benchmark.output.dir:benchmarks}") String defaultBenchmarksDir) {
        this(objectMapper, defaultBenchmarksDir, 100, 104857600L);
    }

    @Autowired
    public BenchmarkResultWriter(
            ObjectMapper objectMapper,
            @Value("${benchmark.output.dir:benchmarks}") String defaultBenchmarksDir,
            @Value("${benchmark.max-result-files:100}") int maxResultFiles,
            @Value("${benchmark.max-total-result-bytes:104857600}") long maxTotalBytes) {
        this.objectMapper = objectMapper;
        this.defaultBenchmarksDir = defaultBenchmarksDir;
        this.maxResultFiles = maxResultFiles;
        this.maxTotalBytes = maxTotalBytes;
    }

    public Path write(BenchmarkResult result) throws IOException {
        return write(result, defaultBenchmarksDir);
    }

    public synchronized Path write(BenchmarkResult result, String benchmarksDir)
            throws IOException {
        Path dir = Paths.get(benchmarksDir);
        Files.createDirectories(dir);
        String fileName = resultFileName(result.getTool());
        Path target = dir.resolve(fileName);
        Path temp = Files.createTempFile(dir, fileName + ".", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), result);
            enforceQuota(dir, target, temp);
            moveAtomicallyOrReplace(temp, target);
        } catch (IOException ioe) {
            Files.deleteIfExists(temp);
            throw ioe;
        }
        return target;
    }

    private void enforceQuota(Path dir, Path target, Path pending) throws IOException {
        long fileCount = 0;
        long existingBytes = 0;
        try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (!Files.isRegularFile(entry) || entry.equals(pending)) continue;
                fileCount++;
                existingBytes += Files.size(entry);
            }
        }
        boolean replacing = Files.isRegularFile(target);
        long replacedBytes = replacing ? Files.size(target) : 0;
        if ((!replacing && fileCount >= maxResultFiles)
                || existingBytes - replacedBytes + Files.size(pending) > maxTotalBytes) {
            throw new IOException("Benchmark result storage quota exceeded");
        }
    }

    private static void moveAtomicallyOrReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notSupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final int MAX_TOOL_LENGTH = 64;

    static String resultFileName(String tool) {
        String original = tool == null ? "" : tool;
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(original.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return sanitizeToolName(tool) + "-" + hex + "-results.json";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    static String sanitizeToolName(String tool) {
        if (tool == null) {
            return "unknown";
        }
        String lowered = tool.trim().toLowerCase(Locale.ROOT);
        String cleaned = lowered.replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return "unknown";
        }
        if (cleaned.length() > MAX_TOOL_LENGTH) {
            cleaned = cleaned.substring(0, MAX_TOOL_LENGTH);
        }
        return cleaned;
    }
}
