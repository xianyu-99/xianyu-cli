package com.yucli.session;

public class SessionMessage {
    private String role;
    private String content;
    private long timestamp;
    private int tokenCount;
    private String toolName;
    private String toolResult;

    public SessionMessage() {}

    public SessionMessage(String role, String content, long timestamp, int tokenCount) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.tokenCount = tokenCount;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }
}
