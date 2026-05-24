package com.yucli.session;

import com.yucli.memory.ConversationMemory;
import com.yucli.memory.LongTermMemory;
import com.yucli.memory.MemoryManager;
import com.yucli.memory.MemoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @TempDir
    Path tempDir;

    private SessionManager sessionManager;
    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        memoryManager = new MemoryManager(new StubLlmClient(), 32768, 128000, new LongTermMemory(tempDir.toFile()));
        sessionManager = new SessionManager(memoryManager, tempDir.resolve("sessions").toFile());
    }

    @Test
    void createSessionGeneratesUUID() {
        Session session = sessionManager.createSession();
        assertNotNull(session.getSessionId());
        assertEquals(36, session.getSessionId().length());
        assertTrue(session.getSessionId().contains("-"));
        assertTrue(session.getCreatedAt() > 0);
        assertEquals(session, sessionManager.getCurrentSession());
    }

    @Test
    void saveAndLoadSession() {
        Session session = sessionManager.createSession();
        session.setTaskSummary("test task");
        session.setModelName("test-model");
        session.setProvider("test-provider");
        session.addMessage(new SessionMessage("user", "hello", System.currentTimeMillis(), 5));

        sessionManager.saveSession(session);

        Session loaded = sessionManager.loadSession(session.getSessionId());
        assertNotNull(loaded);
        assertEquals(session.getSessionId(), loaded.getSessionId());
        assertEquals("test task", loaded.getTaskSummary());
        assertEquals("test-model", loaded.getModelName());
        assertEquals(1, loaded.getMessages().size());
        assertEquals("hello", loaded.getMessages().get(0).getContent());
    }

    @Test
    void listSessionsSortedByUpdatedAt() throws InterruptedException {
        Session s1 = sessionManager.createSession();
        s1.setTaskSummary("first");
        sessionManager.saveSession(s1);

        Thread.sleep(50);

        Session s2 = sessionManager.createSession();
        s2.setTaskSummary("second");
        sessionManager.saveSession(s2);

        List<Session> sessions = sessionManager.listSessions();
        assertEquals(2, sessions.size());
        assertEquals("second", sessions.get(0).getTaskSummary());
        assertEquals("first", sessions.get(1).getTaskSummary());
    }

    @Test
    void deleteSession() {
        Session session = sessionManager.createSession();
        sessionManager.saveSession(session);

        assertTrue(sessionManager.deleteSession(session.getSessionId()));
        assertNull(sessionManager.loadSession(session.getSessionId()));
        assertFalse(sessionManager.deleteSession("nonexistent"));
    }

    @Test
    void exportSession() throws IOException {
        Session session = sessionManager.createSession();
        session.setTaskSummary("export test");
        sessionManager.saveSession(session);

        Path exportPath = tempDir.resolve("exported.json");
        sessionManager.exportSession(session.getSessionId(), exportPath.toString());

        assertTrue(exportPath.toFile().exists());
        Session exported = sessionManager.loadSession(session.getSessionId());
        assertNotNull(exported);
        assertEquals("export test", exported.getTaskSummary());
    }

    @Test
    void autoSaveCreatesSessionFile() {
        memoryManager.addUserMessage("hello from user");
        memoryManager.addAssistantMessage("hello from assistant");

        sessionManager.autoSave();

        Session current = sessionManager.getCurrentSession();
        assertNotNull(current);
        assertNotNull(current.getSessionId());

        Session loaded = sessionManager.loadSession(current.getSessionId());
        assertNotNull(loaded);
        assertFalse(loaded.getMessages().isEmpty());
        assertNotNull(loaded.getTaskSummary());
    }

    @Test
    void sessionMessageSerialization() {
        SessionMessage msg = new SessionMessage("user", "test content", System.currentTimeMillis(), 10);
        msg.setToolName("test_tool");
        msg.setToolResult("test result");

        Session session = sessionManager.createSession();
        session.addMessage(msg);
        sessionManager.saveSession(session);

        Session loaded = sessionManager.loadSession(session.getSessionId());
        assertNotNull(loaded);
        assertEquals(1, loaded.getMessages().size());

        SessionMessage loadedMsg = loaded.getMessages().get(0);
        assertEquals("user", loadedMsg.getRole());
        assertEquals("test content", loadedMsg.getContent());
        assertEquals("test_tool", loadedMsg.getToolName());
        assertEquals("test result", loadedMsg.getToolResult());
        assertEquals(10, loadedMsg.getTokenCount());
    }

    @Test
    void findSessionByPartialId() {
        Session session = sessionManager.createSession();
        session.setTaskSummary("partial test");
        sessionManager.saveSession(session);

        String partialId = session.getSessionId().substring(0, 8);
        Session found = sessionManager.findSessionByPartialId(partialId);
        assertNotNull(found);
        assertEquals(session.getSessionId(), found.getSessionId());
    }

    private static final class StubLlmClient extends com.yucli.llm.GLMClient {
        StubLlmClient() {
            super("test-key");
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return new ChatResponse("assistant", "stub", null, 10, 5);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            return chat(messages, tools);
        }
    }
}
