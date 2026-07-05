package com.yucli.checkpoint;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yucli.policy.PathGuard;
import com.yucli.policy.PolicyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CheckpointManager {
    private static final String CHECKPOINTS_DIR_NAME = "checkpoints";
    private static final String ENTRIES_DIR_NAME = "entries";
    private static final String SNAPSHOTS_DIR_NAME = "snapshots";
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final PathGuard pathGuard;
    private final Path projectRoot;
    private final Path storageDir;
    private final Path entriesDir;
    private final Path snapshotsDir;
    private final ObjectMapper mapper;

    public CheckpointManager(Path projectRoot) {
        this(projectRoot, defaultCheckpointsRoot());
    }

    public CheckpointManager(Path projectRoot, Path checkpointsRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(checkpointsRoot, "checkpointsRoot");
        this.pathGuard = new PathGuard(projectRoot.toString());
        this.projectRoot = pathGuard.getRootPath();
        this.storageDir = checkpointsRoot.toAbsolutePath().normalize()
                .resolve(hashProject(this.projectRoot))
                .normalize();
        this.entriesDir = storageDir.resolve(ENTRIES_DIR_NAME);
        this.snapshotsDir = storageDir.resolve(SNAPSHOTS_DIR_NAME);
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public synchronized CheckpointEntry checkpointBeforeWrite(String relativeOrAbsolutePath) throws IOException {
        Path target = resolveTarget(relativeOrAbsolutePath);
        String targetPath = toStoredRelativePath(target);
        boolean existedBefore = Files.exists(target);
        String snapshotFile = null;
        long sizeBytes = 0;

        if (existedBefore) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Only regular files can be checkpointed: " + target);
            }
            sizeBytes = Files.size(target);
        }

        ensureStorageDirs();
        String id = newCheckpointId();

        if (existedBefore) {
            snapshotFile = id + ".snapshot";
            Files.copy(target, resolveSnapshotFile(snapshotFile), StandardCopyOption.REPLACE_EXISTING);
        }

        CheckpointEntry entry = new CheckpointEntry(
                id,
                System.currentTimeMillis(),
                targetPath,
                existedBefore,
                snapshotFile,
                sizeBytes);
        mapper.writeValue(resolveEntryFile(id).toFile(), entry);
        return entry;
    }

    public synchronized Optional<CheckpointEntry> restoreLast() throws IOException {
        List<CheckpointEntry> recent = listRecent(1);
        if (recent.isEmpty()) {
            return Optional.empty();
        }

        CheckpointEntry entry = recent.get(0);
        restore(entry);
        deleteEntryFiles(entry);
        return Optional.of(entry);
    }

    public synchronized List<CheckpointEntry> listRecent(int limit) {
        if (limit <= 0 || !Files.isDirectory(entriesDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(entriesDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readEntry)
                    .flatMap(Optional::stream)
                    .sorted(Comparator
                            .comparingLong(CheckpointEntry::getCreatedAt).reversed()
                            .thenComparing(CheckpointEntry::getId, Comparator.reverseOrder()))
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public synchronized String statusText() {
        List<CheckpointEntry> recent = listRecent(5);
        if (recent.isEmpty()) {
            return "No checkpoints";
        }

        StringBuilder builder = new StringBuilder("Recent checkpoints:");
        for (CheckpointEntry entry : recent) {
            builder.append(System.lineSeparator())
                    .append("- ")
                    .append(entry.getId())
                    .append(" ")
                    .append(entry.getTargetPath())
                    .append(" ")
                    .append(entry.isExistedBefore() ? "saved" : "missing")
                    .append(" ")
                    .append(Instant.ofEpochMilli(entry.getCreatedAt()));
        }
        return builder.toString();
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getStorageDir() {
        return storageDir;
    }

    private void restore(CheckpointEntry entry) throws IOException {
        Path target = resolveTarget(entry.getTargetPath());
        if (entry.isExistedBefore()) {
            if (entry.getSnapshotFile() == null || entry.getSnapshotFile().isBlank()) {
                throw new IOException("Checkpoint snapshot is missing for " + entry.getId());
            }
            Path snapshot = resolveSnapshotFile(entry.getSnapshotFile());
            if (!Files.exists(snapshot)) {
                throw new IOException("Checkpoint snapshot file does not exist: " + snapshot);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(snapshot, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            deleteRecursively(target);
        }
    }

    private Path resolveTarget(String relativeOrAbsolutePath) {
        Path target = pathGuard.resolveSafe(relativeOrAbsolutePath);
        if (target.equals(projectRoot)) {
            throw new PolicyException("Cannot checkpoint project root");
        }
        return target;
    }

    private String toStoredRelativePath(Path target) {
        Path relative = projectRoot.relativize(target);
        String value = relative.toString().replace('\\', '/');
        if (value.isBlank()) {
            throw new PolicyException("Cannot checkpoint project root");
        }
        return value;
    }

    private void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!target.normalize().startsWith(projectRoot)) {
            throw new PolicyException("Path outside project root: " + target);
        }

        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(target);
            return;
        }

        try (Stream<Path> paths = Files.walk(target)) {
            List<Path> ordered = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : ordered) {
                Path safe = pathGuard.resolveSafe(path.toString());
                if (!safe.normalize().startsWith(projectRoot)) {
                    throw new PolicyException("Path outside project root: " + path);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private void deleteEntryFiles(CheckpointEntry entry) throws IOException {
        Files.deleteIfExists(resolveEntryFile(entry.getId()));
        if (entry.getSnapshotFile() != null && !entry.getSnapshotFile().isBlank()) {
            Files.deleteIfExists(resolveSnapshotFile(entry.getSnapshotFile()));
        }
    }

    private Optional<CheckpointEntry> readEntry(Path file) {
        try {
            CheckpointEntry entry = mapper.readValue(file.toFile(), CheckpointEntry.class);
            if (entry.getId() == null || entry.getTargetPath() == null || entry.getTargetPath().isBlank()) {
                return Optional.empty();
            }
            validateSafeFileName(entry.getId());
            if (entry.getSnapshotFile() != null && !entry.getSnapshotFile().isBlank()) {
                validateSafeFileName(entry.getSnapshotFile());
            }
            return Optional.of(entry);
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void ensureStorageDirs() throws IOException {
        Files.createDirectories(entriesDir);
        Files.createDirectories(snapshotsDir);
    }

    private Path resolveEntryFile(String id) {
        validateSafeFileName(id);
        Path file = entriesDir.resolve(id + ".json").normalize();
        if (!file.startsWith(entriesDir)) {
            throw new IllegalArgumentException("Invalid checkpoint id: " + id);
        }
        return file;
    }

    private Path resolveSnapshotFile(String snapshotFile) {
        validateSafeFileName(snapshotFile);
        Path file = snapshotsDir.resolve(snapshotFile).normalize();
        if (!file.startsWith(snapshotsDir)) {
            throw new IllegalArgumentException("Invalid snapshot file: " + snapshotFile);
        }
        return file;
    }

    private static void validateSafeFileName(String value) {
        if (value == null || value.isBlank() || !SAFE_FILE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe file name: " + value);
        }
    }

    private static String newCheckpointId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    private static Path defaultCheckpointsRoot() {
        return Path.of(System.getProperty("user.home"), ".YuCLI", CHECKPOINTS_DIR_NAME);
    }

    private static String hashProject(Path root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(root.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
