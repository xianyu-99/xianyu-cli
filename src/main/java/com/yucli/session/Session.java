package com.yucli.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class Session {
    private String sessionId;
    private long createdAt;
    private long updatedAt;
    private String modelName;
    private String provider;
    private String taskSummary;
    private long totalTokens;
    private List<SessionMessage> messages;

    public Session() {
        this.messages = new ArrayList<>();
    }

    public Session(String sessionId, long createdAt) {
        this();
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getTaskSummary() { return taskSummary; }
    public void setTaskSummary(String taskSummary) { this.taskSummary = taskSummary; }

    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    public List<SessionMessage> getMessages() { return messages; }
    public void setMessages(List<SessionMessage> messages) { this.messages = messages; }

    public void addMessage(SessionMessage message) {
        this.messages.add(message);
        this.updatedAt = System.currentTimeMillis();
        this.totalTokens += message.getTokenCount();
    }

    @JsonIgnore
    public String getShortId() {
        return sessionId != null && sessionId.length() > 8
                ? sessionId.substring(0, 8)
                : sessionId;
    }
}
