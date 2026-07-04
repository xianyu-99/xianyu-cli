package com.yucli.hook;

import com.yucli.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryHookTest {

    @Test
    void preToolUseCanBlockToolExecution(@TempDir Path tempDir) {
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(
                new HookDefinition("write_file", List.of(failingCommand()), 3)));

        ToolRegistry registry = new ToolRegistry(new HookManager(hooks, tempDir));
        registry.setProjectPath(tempDir.toString());

        String result = registry.executeTool("write_file",
                "{\"path\":\"blocked.txt\",\"content\":\"should not write\"}");

        assertTrue(result.contains("[Hook] PreToolUse 拒绝"), result);
        assertFalse(Files.exists(tempDir.resolve("blocked.txt")));
    }

    @Test
    void postToolUseRunsAfterToolExecution(@TempDir Path tempDir) throws Exception {
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.POST_TOOL_USE, List.of(
                new HookDefinition("list_dir", List.of("echo post>>hook.log"), 3)));

        ToolRegistry registry = new ToolRegistry(new HookManager(hooks, tempDir));
        registry.setProjectPath(tempDir.toString());

        String result = registry.executeTool("list_dir", "{\"path\":\".\"}");

        assertTrue(result.contains("目录内容"), result);
        assertTrue(Files.readString(tempDir.resolve("hook.log")).contains("post"));
    }

    private static String failingCommand() {
        return isWindows() ? "echo denied & exit /b 7" : "echo denied; exit 7";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
