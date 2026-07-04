package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HookManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 30;

    private final Map<HookEvent, List<HookDefinition>> hooks;
    private final HookCommandExecutor executor;
    private volatile Path projectPath;

    public HookManager(Map<HookEvent, List<HookDefinition>> hooks, Path projectPath) {
        this(hooks, projectPath, new HookCommandExecutor());
    }

    HookManager(Map<HookEvent, List<HookDefinition>> hooks, Path projectPath, HookCommandExecutor executor) {
        this.hooks = copyHooks(hooks);
        this.projectPath = normalizeProjectPath(projectPath);
        this.executor = executor == null ? new HookCommandExecutor() : executor;
    }

    public static HookManager disabled() {
        return new HookManager(Map.of(), Path.of(System.getProperty("user.dir")));
    }

    public static HookManager loadDefault(Path projectDir) {
        return new HookConfigLoader().loadDefault(projectDir);
    }

    public boolean hasHooks() {
        return hooks.values().stream().anyMatch(list -> !list.isEmpty());
    }

    public void setProjectPath(Path projectPath) {
        this.projectPath = normalizeProjectPath(projectPath);
    }

    public HookDecision runPreToolUse(String toolName, String argumentsJson) {
        return runPreToolUse(toolName, argumentsJson, null);
    }

    public HookDecision runPreToolUse(String toolName, String argumentsJson, String toolCallId) {
        return runBlockingHooks(HookEvent.PRE_TOOL_USE, toolName,
                createPayload(HookEvent.PRE_TOOL_USE, toolName, argumentsJson, toolCallId, null, null));
    }

    public void runPostToolUse(String toolName, String argumentsJson, String result, long elapsedMillis) {
        runPostToolUse(toolName, argumentsJson, null, result, elapsedMillis);
    }

    public void runPostToolUse(String toolName, String argumentsJson, String toolCallId,
                               String result, long elapsedMillis) {
        ObjectNode payload = createPayload(HookEvent.POST_TOOL_USE, toolName, argumentsJson, toolCallId,
                result, elapsedMillis);
        for (HookDefinition definition : matchingHooks(HookEvent.POST_TOOL_USE, toolName)) {
            runDefinition(definition, payload);
        }
    }

    private HookDecision runBlockingHooks(HookEvent event, String toolName, ObjectNode payload) {
        for (HookDefinition definition : matchingHooks(event, toolName)) {
            for (String command : definition.normalizedCommands()) {
                HookCommandExecutor.HookCommandResult result = executor.execute(
                        command,
                        payload.toString(),
                        projectPath,
                        normalizeTimeout(definition.getTimeoutSeconds()));
                if (!result.success()) {
                    return HookDecision.block(result.failureMessage());
                }
            }
        }
        return HookDecision.allow();
    }

    private void runDefinition(HookDefinition definition, ObjectNode payload) {
        for (String command : definition.normalizedCommands()) {
            HookCommandExecutor.HookCommandResult result = executor.execute(
                    command,
                    payload.toString(),
                    projectPath,
                    normalizeTimeout(definition.getTimeoutSeconds()));
            if (!result.success()) {
                System.err.println("[Hook] PostToolUse hook failed: " + result.failureMessage());
            }
        }
    }

    private List<HookDefinition> matchingHooks(HookEvent event, String toolName) {
        List<HookDefinition> definitions = hooks.getOrDefault(event, List.of());
        if (definitions.isEmpty()) {
            return List.of();
        }
        List<HookDefinition> matched = new ArrayList<>();
        for (HookDefinition definition : definitions) {
            if (definition != null && definition.matches(toolName) && !definition.normalizedCommands().isEmpty()) {
                matched.add(definition);
            }
        }
        return matched;
    }

    private ObjectNode createPayload(HookEvent event, String toolName, String argumentsJson, String toolCallId,
                                     String result, Long elapsedMillis) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("event", event.configName());
        payload.put("tool_name", toolName);
        payload.put("project_path", projectPath.toString());
        payload.put("timestamp", Instant.now().toString());
        if (toolCallId != null && !toolCallId.isBlank()) {
            payload.put("tool_call_id", toolCallId);
        }
        payload.put("arguments_raw", argumentsJson == null ? "{}" : argumentsJson);
        payload.set("arguments", parseArguments(argumentsJson));
        if (result != null) {
            payload.put("result", result);
        }
        if (elapsedMillis != null) {
            payload.put("elapsed_ms", elapsedMillis);
        }
        return payload;
    }

    private JsonNode parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            ObjectNode fallback = MAPPER.createObjectNode();
            fallback.put("raw", argumentsJson);
            return fallback;
        }
    }

    private static int normalizeTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS);
    }

    private static Map<HookEvent, List<HookDefinition>> copyHooks(Map<HookEvent, List<HookDefinition>> source) {
        Map<HookEvent, List<HookDefinition>> copy = new EnumMap<>(HookEvent.class);
        if (source == null) {
            return copy;
        }
        for (Map.Entry<HookEvent, List<HookDefinition>> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return copy;
    }

    private static Path normalizeProjectPath(Path projectPath) {
        Path path = projectPath == null ? Path.of(System.getProperty("user.dir")) : projectPath;
        return path.toAbsolutePath().normalize();
    }
}
