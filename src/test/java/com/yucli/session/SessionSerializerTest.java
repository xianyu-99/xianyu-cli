package com.yucli.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionSerializerTest {

    @TempDir
    File tempDir;

    private SessionSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new SessionSerializer(tempDir);
    }

    @Test
    void saveAndLoadRoundtrip() {
        Session session = new Session("test-id-1234", System.currentTimeMillis());
        session.setTaskSummary("test task");
        session.setModelName("glm-4");
        session.setProvider("zhipu");
        session.setTotalTokens(42);

        serializer.save(session);

        Session loaded = serializer.load("test-id-1234");
        assertNotNull(loaded);
        assertEquals("test-id-1234", loaded.getSessionId());
        assertEquals("test task", loaded.getTaskSummary());
        assertEquals("glm-4", loaded.getModelName());
        assertEquals("zhipu", loaded.getProvider());
        assertEquals(42, loaded.getTotalTokens());
    }

    @Test
    void loadPreservesTimestamps() {
        long createdAt = 1700000000000L;
        long updatedAt = 1700000060000L;
        Session session = new Session("ts-test-id", createdAt);
        session.setUpdatedAt(updatedAt);

        serializer.save(session);

        Session loaded = serializer.load("ts-test-id");
        assertNotNull(loaded);
        assertEquals(createdAt, loaded.getCreatedAt());
        assertEquals(updatedAt, loaded.getUpdatedAt());
    }

    @Test
    void loadNonExistentReturnsNull() {
        Session loaded = serializer.load("does-not-exist");
        assertNull(loaded);
    }

    @Test
    void saveAndLoadMultipleMessages() {
        Session session = new Session("multi-msg", System.currentTimeMillis());

        SessionMessage userMsg = new SessionMessage("user", "hello", 1700000000000L, 5);
        SessionMessage assistantMsg = new SessionMessage("assistant", "hi there", 1700000001000L, 8);
        SessionMessage systemMsg = new SessionMessage("system", "system prompt", 1700000002000L, 12);
        SessionMessage toolMsg = new SessionMessage("tool", "tool output", 1700000003000L, 10);
        toolMsg.setToolName("read_file");
        toolMsg.setToolResult("file contents here");

        session.getMessages().add(userMsg);
        session.getMessages().add(assistantMsg);
        session.getMessages().add(systemMsg);
        session.getMessages().add(toolMsg);

        serializer.save(session);

        Session loaded = serializer.load("multi-msg");
        assertNotNull(loaded);
        assertEquals(4, loaded.getMessages().size());

        assertEquals("user", loaded.getMessages().get(0).getRole());
        assertEquals("hello", loaded.getMessages().get(0).getContent());

        assertEquals("assistant", loaded.getMessages().get(1).getRole());
        assertEquals("hi there", loaded.getMessages().get(1).getContent());

        assertEquals("system", loaded.getMessages().get(2).getRole());
        assertEquals("system prompt", loaded.getMessages().get(2).getContent());

        assertEquals("tool", loaded.getMessages().get(3).getRole());
        assertEquals("tool output", loaded.getMessages().get(3).getContent());
    }

    @Test
    void toolNamePreservedForToolMessages() {
        Session session = new Session("tool-test", System.currentTimeMillis());

        SessionMessage toolMsg = new SessionMessage("tool", "result data", 1700000000000L, 15);
        toolMsg.setToolName("write_file");
        toolMsg.setToolResult("success");

        session.getMessages().add(toolMsg);
        serializer.save(session);

        Session loaded = serializer.load("tool-test");
        assertNotNull(loaded);
        assertEquals(1, loaded.getMessages().size());

        SessionMessage loadedMsg = loaded.getMessages().get(0);
        assertEquals("tool", loadedMsg.getRole());
        assertEquals("write_file", loadedMsg.getToolName());
        assertEquals("success", loadedMsg.getToolResult());
        assertEquals(15, loadedMsg.getTokenCount());
    }

    @Test
    void listAllReturnsEmptyWhenNoFiles() {
        List<Session> sessions = serializer.listAll();
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listAllReturnsSavedSessions() {
        Session s1 = new Session("id-1", 1700000000000L);
        s1.setUpdatedAt(1700000000000L);
        Session s2 = new Session("id-2", 1700000010000L);
        s2.setUpdatedAt(1700000010000L);

        serializer.save(s1);
        serializer.save(s2);

        List<Session> sessions = serializer.listAll();
        assertEquals(2, sessions.size());
    }

    @Test
    void listAllSortedByUpdatedAtDesc() {
        Session older = new Session("older", 1700000000000L);
        older.setUpdatedAt(1700000000000L);
        Session newer = new Session("newer", 1700000010000L);
        newer.setUpdatedAt(1700000010000L);

        serializer.save(older);
        serializer.save(newer);

        List<Session> sessions = serializer.listAll();
        assertEquals(2, sessions.size());
        assertEquals("newer", sessions.get(0).getSessionId());
        assertEquals("older", sessions.get(1).getSessionId());
    }

    @Test
    void deleteExistingSession() {
        Session session = new Session("del-test", System.currentTimeMillis());
        serializer.save(session);

        assertTrue(serializer.delete("del-test"));
        assertNull(serializer.load("del-test"));
    }

    @Test
    void deleteNonExistentReturnsFalse() {
        assertFalse(serializer.delete("no-such-id"));
    }

    @Test
    void getStorageDirReturnsConfiguredDir() {
        assertEquals(tempDir, serializer.getStorageDir());
    }

    @Test
    void constructorCreatesStorageDirIfMissing() {
        File newDir = new File(tempDir, "nested/sessions");
        assertFalse(newDir.exists());

        SessionSerializer newSerializer = new SessionSerializer(newDir);
        assertTrue(newDir.exists());
        assertEquals(newDir, newSerializer.getStorageDir());
    }
}
