package com.yucli.session;

import com.yucli.memory.MemoryManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

public class SessionManager {
    private final SessionSerializer serializer;
    private final MemoryManager memoryManager;
    private Session currentSession;

    public SessionManager(MemoryManager memoryManager) {
        this.serializer = new SessionSerializer();
        this.memoryManager = memoryManager;
    }

    public SessionManager(MemoryManager memoryManager, File storageDir) {
        this.serializer = new SessionSerializer(storageDir);
        this.memoryManager = memoryManager;
    }

    public Session createSession() {
        Session session = new Session(UUID.randomUUID().toString(), System.currentTimeMillis());
        currentSession = session;
        return session;
    }

    public void saveSession(Session session) {
        serializer.save(session);
    }

    public Session loadSession(String sessionId) {
        return serializer.load(sessionId);
    }

    public List<Session> listSessions() {
        return serializer.listAll();
    }

    public boolean deleteSession(String sessionId) {
        return serializer.delete(sessionId);
    }

    public void exportSession(String sessionId, String path) throws IOException {
        Session session = serializer.load(sessionId);
        if (session == null) {
            throw new IOException("会话不存在: " + sessionId);
        }
        File source = new File(serializer.getStorageDir(), sessionId + ".json");
        File target = new File(path);
        if (target.isDirectory()) {
            target = new File(target, sessionId + ".json");
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void autoSave() {
        if (currentSession == null) {
            currentSession = createSession();
        }
        List<SessionMessage> messages = memoryManager.exportToSession();
        currentSession.setMessages(messages);
        currentSession.setUpdatedAt(System.currentTimeMillis());
        if (currentSession.getTaskSummary() == null || currentSession.getTaskSummary().isEmpty()) {
            currentSession.getMessages().stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .findFirst()
                    .ifPresent(first -> {
                        String content = first.getContent();
                        currentSession.setTaskSummary(content.length() > 100
                                ? content.substring(0, 100) + "..."
                                : content);
                    });
        }
        serializer.save(currentSession);
    }

    public void saveOnExit() {
        try {
            autoSave();
        } catch (Exception e) {
            System.err.println("退出时保存会话失败: " + e.getMessage());
        }
    }

    public Session findMostRecentUnclosed() {
        List<Session> sessions = serializer.listAll();
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public Session findSessionByPartialId(String partialId) {
        List<Session> sessions = serializer.listAll();
        return sessions.stream()
                .filter(s -> s.getSessionId().startsWith(partialId))
                .findFirst()
                .orElse(null);
    }

    public Session getCurrentSession() {
        return currentSession;
    }

    public void setCurrentSession(Session session) {
        this.currentSession = session;
    }

    public SessionSerializer getSerializer() {
        return serializer;
    }
}
