package com.yucli.tool;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.yucli.hook.HookManager;
import com.yucli.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedToolRegistryTest {

    @Test
    void shouldFilterToolDefinitionsByExactAndPrefixMatchers() {
        FakeToolRegistry delegate = new FakeToolRegistry();
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate, List.of("read_file", "mcp__*"));

        List<String> toolNames = scoped.getToolDefinitions().stream()
                .map(LlmClient.Tool::name)
                .toList();

        assertEquals(List.of("read_file", "mcp__demo__echo"), toolNames);
    }

    @Test
    void shouldRejectUnauthorizedToolWithoutTouchingDelegateRegistry() {
        FakeToolRegistry delegate = new FakeToolRegistry();
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate, List.of("read_file"));

        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "execute_command", "{\"command\":\"echo denied\"}")
        ));

        assertEquals(0, delegate.executeToolsCalls.get());
        assertEquals("call_1", results.get(0).id());
        assertTrue(results.get(0).result().contains("工具调用被拒绝"));
        assertTrue(results.get(0).result().contains("execute_command"));
    }

    @Test
    void shouldKeepEmptyWhitelistUnrestricted() {
        FakeToolRegistry delegate = new FakeToolRegistry();
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate, List.of());

        List<String> toolNames = scoped.getToolDefinitions().stream()
                .map(LlmClient.Tool::name)
                .toList();
        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "execute_command", "{}")
        ));

        assertEquals(delegate.getToolDefinitions().stream().map(LlmClient.Tool::name).toList(), toolNames);
        assertEquals(1, delegate.executeToolsCalls.get());
        assertEquals("delegate-execute_command", results.get(0).result());
    }

    @Test
    void shouldAllowPrefixWildcardForDefinitionsAndExecution() {
        FakeToolRegistry delegate = new FakeToolRegistry();
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate, List.of("browser_*"));

        List<String> toolNames = scoped.getToolDefinitions().stream()
                .map(LlmClient.Tool::name)
                .toList();
        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "browser_click", "{}")
        ));

        assertEquals(List.of("browser_click"), toolNames);
        assertEquals(1, delegate.executeToolsCalls.get());
        assertEquals("delegate-browser_click", results.get(0).result());
        assertTrue(ScopedToolRegistry.matches("browser_*", "browser_click"));
        assertFalse(ScopedToolRegistry.matches("browser_*", "mcp__demo__echo"));
    }

    @Test
    void shouldRejectPathOutsideAllowedPathsWithoutTouchingDelegate(@TempDir Path tempDir) {
        FakeToolRegistry delegate = new FakeToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        ScopedToolRegistry scoped = new ScopedToolRegistry(
                delegate,
                List.of(),
                List.of("src"),
                List.of(),
                null);

        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "read_file", "{\"path\":\"README.md\"}")
        ));

        assertEquals(0, delegate.executeToolsCalls.get());
        assertTrue(results.get(0).result().contains("allowedPaths"));
    }

    @Test
    void shouldAllowPathInsideAllowedPaths(@TempDir Path tempDir) {
        FakeToolRegistry delegate = new FakeToolRegistry();
        delegate.setProjectPath(tempDir.toString());
        ScopedToolRegistry scoped = new ScopedToolRegistry(
                delegate,
                List.of(),
                List.of("src"),
                List.of(),
                null);

        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "read_file", "{\"path\":\"src/Main.java\"}")
        ));

        assertEquals(1, delegate.executeToolsCalls.get());
        assertEquals("delegate-read_file", results.get(0).result());
    }

    @Test
    void shouldRejectDeniedCommandWithoutTouchingDelegate() {
        FakeToolRegistry delegate = new FakeToolRegistry();
        ScopedToolRegistry scoped = new ScopedToolRegistry(
                delegate,
                List.of(),
                List.of(),
                List.of("git push"),
                null);

        List<ToolRegistry.ToolExecutionResult> results = scoped.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "execute_command",
                        "{\"command\":\"git push origin main\"}")
        ));

        assertEquals(0, delegate.executeToolsCalls.get());
        assertTrue(results.get(0).result().contains("deniedCommands"));
    }

    @Test
    void shouldDelegateHookManagerToUnderlyingRegistry() {
        HookManager hookManager = new HookManager(Map.of(), Path.of("."));
        ToolRegistry delegate = new ToolRegistry(hookManager);
        ScopedToolRegistry scoped = new ScopedToolRegistry(delegate, List.of("read_file"));

        assertEquals(hookManager, scoped.getHookManager());
    }

    private static final class FakeToolRegistry extends ToolRegistry {
        private final AtomicInteger executeToolsCalls = new AtomicInteger();
        private final List<LlmClient.Tool> toolDefinitions = List.of(
                tool("read_file"),
                tool("execute_command"),
                tool("mcp__demo__echo"),
                tool("browser_click")
        );

        @Override
        public List<LlmClient.Tool> getToolDefinitions() {
            return toolDefinitions;
        }

        @Override
        public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
            executeToolsCalls.incrementAndGet();
            return invocations.stream()
                    .map(invocation -> new ToolExecutionResult(
                            invocation.id(),
                            invocation.name(),
                            invocation.argumentsJson(),
                            "delegate-" + invocation.name(),
                            1,
                            false
                    ))
                    .toList();
        }

        @Override
        public boolean hasTool(String name) {
            return toolDefinitions.stream().anyMatch(tool -> tool.name().equals(name));
        }

        private static LlmClient.Tool tool(String name) {
            return new LlmClient.Tool(name, name + " description", JsonNodeFactory.instance.objectNode());
        }
    }
}
