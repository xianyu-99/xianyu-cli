package com.yucli.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HookDefinition {
    private String matcher = "*";
    private String command;
    private List<String> commands = new ArrayList<>();
    private String url;
    private List<String> urls = new ArrayList<>();
    private String prompt;
    private List<String> prompts = new ArrayList<>();
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
}
