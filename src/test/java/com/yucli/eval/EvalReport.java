package com.yucli.eval;

public class EvalReport {
    private String id;
    private boolean success;
    private long tokenUsage;
    private long durationMs;
    private String error;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public long getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(long tokenUsage) { this.tokenUsage = tokenUsage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
