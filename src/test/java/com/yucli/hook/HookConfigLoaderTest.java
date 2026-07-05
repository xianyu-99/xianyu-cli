package com.yucli.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookConfigLoaderTest {

    @Test
    void loadsAndMergesHookFilesInOrder(@TempDir Path tempDir) throws Exception {
        Path first = tempDir.resolve("first-hooks.json");
        Path second = tempDir.resolve("second-hooks.json");
        Files.writeString(first, """
                {
                  "hooks": {
                    "PostToolUse": [
                      { "matcher": "*", "command": "echo first>>hook.log" }
                    ]
                  }
                }
                """);
        Files.writeString(second, """
                {
                  "hooks": {
                    "PostToolUse": [
                      { "matcher": "*", "commands": ["echo second>>hook.log"] }
                    ]
                  }
                }
                """);

        HookManager manager = new HookConfigLoader().load(tempDir, first, second);
        manager.runPostToolUse("list_dir", "{\"path\":\".\"}", "ok", 1);

        assertTrue(manager.hasHooks());
        assertEquals("first" + System.lineSeparator() + "second",
                Files.readString(tempDir.resolve("hook.log")).trim());
    }

    @Test
    void loadsHttpAndPromptHookFields(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("hooks.json");
        Files.writeString(config, """
                {
                  "hooks": {
                    "PreToolUse": [
                      {
                        "matcher": "write_file",
                        "url": "https://hooks.example/pre",
                        "urls": ["https://hooks.example/audit"],
                        "prompt": "review write",
                        "prompts": ["audit write"]
                      }
                    ]
                  }
                }
                """);

        HookManager manager = new HookConfigLoader().load(tempDir, config);
        HookManager.HookStatus status = manager.status();

        assertTrue(manager.hasHooks());
        assertEquals(2, status.executorCounts().get("http"));
        assertEquals(2, status.executorCounts().get("prompt"));
        assertEquals(2, status.hooks().get(0).urls().size());
        assertEquals(2, status.hooks().get(0).prompts().size());
    }
}
