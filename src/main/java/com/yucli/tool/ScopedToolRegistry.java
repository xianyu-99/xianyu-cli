package com.yucli.tool;

import com.yucli.hook.HookManager;
import com.yucli.llm.LlmClient;

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
    private final ToolRegistry delegate;
    private final List<String> allowedToolMatchers;

    public ScopedToolRegistry(ToolRegistry delegate, List<String> allowedToolMatchers) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.allowedToolMatchers = sanitizeMatchers(allowedToolMatchers);
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
        return delegate.executeTool(name, argumentsJson);
    }

    @Override
    public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        if (isUnrestricted()) {
            return delegate.executeTools(invocations);
        }

        List<ToolInvocation> allowedInvocations = new ArrayList<>();
        List<Integer> allowedIndexes = new ArrayList<>();
        ToolExecutionResult[] results = new ToolExecutionResult[invocations.size()];

        for (int i = 0; i < invocations.size(); i++) {
            ToolInvocation invocation = invocations.get(i);
            if (isAllowed(invocation.name())) {
                allowedIndexes.add(i);
                allowedInvocations.add(invocation);
            } else {
                results[i] = deniedExecutionResult(invocation);
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

    private ToolExecutionResult deniedExecutionResult(ToolInvocation invocation) {
        return new ToolExecutionResult(
                invocation.id(),
                invocation.name(),
                invocation.argumentsJson(),
                deniedResult(invocation.name()),
                0,
                false
        );
    }

    private String deniedResult(String toolName) {
        String displayName = toolName == null || toolName.isBlank() ? "<unknown>" : toolName;
        return "[SubAgent Scope] 工具调用被拒绝: 未授权工具 " + displayName
                + "，允许范围: " + String.join(", ", allowedToolMatchers);
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
