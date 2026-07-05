package com.yucli.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HookDefinition {
    private String matcher = "*";
    private String command;
    private List<String> commands = new ArrayList<>();
    private String url;
    private List<String> urls = new ArrayList<>();
    private String prompt;
    private List<String> prompts = new ArrayList<>();
    private Map<String, String> headers = new LinkedHashMap<>();
    private String authToken;
    private String signatureSecret;
    private Integer retryCount;
    private Long retryBackoffMillis;
    private Boolean async;
    private Integer timeoutSeconds;

    public HookDefinition() {
    }

    public HookDefinition(String matcher, List<String> commands, Integer timeoutSeconds) {
        this.matcher = matcher;
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getMatcher() {
        return matcher;
    }

    public void setMatcher(String matcher) {
        this.matcher = matcher;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands == null ? new ArrayList<>() : commands;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls == null ? new ArrayList<>() : urls;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getPrompts() {
        return prompts;
    }

    public void setPrompts(List<String> prompts) {
        this.prompts = prompts == null ? new ArrayList<>() : prompts;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = trimToNull(authToken);
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = trimToNull(signatureSecret);
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(Long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<String> normalizedCommands() {
        return normalize(command, commands);
    }

    public List<String> normalizedUrls() {
        return normalize(url, urls);
    }

    public List<String> normalizedPrompts() {
        return normalize(prompt, prompts);
    }

    public Map<String, String> normalizedHeaders() {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    normalized.put(key.trim(), value.trim());
                }
            });
        }
        if (authToken != null && !authToken.isBlank()
                && normalized.keySet().stream().noneMatch("authorization"::equalsIgnoreCase)) {
            normalized.put("Authorization", "Bearer " + authToken.trim());
        }
        return normalized;
    }

    public int normalizedRetryCount() {
        if (retryCount == null || retryCount <= 0) {
            return 0;
        }
        return Math.min(retryCount, 3);
    }

    public long normalizedRetryBackoffMillis() {
        if (retryBackoffMillis == null || retryBackoffMillis <= 0) {
            return 200L;
        }
        return Math.min(retryBackoffMillis, 2_000L);
    }

    public boolean asyncEnabled() {
        return Boolean.TRUE.equals(async);
    }

    public boolean hasExecutors() {
        return !normalizedCommands().isEmpty()
                || !normalizedUrls().isEmpty()
                || !normalizedPrompts().isEmpty();
    }

    private static List<String> normalize(String singleValue, List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (singleValue != null && !singleValue.isBlank()) {
            normalized.add(singleValue.trim());
        }
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value.trim());
                }
            }
        }
        return normalized;
    }

    String command() {
        List<String> normalized = normalizedCommands();
        return normalized.isEmpty() ? "" : normalized.get(0);
    }

    int timeoutSeconds() {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return 10;
        }
        return timeoutSeconds;
    }

    public boolean matches(String toolName) {
        String pattern = matcher == null || matcher.isBlank() ? "*" : matcher.trim();
        if ("*".equals(pattern)) {
            return true;
        }
        if (toolName == null) {
            return false;
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1).toLowerCase(Locale.ROOT);
            return toolName.toLowerCase(Locale.ROOT).startsWith(prefix);
        }
        return pattern.equalsIgnoreCase(toolName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
