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

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<String> normalizedCommands() {
        List<String> normalized = new ArrayList<>();
        if (command != null && !command.isBlank()) {
            normalized.add(command.trim());
        }
        if (commands != null) {
            for (String value : commands) {
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
