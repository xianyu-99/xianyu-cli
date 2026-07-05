package com.yucli.examples;

import com.yucli.agent.AgentRole;
import com.yucli.agent.config.AgentProfile;
import com.yucli.agent.config.AgentProfileLoader;
import com.yucli.hook.HookManager;
import com.yucli.hook.HookConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleConfigTest {

    @Test
    void bundledAgentProfilesLoad(@TempDir Path tempDir) throws Exception {
        Map<String, AgentProfile> profiles = new AgentProfileLoader(
                Path.of("examples", "agents"),
                tempDir.resolve("missing-project-agents")
        ).load();

        assertTrue(profiles.containsKey("planner"));
        assertTrue(profiles.containsKey("reviewer"));
        assertTrue(profiles.containsKey("worker-safe-editor"));
        assertTrue(profiles.containsKey("worker-verifier"));
        assertEquals(AgentRole.PLANNER, profiles.get("planner").getRole());
        assertEquals(AgentRole.REVIEWER, profiles.get("reviewer").getRole());
        assertFalse(profiles.get("worker-safe-editor").getTools().isEmpty());
        assertFalse(profiles.get("worker-safe-editor").getAllowedPaths().isEmpty());
        assertFalse(profiles.get("worker-safe-editor").getDeniedCommands().isEmpty());
    }

    @Test
    void bundledHookRecipesLoad(@TempDir Path tempDir) {
        HookManager manager = new HookConfigLoader().load(
                tempDir,
                Path.of("examples", "hooks", "hooks.json")
        );
        HookManager.HookStatus status = manager.status();

        assertTrue(manager.hasHooks());
        assertEquals(3, status.totalHooks());
        assertEquals(3, status.executorCounts().get("command"));
        assertEquals(0, status.executorCounts().get("http"));
        assertEquals(0, status.executorCounts().get("prompt"));
        assertTrue(status.hooks().stream().anyMatch(HookManager.HookSummary::async));
    }
}
