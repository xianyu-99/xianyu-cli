package com.yucli.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
