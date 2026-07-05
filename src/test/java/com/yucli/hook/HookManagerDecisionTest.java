package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookManagerDecisionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preToolUseDeniesFromStructuredStdout(@TempDir Path tempDir) {
        HookManager manager = new HookManager(
                hooks(new HookDefinition("write_file", List.of("deny"), 3)),
                tempDir,
                stdoutExecutor("{\"decision\":\"deny\",\"reason\":\"blocked by hook\"}"));

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"blocked.txt\",\"content\":\"no\"}");

        assertFalse(decision.allowed());
        assertEquals(HookDecision.Decision.DENY, decision.decision());
        assertEquals("blocked by hook", decision.reason());
    }

    @Test
    void preToolUseModifyUpdatesDecisionAndLaterPayloads(@TempDir Path tempDir) throws Exception {
        List<String> payloads = new ArrayList<>();
        HookCommandExecutor executor = new HookCommandExecutor() {
            private int calls;

            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                payloads.add(inputJson);
                calls++;
                if (calls == 1) {
                    return success("""
                            {"decision":"modify","arguments":{"path":"modified.txt","content":"after"}}
                            """);
                }
                return success("{\"decision\":\"allow\"}");
            }
        };
        HookManager manager = new HookManager(
                hooks(new HookDefinition("write_file", List.of("first", "second"), 3)),
                tempDir,
                executor);

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"original.txt\",\"content\":\"before\"}");

        assertTrue(decision.allowed());
        assertTrue(decision.modified());
        JsonNode modified = MAPPER.readTree(decision.argumentsJsonOr("{}"));
        assertEquals("modified.txt", modified.path("path").asText());
        assertEquals("after", modified.path("content").asText());

        assertEquals(2, payloads.size());
        JsonNode secondPayload = MAPPER.readTree(payloads.get(1));
        assertEquals("modified.txt", secondPayload.path("arguments").path("path").asText());
        assertEquals("after", secondPayload.path("arguments").path("content").asText());
        JsonNode rawArguments = MAPPER.readTree(secondPayload.path("arguments_raw").asText());
        assertEquals("modified.txt", rawArguments.path("path").asText());
    }

    @Test
    void successfulNonJsonStdoutKeepsLegacyAllowBehavior(@TempDir Path tempDir) {
        HookManager manager = new HookManager(
                hooks(new HookDefinition("*", List.of("legacy"), 3)),
                tempDir,
                stdoutExecutor("legacy output"));

        HookDecision decision = manager.runPreToolUse("list_dir", "{\"path\":\".\"}");

        assertTrue(decision.allowed());
        assertFalse(decision.modified());
        assertEquals(HookDecision.Decision.ALLOW, decision.decision());
    }

    @Test
    void statusReportsConfiguredHookSummary(@TempDir Path tempDir) {
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(
                new HookDefinition("write_file", List.of("echo pre"), 3)));
        hooks.put(HookEvent.POST_TOOL_USE, List.of(
                new HookDefinition("*", List.of("echo post", "echo audit"), 0)));
        HookManager manager = new HookManager(hooks, tempDir, stdoutExecutor(""));

        HookManager.HookStatus status = manager.status();

        assertTrue(status.enabled());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), status.projectPath());
        assertEquals(2, status.totalHooks());
        assertEquals(1, status.hookCounts().get("PreToolUse"));
        assertEquals(1, status.hookCounts().get("PostToolUse"));
        assertEquals("PreToolUse", status.hooks().get(0).event());
        assertEquals("write_file", status.hooks().get(0).matcher());
        assertEquals(List.of("echo pre"), status.hooks().get(0).commands());
        assertEquals(10, status.hooks().get(1).timeoutSeconds());
    }

    private static Map<HookEvent, List<HookDefinition>> hooks(HookDefinition definition) {
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(definition));
        return hooks;
    }

    private static HookCommandExecutor stdoutExecutor(String output) {
        return new HookCommandExecutor() {
            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                return success(output);
            }
        };
    }

    private static HookCommandExecutor.HookCommandResult success(String output) {
        return new HookCommandExecutor.HookCommandResult(0, output, false, null);
    }
}
