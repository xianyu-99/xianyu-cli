package com.yucli.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.hook.HookManager;
import com.yucli.llm.LlmClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 将一个 ToolRegistry 收窄到指定工具 matcher 范围内。
 *
 * <p>matcher 支持精确工具名、*、以及以 * 结尾的前缀通配，如 mcp__* / browser_*。
 * allowlist 为空时保持不限制，直接委托底层 registry。</p>
 */
public class ScopedToolRegistry extends ToolRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PATH_TOOLS = List.of("read_file", "write_file", "list_dir", "create_project");

    private final ToolRegistry delegate;
    private final List<String> allowedToolMatchers;
    private final List<String> allowedPathMatchers;
    private final List<String> deniedCommandMatchers;
    private final String workingDirectory;

    public ScopedToolRegistry(ToolRegistry delegate, List<String> allowedToolMatchers) {
        this(delegate, allowedToolMatchers, List.of(), List.of(), null);
    }

    public ScopedToolRegistry(ToolRegistry delegate, List<String> allowedToolMatchers,
                              List<String> allowedPathMatchers, List<String> deniedCommandMatchers,
                              String workingDirectory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.allowedToolMatchers = sanitizeMatchers(allowedToolMatchers);
        this.allowedPathMatchers = sanitizeMatchers(allowedPathMatchers);
        this.deniedCommandMatchers = sanitizeMatchers(deniedCommandMatchers);
        this.workingDirectory = workingDirectory == null || workingDirectory.isBlank()
                ? null
                : workingDirectory.trim();
    }

    @Override
    public List<LlmClient.Tool> getToolDefinitions() {
        List<LlmClient.Tool> definitions = delegate.getToolDefinitions();
        if (isUnrestricted()) {
            return definitions;
        }
        return definitions.stream()
                .filter(tool -> isAllowed(tool.name()))
                .toList();
    }

    @Override
    public String executeTool(String name, String argumentsJson) {
        if (!isAllowed(name)) {
            return deniedResult(name);
        }
        String scopeViolation = scopeViolation(name, argumentsJson);
        if (scopeViolation != null) {
            return scopeViolation;
        }
        return delegate.executeTool(name, argumentsJson);
    }

    @Override
    public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        if (hasNoRestrictions()) {
            return delegate.executeTools(invocations);
        }

        List<ToolInvocation> allowedInvocations = new ArrayList<>();
        List<Integer> allowedIndexes = new ArrayList<>();
        ToolExecutionResult[] results = new ToolExecutionResult[invocations.size()];

        for (int i = 0; i < invocations.size(); i++) {
            ToolInvocation invocation = invocations.get(i);
            String violation = !isAllowed(invocation.name())
                    ? deniedResult(invocation.name())
                    : scopeViolation(invocation.name(), invocation.argumentsJson());
            if (violation == null) {
                allowedIndexes.add(i);
                allowedInvocations.add(invocation);
            } else {
                results[i] = deniedExecutionResult(invocation, violation);
            }
        }

        if (!allowedInvocations.isEmpty()) {
            List<ToolExecutionResult> allowedResults = delegate.executeTools(allowedInvocations);
            for (int i = 0; i < allowedResults.size(); i++) {
                results[allowedIndexes.get(i)] = allowedResults.get(i);
            }
        }

        return List.of(results);
    }

    @Override
    public boolean hasTool(String name) {
        return isAllowed(name) && delegate.hasTool(name);
    }

    @Override
    public void setProjectPath(String projectPath) {
        delegate.setProjectPath(projectPath);
    }

    @Override
    public String getProjectPath() {
        return delegate.getProjectPath();
    }

    @Override
    public HookManager getHookManager() {
        return delegate.getHookManager();
    }

    public boolean isAllowed(String toolName) {
        if (isUnrestricted()) {
            return true;
        }
        return allowedToolMatchers.stream().anyMatch(matcher -> matches(matcher, toolName));
    }

    public List<String> allowedToolMatchers() {
        return allowedToolMatchers;
    }

    public List<String> allowedPathMatchers() {
        return allowedPathMatchers;
    }

    public List<String> deniedCommandMatchers() {
        return deniedCommandMatchers;
    }

    public String workingDirectory() {
        return workingDirectory;
    }

    public static boolean matches(String matcher, String toolName) {
        if (matcher == null || matcher.isBlank() || toolName == null || toolName.isBlank()) {
            return false;
        }
        String normalizedMatcher = matcher.trim();
        if ("*".equals(normalizedMatcher)) {
            return true;
        }
        if (normalizedMatcher.endsWith("*")) {
            String prefix = normalizedMatcher.substring(0, normalizedMatcher.length() - 1);
            return toolName.startsWith(prefix);
        }
        return toolName.equals(normalizedMatcher);
    }

    private boolean isUnrestricted() {
        return allowedToolMatchers.isEmpty();
    }

    private boolean hasNoRestrictions() {
        return allowedToolMatchers.isEmpty()
                && allowedPathMatchers.isEmpty()
                && deniedCommandMatchers.isEmpty()
                && workingDirectory == null;
    }

    private ToolExecutionResult deniedExecutionResult(ToolInvocation invocation, String result) {
        return new ToolExecutionResult(
                invocation.id(),
                invocation.name(),
                invocation.argumentsJson(),
                result,
                0,
                false
        );
    }

    private String deniedResult(String toolName) {
        String displayName = toolName == null || toolName.isBlank() ? "<unknown>" : toolName;
        return "[SubAgent Scope] 工具调用被拒绝: 未授权工具 " + displayName
                + "，允许范围: " + String.join(", ", allowedToolMatchers);
    }

    private String scopeViolation(String toolName, String argumentsJson) {
        if ("execute_command".equals(toolName)) {
            return commandScopeViolation(argumentsJson);
        }
        if (PATH_TOOLS.contains(toolName)) {
            return pathScopeViolation(toolName, argumentsJson);
        }
        return null;
    }

    private String commandScopeViolation(String argumentsJson) {
        String command = parseArgument(argumentsJson, "command");
        for (String matcher : deniedCommandMatchers) {
            if (matchesCommand(matcher, command)) {
                return "[SubAgent Scope] 工具调用被拒绝: 命令匹配 deniedCommands: " + matcher;
            }
        }
        if (workingDirectory != null && !isCurrentProjectInsideWorkingDirectory()) {
            return "[SubAgent Scope] 工具调用被拒绝: 当前项目目录不在 SubAgent workingDirectory 内: "
                    + workingDirectory;
        }
        return null;
    }

    private String pathScopeViolation(String toolName, String argumentsJson) {
        if (allowedPathMatchers.isEmpty()) {
            return null;
        }
        String pathValue = "create_project".equals(toolName)
                ? parseArgument(argumentsJson, "name")
                : parseArgument(argumentsJson, "path");
        if (pathValue == null || pathValue.isBlank()) {
            return "[SubAgent Scope] 工具调用被拒绝: 缺少路径参数";
        }
        Path target = resolveProjectPath(pathValue);
        boolean allowed = allowedPathMatchers.stream()
                .map(this::resolveProjectPath)
                .anyMatch(target::startsWith);
        if (!allowed) {
            return "[SubAgent Scope] 工具调用被拒绝: 路径不在 allowedPaths 内: " + pathValue
                    + "，允许范围: " + String.join(", ", allowedPathMatchers);
        }
        return null;
    }

    private boolean isCurrentProjectInsideWorkingDirectory() {
        Path project = Path.of(delegate.getProjectPath()).toAbsolutePath().normalize();
        Path allowed = resolveProjectPath(workingDirectory);
        return project.startsWith(allowed);
    }

    private Path resolveProjectPath(String path) {
        Path candidate = Path.of(path);
        if (!candidate.isAbsolute()) {
            candidate = Path.of(delegate.getProjectPath()).resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private static boolean matchesCommand(String matcher, String command) {
        if (matcher == null || matcher.isBlank() || command == null) {
            return false;
        }
        String normalizedMatcher = matcher.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedCommand = command.toLowerCase(java.util.Locale.ROOT);
        if ("*".equals(normalizedMatcher)) {
            return true;
        }
        if (normalizedMatcher.endsWith("*")) {
            return normalizedCommand.startsWith(normalizedMatcher.substring(0, normalizedMatcher.length() - 1));
        }
        return normalizedCommand.contains(normalizedMatcher);
    }

    private static String parseArgument(String argumentsJson, String key) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(argumentsJson);
            return node.path(key).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> sanitizeMatchers(List<String> matchers) {
        if (matchers == null || matchers.isEmpty()) {
            return List.of();
        }
        return matchers.stream()
                .filter(matcher -> matcher != null && !matcher.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
