package com.yucli.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void shouldRunCommandInProjectDirectory() throws Exception {
        // 手动创建目录避免 @TempDir 在 Windows 上的清理问题
        Path tempDir = Files.createTempDirectory("YuCLI-test-");
        try {
            ToolRegistry registry = new ToolRegistry();
            registry.setProjectPath(tempDir.toString());

            String command = isWindows() ? "cd" : "pwd";
            String result = registry.executeTool("execute_command", "{\"command\":\"" + command + "\"}");

            assertTrue(result.contains(tempDir.getFileName().toString()),
                    "命令输出应包含目录名: " + result);
        } finally {
            // 静默清理，忽略 Windows 上可能无法删除的情况
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldRejectBroadFilesystemScan() {
        ToolRegistry registry = new ToolRegistry();

        String result = registry.executeTool("execute_command", "{\"command\":\"find / -name \\\"pom.xml\\\" -type f | head -20\"}");

        assertTrue(result.contains("策略拒绝"));
    }

    @Test
    void shouldRejectUnknownProjectTypeWithoutCreatingDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("YuCLI-test-");
        try {
            ToolRegistry registry = new ToolRegistry();
            registry.setProjectPath(tempDir.toString());

            String result = registry.executeTool("create_project",
                    "{\"name\":\"bad-project\",\"type\":\"ruby\"}");

            assertTrue(result.contains("不支持的项目类型"), "实际输出: " + result);
            assertFalse(Files.exists(tempDir.resolve("bad-project")));
        } finally {
            try { Files.deleteIfExists(tempDir.resolve("bad-project")); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldNormalizeSearchCodeTopKWithinSupportedRange() {
        assertEquals(5, ToolRegistry.normalizeSearchTopK(null));
        assertEquals(5, ToolRegistry.normalizeSearchTopK(""));
        assertEquals(5, ToolRegistry.normalizeSearchTopK("abc"));
        assertEquals(5, ToolRegistry.normalizeSearchTopK("0"));
        assertEquals(5, ToolRegistry.normalizeSearchTopK("-3"));
        assertEquals(7, ToolRegistry.normalizeSearchTopK("7"));
        assertEquals(20, ToolRegistry.normalizeSearchTopK("100"));
    }

    @Test
    void shouldTimeoutLongRunningCommandWithoutHanging() throws Exception {
        // 手动创建目录避免 @TempDir 在 Windows 上的清理问题
        Path tempDir = Files.createTempDirectory("YuCLI-test-");
        try {
            ToolRegistry registry = new ToolRegistry(1);
            registry.setProjectPath(tempDir.toString());

            String command = isWindows() ? "ping -n 3 127.0.0.1 > nul" : "sleep 2";
            String result = registry.executeTool("execute_command", "{\"command\":\"" + command + "\"}");

            assertTrue(result.contains("命令执行超时"), "预期超时，实际输出: " + result);
        } finally {
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldExecuteMultipleToolInvocationsInParallelAndKeepResultOrder() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public String executeTool(String name, String argumentsJson) {
                int now = current.incrementAndGet();
                peak.updateAndGet(prev -> Math.max(prev, now));
                bothStarted.countDown();
                try {
                    assertTrue(bothStarted.await(5, TimeUnit.SECONDS), "两个工具调用应同时进入执行区");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    current.decrementAndGet();
                }
                return "result-" + name;
            }
        };

        List<ToolRegistry.ToolExecutionResult> results = registry.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "first", "{}"),
                new ToolRegistry.ToolInvocation("call_2", "second", "{}")
        ));

        assertEquals(2, peak.get(), "两个工具调用应并行执行");
        assertEquals("call_1", results.get(0).id());
        assertEquals("result-first", results.get(0).result());
        assertEquals("call_2", results.get(1).id());
        assertEquals("result-second", results.get(1).result());
    }

    @Test
    void shouldUseNativeShellForCurrentOperatingSystem() {
        List<String> commandLine = ToolRegistry.shellCommand("echo ok");

        if (isWindows()) {
            assertEquals("cmd.exe", commandLine.get(0));
            assertEquals("/c", commandLine.get(1));
        } else {
            assertEquals("bash", commandLine.get(0));
            assertEquals("-c", commandLine.get(1));
        }
        assertEquals("echo ok", commandLine.get(2));
    }

    @Test
    void shouldCancelToolInvocationWhenBatchTimeoutIsReached() {
        ToolRegistry registry = new ToolRegistry(1, 1) {
            @Override
            public String executeTool(String name, String argumentsJson) {
                if ("slow".equals(name)) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return "result-" + name;
            }
        };

        List<ToolRegistry.ToolExecutionResult> results = registry.executeTools(List.of(
                new ToolRegistry.ToolInvocation("call_1", "slow", "{}"),
                new ToolRegistry.ToolInvocation("call_2", "fast", "{}")
        ));

        assertTrue(results.get(0).timedOut());
        assertTrue(results.get(0).result().contains("工具执行超时"));
        assertEquals("result-fast", results.get(1).result());
    }

    @Test
    void browserInteractionToolsExposeDomSummaryControls() {
        ToolRegistry registry = new ToolRegistry();

        assertDomSummaryControls(registry, "browser_navigate");
        assertDomSummaryControls(registry, "browser_click");
        assertDomSummaryControls(registry, "browser_type");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void assertDomSummaryControls(ToolRegistry registry, String toolName) {
        JsonNode properties = registry.getToolDefinitions().stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElseThrow()
                .parameters()
                .path("properties");

        assertNotNull(properties.get("include_dom_summary"), toolName + " 应声明 include_dom_summary");
        assertNotNull(properties.get("dom_summary_max_length"), toolName + " 应声明 dom_summary_max_length");
    }
}
