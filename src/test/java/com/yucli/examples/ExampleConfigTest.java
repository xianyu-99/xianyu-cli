package com.yucli.examples;

import com.yucli.agent.AgentRole;
import com.yucli.agent.config.AgentProfile;
import com.yucli.agent.config.AgentProfileLoader;
import com.yucli.hook.HookManager;
import com.yucli.hook.HookConfigLoader;
import com.yucli.mcp.config.McpConfigLoader;
import com.yucli.mcp.config.McpServerConfig;
import com.yucli.skill.Skill;
import com.yucli.skill.SkillLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void bundledMcpExamplesLoad(@TempDir Path tempDir) throws Exception {
        Map<String, McpServerConfig> configs = new McpConfigLoader(
                tempDir.resolve("missing-user-mcp.json"),
                Path.of("examples", "mcp", "mcp.json"),
                Path.of(".")
        ).load();

        assertTrue(configs.containsKey("fetch"));
        assertTrue(configs.containsKey("filesystem"));
        assertTrue(configs.containsKey("remote-header-demo"));
        assertTrue(configs.containsKey("remote-oauth-demo"));
        assertEquals("uvx", configs.get("fetch").getCommand());
        assertTrue(configs.get("fetch").isDisabled());
        assertEquals("https://mcp.example.com/v1", configs.get("remote-header-demo").getUrl());
        assertTrue(configs.get("remote-oauth-demo").isOauth());
        assertEquals(List.of("mcp:tools", "mcp:resources"), configs.get("remote-oauth-demo").getScopes());
    }

    @Test
    void bundledSkillExamplesLoad() {
        Map<String, Skill> skills = SkillLoader.loadFromDirectory(Path.of("examples", "skills"))
                .stream()
                .collect(Collectors.toMap(Skill::name, skill -> skill));

        Skill codeReview = skills.get("code-review");
        Skill mcpResearch = skills.get("mcp-research");

        assertNotNull(codeReview);
        assertNotNull(mcpResearch);
        assertTrue(codeReview.triggers().contains("review"));
        assertTrue(codeReview.body().contains("Prioritize findings"));
        assertTrue(mcpResearch.triggers().contains("mcp"));
        assertTrue(mcpResearch.body().contains("/mcp resources <name>"));
    }
}
