package com.yucli.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void expandsEnvironmentPlaceholdersForHttpHookFields(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("hooks.json");
        Files.writeString(config, """
                {
                  "hooks": {
                    "PreToolUse": [
                      {
                        "matcher": "write_file",
                        "url": "https://${YUCLI_HOOK_HOST}/pre",
                        "headers": {"X-Remote-Token": "token-${YUCLI_HOOK_HEADER_TOKEN}"},
                        "authToken": "${YUCLI_HOOK_AUTH_TOKEN}",
                        "signatureSecret": "${YUCLI_HOOK_SIGNATURE_SECRET}"
                      }
                    ]
                  }
                }
                """);

        HookManager manager = new HookConfigLoader(Map.of(
                "YUCLI_HOOK_HOST", "hooks.example",
                "YUCLI_HOOK_HEADER_TOKEN", "header-secret",
                "YUCLI_HOOK_AUTH_TOKEN", "auth$secret",
                "YUCLI_HOOK_SIGNATURE_SECRET", "signature-secret"
        )).load(tempDir, config);

        HookDefinition definition = hooksFrom(manager).get(HookEvent.PRE_TOOL_USE).get(0);

        assertEquals(List.of("https://hooks.example/pre"), definition.normalizedUrls());
        assertEquals("token-header-secret", definition.normalizedHeaders().get("X-Remote-Token"));
        assertEquals("Bearer auth$secret", definition.normalizedHeaders().get("Authorization"));
        assertEquals("signature-secret", definition.getSignatureSecret());
    }

    @Test
    void missingEnvironmentPlaceholderIgnoresConfigWithoutPrintingSecretContext(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("hooks.json");
        Files.writeString(config, """
                {
                  "hooks": {
                    "PreToolUse": [
                      {
                        "matcher": "write_file",
                        "headers": {"Authorization": "Bearer prefix-${MISSING_HOOK_SECRET}-suffix"}
                      }
                    ]
                  }
                }
                """);

        String stderr = captureStderr(() -> {
            HookManager manager = new HookConfigLoader(Map.of()).load(tempDir, config);
            assertFalse(manager.hasHooks());
        });

        assertTrue(stderr.contains("Missing environment variable MISSING_HOOK_SECRET"), stderr);
        assertFalse(stderr.contains("prefix-"), stderr);
        assertFalse(stderr.contains("-suffix"), stderr);
    }

    @Test
    void missingEnvironmentPlaceholderDoesNotPartiallyMergeConfigFile(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("hooks.json");
        Files.writeString(config, """
                {
                  "hooks": {
                    "PostToolUse": [
                      { "matcher": "*", "command": "echo should-not-load" }
                    ],
                    "PreToolUse": [
                      {
                        "matcher": "write_file",
                        "authToken": "${MISSING_HOOK_TOKEN}"
                      }
                    ]
                  }
                }
                """);

        final HookManager[] manager = new HookManager[1];
        String stderr = captureStderr(() ->
                manager[0] = new HookConfigLoader(Map.of()).load(tempDir, config));

        assertTrue(stderr.contains("MISSING_HOOK_TOKEN"), stderr);
        assertFalse(manager[0].hasHooks());
        assertEquals(0, manager[0].status().totalHooks());
    }

    @SuppressWarnings("unchecked")
    private static Map<HookEvent, List<HookDefinition>> hooksFrom(HookManager manager) throws Exception {
        java.lang.reflect.Field field = HookManager.class.getDeclaredField("hooks");
        field.setAccessible(true);
        return (Map<HookEvent, List<HookDefinition>>) field.get(manager);
    }

    private static String captureStderr(Runnable runnable) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true)) {
            System.setErr(capture);
            runnable.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString();
    }
}
