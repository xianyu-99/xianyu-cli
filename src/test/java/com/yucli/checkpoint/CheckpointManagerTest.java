package com.yucli.checkpoint;

import com.yucli.policy.PolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointManagerTest {

    @Test
    void restoresExistingFileContent(@TempDir Path tempDir) throws Exception {
        Path project = createProject(tempDir);
        Path file = project.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "old content", StandardCharsets.UTF_8);

        CheckpointManager manager = new CheckpointManager(project, tempDir.resolve("checkpoints"));
        CheckpointEntry entry = manager.checkpointBeforeWrite("src/App.java");
        Files.writeString(file, "new content", StandardCharsets.UTF_8);

        Optional<CheckpointEntry> restored = manager.restoreLast();

        assertTrue(restored.isPresent());
        assertEquals(entry.getId(), restored.get().getId());
        assertEquals("old content", Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(manager.listRecent(10).isEmpty());
    }

    @Test
    void deletesFileThatDidNotExistBeforeCheckpoint(@TempDir Path tempDir) throws Exception {
        Path project = createProject(tempDir);
        CheckpointManager manager = new CheckpointManager(project, tempDir.resolve("checkpoints"));
        Path file = project.resolve("new-file.txt");

        manager.checkpointBeforeWrite("new-file.txt");
        Files.writeString(file, "created", StandardCharsets.UTF_8);

        Optional<CheckpointEntry> restored = manager.restoreLast();

        assertTrue(restored.isPresent());
        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsPathOutsideProjectRoot(@TempDir Path tempDir) throws Exception {
        Path project = createProject(tempDir);
        Path outside = tempDir.resolve("outside.txt");
        CheckpointManager manager = new CheckpointManager(project, tempDir.resolve("checkpoints"));

        assertThrows(PolicyException.class, () -> manager.checkpointBeforeWrite(outside.toString()));
        assertThrows(PolicyException.class, () -> manager.checkpointBeforeWrite("../outside.txt"));
    }

    @Test
    void listsRecentCheckpointsNewestFirst(@TempDir Path tempDir) throws Exception {
        Path project = createProject(tempDir);
        CheckpointManager manager = new CheckpointManager(project, tempDir.resolve("checkpoints"));

        CheckpointEntry first = manager.checkpointBeforeWrite("first.txt");
        waitUntilClockAdvancesPast(first.getCreatedAt());
        CheckpointEntry second = manager.checkpointBeforeWrite("second.txt");
        waitUntilClockAdvancesPast(second.getCreatedAt());
        CheckpointEntry third = manager.checkpointBeforeWrite("third.txt");

        List<CheckpointEntry> recent = manager.listRecent(2);

        assertEquals(2, recent.size());
        assertEquals(third.getId(), recent.get(0).getId());
        assertEquals(second.getId(), recent.get(1).getId());
    }

    private static Path createProject(Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);
        return project;
    }

    private static void waitUntilClockAdvancesPast(long timestamp) throws InterruptedException {
        while (System.currentTimeMillis() <= timestamp) {
            Thread.sleep(1);
        }
    }
}
