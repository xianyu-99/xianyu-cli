package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    public HookStatus status() {
        Map<String, Integer> hookCounts = new LinkedHashMap<>();
        List<HookSummary> summaries = new ArrayList<>();
        for (HookEvent event : HookEvent.values()) {
            List<HookDefinition> definitions = hooks.getOrDefault(event, List.of());
            hookCounts.put(event.configName(), definitions.size());
            for (HookDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                summaries.add(new HookSummary(
                        event.configName(),
                        definition.getMatcher() == null || definition.getMatcher().isBlank()
                                ? "*"
                                : definition.getMatcher().trim(),
                        definition.normalizedCommands(),
                        normalizeTimeout(definition.getTimeoutSeconds())));
            }
        }
        return new HookStatus(hasHooks(), projectPath.toString(), summaries.size(), hookCounts, summaries);
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
        HookDecision finalDecision = HookDecision.allow();
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
                HookDecision outputDecision = parseStructuredDecision(result.output());
                if (!outputDecision.allowed()) {
                    return outputDecision;
                }
                if (outputDecision.modified()) {
                    updateArguments(payload, outputDecision.arguments());
                    finalDecision = outputDecision;
                }
            }
        }
        return finalDecision;
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

    private HookDecision parseStructuredDecision(String output) {
        JsonNode decisionNode = findStructuredDecision(output);
        if (decisionNode == null || !decisionNode.has("decision")) {
            return HookDecision.allow();
        }

        String decision = decisionNode.path("decision").asText("").trim().toLowerCase(Locale.ROOT);
        String reason = textOrNull(decisionNode.get("reason"));
        return switch (decision) {
            case "allow" -> HookDecision.allow();
            case "deny" -> HookDecision.block(reason);
            case "modify" -> {
                JsonNode arguments = decisionNode.get("arguments");
                if (arguments == null || !arguments.isObject()) {
                    yield HookDecision.block("hook modify decision requires object arguments");
                }
                yield HookDecision.modify(arguments.deepCopy(), reason);
            }
            default -> HookDecision.block("unsupported hook decision: " + decision);
        };
    }

    private JsonNode findStructuredDecision(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String trimmed = output.trim();
        JsonNode direct = parseObjectNode(trimmed);
        if (direct != null) {
            return direct;
        }

        String[] lines = trimmed.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            JsonNode fromLine = parseObjectNode(lines[i].trim());
            if (fromLine != null) {
                return fromLine;
            }
        }
        return null;
    }

    private JsonNode parseObjectNode(String value) {
        if (value == null || value.isBlank() || !value.startsWith("{") || !value.endsWith("}")) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateArguments(ObjectNode payload, JsonNode arguments) {
        JsonNode copy = arguments.deepCopy();
        payload.set("arguments", copy);
        try {
            payload.put("arguments_raw", MAPPER.writeValueAsString(copy));
        } catch (Exception e) {
            payload.put("arguments_raw", "{}");
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
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

    public record HookStatus(boolean enabled, String projectPath, int totalHooks,
                             Map<String, Integer> hookCounts, List<HookSummary> hooks) {
        public HookStatus {
            hookCounts = hookCounts == null ? Map.of() : Map.copyOf(hookCounts);
            hooks = hooks == null ? List.of() : List.copyOf(hooks);
        }
    }

    public record HookSummary(String event, String matcher, List<String> commands, int timeoutSeconds) {
        public HookSummary {
            matcher = matcher == null || matcher.isBlank() ? "*" : matcher;
            commands = commands == null ? List.of() : List.copyOf(commands);
        }
    }
}
