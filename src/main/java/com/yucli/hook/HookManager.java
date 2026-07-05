package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yucli.llm.LlmClient;

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
    private final HookCommandExecutor commandExecutor;
    private final HookHttpClient httpClient;
    private final HookPromptExecutor promptExecutor;
    private final HookAsyncExecutor asyncExecutor;
    private volatile Path projectPath;

    public HookManager(Map<HookEvent, List<HookDefinition>> hooks, Path projectPath) {
        this(hooks, projectPath, new HookCommandExecutor());
    }

    HookManager(Map<HookEvent, List<HookDefinition>> hooks, Path projectPath, HookCommandExecutor executor) {
        this(hooks, projectPath, executor, new HookHttpClient(), null);
    }

    HookManager(Map<HookEvent, List<HookDefinition>> hooks, Path projectPath, HookCommandExecutor executor,
                HookHttpClient httpClient, LlmClient llmClient) {
        this.hooks = copyHooks(hooks);
        this.projectPath = normalizeProjectPath(projectPath);
        this.commandExecutor = executor == null ? new HookCommandExecutor() : executor;
        this.httpClient = httpClient == null ? new HookHttpClient() : httpClient;
        this.promptExecutor = new HookPromptExecutor(llmClient);
        this.asyncExecutor = new HookAsyncExecutor();
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
        Map<String, Integer> executorCounts = new LinkedHashMap<>();
        executorCounts.put("command", 0);
        executorCounts.put("http", 0);
        executorCounts.put("prompt", 0);
        List<HookSummary> summaries = new ArrayList<>();
        for (HookEvent event : HookEvent.values()) {
            List<HookDefinition> definitions = hooks.getOrDefault(event, List.of());
            hookCounts.put(event.configName(), definitions.size());
            for (HookDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                List<String> commands = definition.normalizedCommands();
                List<String> urls = definition.normalizedUrls();
                List<String> prompts = definition.normalizedPrompts();
                executorCounts.computeIfPresent("command", (ignored, count) -> count + commands.size());
                executorCounts.computeIfPresent("http", (ignored, count) -> count + urls.size());
                executorCounts.computeIfPresent("prompt", (ignored, count) -> count + prompts.size());
                summaries.add(new HookSummary(
                        event.configName(),
                        definition.getMatcher() == null || definition.getMatcher().isBlank()
                                ? "*"
                                : definition.getMatcher().trim(),
                        commands,
                        urls,
                        prompts,
                        definition.asyncEnabled(),
                        normalizeTimeout(definition.getTimeoutSeconds())));
            }
        }
        return new HookStatus(hasHooks(), projectPath.toString(), summaries.size(),
                hookCounts, executorCounts, summaries);
    }

    public void setProjectPath(Path projectPath) {
        this.projectPath = normalizeProjectPath(projectPath);
    }

    public void setLlmClient(LlmClient llmClient) {
        this.promptExecutor.setLlmClient(llmClient);
    }

    public boolean awaitAsyncHooks(long timeoutMillis) {
        return asyncExecutor.awaitIdle(timeoutMillis);
    }

    public void shutdown() {
        asyncExecutor.shutdown();
    }

    public HookDecision runPreToolUse(String toolName, String argumentsJson) {
        return runPreToolUse(toolName, argumentsJson, null);
    }

    public HookDecision runPreToolUse(String toolName, String argumentsJson, String toolCallId) {
        return runBlockingHooks(HookEvent.PRE_TOOL_USE, toolName,
                createToolPayload(HookEvent.PRE_TOOL_USE, toolName, argumentsJson, toolCallId, null, null));
    }

    public void runPostToolUse(String toolName, String argumentsJson, String result, long elapsedMillis) {
        runPostToolUse(toolName, argumentsJson, null, result, elapsedMillis);
    }

    public void runPostToolUse(String toolName, String argumentsJson, String toolCallId,
                               String result, long elapsedMillis) {
        ObjectNode payload = createToolPayload(HookEvent.POST_TOOL_USE, toolName, argumentsJson, toolCallId,
                result, elapsedMillis);
        for (HookDefinition definition : matchingHooks(HookEvent.POST_TOOL_USE, toolName)) {
            runNonBlockingDefinition(HookEvent.POST_TOOL_USE, definition, payload);
        }
    }

    public HookDecision runUserPromptSubmit(String prompt, String mode, String source, String commandType) {
        String target = safeText(mode, "react");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("prompt", safeText(prompt, ""));
        arguments.put("mode", target);
        arguments.put("source", safeText(source, "cli"));
        arguments.put("command_type", safeText(commandType, ""));
        return runBlockingHooks(HookEvent.USER_PROMPT_SUBMIT, target,
                createLifecyclePayload(HookEvent.USER_PROMPT_SUBMIT, target, arguments));
    }

    public void runAgentStart(String agentType, String input, String source) {
        String target = safeText(agentType, "agent");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("agent_type", target);
        arguments.put("input", safeText(input, ""));
        arguments.put("source", safeText(source, ""));
        runNonBlockingHooks(HookEvent.AGENT_START, target,
                createLifecyclePayload(HookEvent.AGENT_START, target, arguments));
    }

    public void runAgentFinish(String agentType, String input, String result, long elapsedMillis,
                               String error, boolean cancelled) {
        String target = safeText(agentType, "agent");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("agent_type", target);
        arguments.put("input", safeText(input, ""));
        arguments.put("result", safeText(result, ""));
        arguments.put("elapsed_ms", elapsedMillis);
        arguments.put("error", safeText(error, ""));
        arguments.put("cancelled", cancelled);
        runNonBlockingHooks(HookEvent.AGENT_FINISH, target,
                createLifecyclePayload(HookEvent.AGENT_FINISH, target, arguments));
    }

    public void runSubAgentStart(String agentName, String agentRole, String fromAgent,
                                 String taskType, String taskContent) {
        String target = safeText(agentRole, "subagent");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("agent_name", safeText(agentName, ""));
        arguments.put("agent_role", target);
        arguments.put("from_agent", safeText(fromAgent, ""));
        arguments.put("task_type", safeText(taskType, ""));
        arguments.put("task_content", safeText(taskContent, ""));
        runNonBlockingHooks(HookEvent.SUB_AGENT_START, target,
                createLifecyclePayload(HookEvent.SUB_AGENT_START, target, arguments));
    }

    public void runSubAgentFinish(String agentName, String agentRole, String resultType,
                                  String content, long elapsedMillis) {
        String target = safeText(agentRole, "subagent");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("agent_name", safeText(agentName, ""));
        arguments.put("agent_role", target);
        arguments.put("result_type", safeText(resultType, ""));
        arguments.put("content", safeText(content, ""));
        arguments.put("elapsed_ms", elapsedMillis);
        runNonBlockingHooks(HookEvent.SUB_AGENT_FINISH, target,
                createLifecyclePayload(HookEvent.SUB_AGENT_FINISH, target, arguments));
    }

    public void runPreCompact(String memoryScope, int entryCount, int tokenCount,
                              double usageRatio, String contextMode, String trigger) {
        String target = safeText(memoryScope, "memory");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("memory_scope", target);
        arguments.put("entry_count", entryCount);
        arguments.put("token_count", tokenCount);
        arguments.put("usage_ratio", usageRatio);
        arguments.put("context_mode", safeText(contextMode, ""));
        arguments.put("trigger", safeText(trigger, ""));
        runNonBlockingHooks(HookEvent.PRE_COMPACT, target,
                createLifecyclePayload(HookEvent.PRE_COMPACT, target, arguments));
    }

    private void runNonBlockingHooks(HookEvent event, String hookTarget, ObjectNode payload) {
        for (HookDefinition definition : matchingHooks(event, hookTarget)) {
            if (definition.asyncEnabled()) {
                ObjectNode payloadCopy = payload.deepCopy();
                asyncExecutor.submit(event.configName(),
                        () -> runNonBlockingDefinition(event, definition, payloadCopy));
            } else {
                runNonBlockingDefinition(event, definition, payload);
            }
        }
    }

    private HookDecision runBlockingHooks(HookEvent event, String hookTarget, ObjectNode payload) {
        HookDecision finalDecision = HookDecision.allow();
        for (HookDefinition definition : matchingHooks(event, hookTarget)) {
            for (HookAction action : actionsFor(definition)) {
                HookActionResult result = runAction(action, payload, normalizeTimeout(definition.getTimeoutSeconds()));
                if (!result.success()) {
                    return HookDecision.block(HookRedactor.redact(result.failureMessage()));
                }
                HookDecision outputDecision = parseStructuredDecision(result.output(), action.requiresDecision());
                if (!outputDecision.allowed()) {
                    return HookDecision.block(HookRedactor.redact(outputDecision.reason()));
                }
                if (outputDecision.modified()) {
                    updateArguments(payload, outputDecision.arguments());
                    finalDecision = outputDecision;
                }
            }
        }
        return finalDecision;
    }

    private void runNonBlockingDefinition(HookEvent event, HookDefinition definition, ObjectNode payload) {
        for (HookAction action : actionsFor(definition)) {
            HookActionResult result = runAction(action, payload, normalizeTimeout(definition.getTimeoutSeconds()));
            if (!result.success()) {
                System.err.println("[Hook] " + event.configName() + " hook failed: "
                        + HookRedactor.redact(result.failureMessage()));
                continue;
            }
            HookDecision outputDecision = parseStructuredDecision(result.output(), action.requiresDecision());
            if (!outputDecision.allowed()) {
                System.err.println("[Hook] " + event.configName() + " hook decision ignored: "
                        + HookRedactor.redact(outputDecision.reason()));
            } else if (outputDecision.modified()) {
                System.err.println("[Hook] " + event.configName() + " modify decision ignored");
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
            if (definition != null && definition.matches(toolName) && definition.hasExecutors()) {
                matched.add(definition);
            }
        }
        return matched;
    }

    private List<HookAction> actionsFor(HookDefinition definition) {
        List<HookAction> actions = new ArrayList<>();
        for (String command : definition.normalizedCommands()) {
            actions.add(new HookAction(HookActionType.COMMAND, command, definition));
        }
        for (String url : definition.normalizedUrls()) {
            actions.add(new HookAction(HookActionType.HTTP, url, definition));
        }
        for (String prompt : definition.normalizedPrompts()) {
            actions.add(new HookAction(HookActionType.PROMPT, prompt, definition));
        }
        return actions;
    }

    private HookActionResult runAction(HookAction action, ObjectNode payload, int timeoutSeconds) {
        String payloadJson = payload.toString();
        return switch (action.type()) {
            case COMMAND -> {
                HookCommandExecutor.HookCommandResult result = commandExecutor.execute(
                        action.target(), payloadJson, projectPath, timeoutSeconds);
                yield result.success()
                        ? HookActionResult.success(result.output())
                        : HookActionResult.failure(result.failureMessage());
            }
            case HTTP -> {
                HookDefinition definition = action.definition();
                HookHttpClient.HookHttpResult result = hasHttpOptions(definition)
                        ? httpClient.post(
                                action.target(),
                                payloadJson,
                                timeoutSeconds,
                                definition.normalizedHeaders(),
                                definition.getSignatureSecret(),
                                definition.normalizedRetryCount(),
                                definition.normalizedRetryBackoffMillis())
                        : httpClient.post(action.target(), payloadJson, timeoutSeconds);
                yield result.success()
                        ? HookActionResult.success(result.body())
                        : HookActionResult.failure(result.failureMessage());
            }
            case PROMPT -> {
                HookPromptExecutor.HookPromptResult result = promptExecutor.execute(
                        action.target(), payloadJson, timeoutSeconds);
                yield result.success()
                        ? HookActionResult.success(result.output())
                        : HookActionResult.failure(result.failureMessage());
            }
        };
    }

    private ObjectNode createToolPayload(HookEvent event, String toolName, String argumentsJson, String toolCallId,
                                         String result, Long elapsedMillis) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("event", event.configName());
        payload.put("hook_target", toolName == null ? "" : toolName);
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

    private ObjectNode createLifecyclePayload(HookEvent event, String hookTarget, Map<String, Object> arguments) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("event", event.configName());
        payload.put("hook_target", safeText(hookTarget, ""));
        payload.put("project_path", projectPath.toString());
        payload.put("timestamp", Instant.now().toString());

        ObjectNode argumentNode = MAPPER.valueToTree(arguments == null ? Map.of() : arguments);
        payload.set("arguments", argumentNode);
        payload.setAll(argumentNode);
        try {
            payload.put("arguments_raw", MAPPER.writeValueAsString(argumentNode));
        } catch (Exception e) {
            payload.put("arguments_raw", "{}");
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
        return parseStructuredDecision(output, false);
    }

    private HookDecision parseStructuredDecision(String output, boolean requireDecision) {
        JsonNode decisionNode = findStructuredDecision(output);
        if (decisionNode == null || !decisionNode.has("decision")) {
            if (requireDecision) {
                return HookDecision.block("hook prompt returned no structured decision JSON");
            }
            return HookDecision.allow();
        }

        String decision = decisionNode.path("decision").asText("").trim().toLowerCase(Locale.ROOT);
        String reason = textOrNull(decisionNode.get("reason"));
        return switch (decision) {
            case "allow" -> HookDecision.allow();
            case "deny" -> HookDecision.block(HookRedactor.redact(reason));
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
        if (copy.isObject()) {
            copy.fields().forEachRemaining(entry -> {
                if (!isReservedPayloadField(entry.getKey())) {
                    payload.set(entry.getKey(), entry.getValue().deepCopy());
                }
            });
        }
        try {
            payload.put("arguments_raw", MAPPER.writeValueAsString(copy));
        } catch (Exception e) {
            payload.put("arguments_raw", "{}");
        }
    }

    private static boolean isReservedPayloadField(String key) {
        return switch (key) {
            case "event", "hook_target", "project_path", "timestamp", "arguments", "arguments_raw" -> true;
            default -> false;
        };
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static int normalizeTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS);
    }

    private static boolean hasHttpOptions(HookDefinition definition) {
        return definition != null
                && (!definition.normalizedHeaders().isEmpty()
                || definition.getSignatureSecret() != null
                || definition.normalizedRetryCount() > 0);
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

    private enum HookActionType {
        COMMAND,
        HTTP,
        PROMPT
    }

    private record HookAction(HookActionType type, String target, HookDefinition definition) {
        private boolean requiresDecision() {
            return type == HookActionType.PROMPT;
        }
    }

    private record HookActionResult(boolean success, String output, String failureMessage) {
        private static HookActionResult success(String output) {
            return new HookActionResult(true, output, null);
        }

        private static HookActionResult failure(String failureMessage) {
            return new HookActionResult(false, "", failureMessage);
        }
    }

    public record HookStatus(boolean enabled, String projectPath, int totalHooks,
                             Map<String, Integer> hookCounts, Map<String, Integer> executorCounts,
                             List<HookSummary> hooks) {
        public HookStatus(boolean enabled, String projectPath, int totalHooks,
                          Map<String, Integer> hookCounts, List<HookSummary> hooks) {
            this(enabled, projectPath, totalHooks, hookCounts, Map.of(), hooks);
        }

        public HookStatus {
            hookCounts = hookCounts == null ? Map.of() : Map.copyOf(hookCounts);
            executorCounts = executorCounts == null ? Map.of() : Map.copyOf(executorCounts);
            hooks = hooks == null ? List.of() : List.copyOf(hooks);
        }
    }

    public record HookSummary(String event, String matcher, List<String> commands,
                              List<String> urls, List<String> prompts,
                              boolean async, int timeoutSeconds) {
        public HookSummary(String event, String matcher, List<String> commands, int timeoutSeconds) {
            this(event, matcher, commands, List.of(), List.of(), false, timeoutSeconds);
        }

        public HookSummary {
            matcher = matcher == null || matcher.isBlank() ? "*" : matcher;
            commands = commands == null ? List.of() : List.copyOf(commands);
            urls = urls == null ? List.of() : List.copyOf(urls);
            prompts = prompts == null ? List.of() : List.copyOf(prompts);
        }

        public int commandCount() {
            return commands.size();
        }

        public int httpCount() {
            return urls.size();
        }

        public int promptCount() {
            return prompts.size();
        }
    }
}
