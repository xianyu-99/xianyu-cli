package com.yucli.agent.config;

import com.yucli.agent.AgentRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentProfileLoaderTest {

    @Test
    void loadReturnsEmptyMapWhenDirectoriesAreMissing(@TempDir Path tempDir) throws Exception {
        AgentProfileLoader loader = new AgentProfileLoader(
                tempDir.resolve("missing-user-agents"),
                tempDir.resolve("missing-project-agents")
        );

        assertTrue(loader.load().isEmpty());
    }

    @Test
    void loadsUserAndProjectProfilesWithProjectOverride(@TempDir Path tempDir) throws Exception {
        Path userAgents = tempDir.resolve("user-agents");
        Path projectAgents = tempDir.resolve("project-agents");
        Files.createDirectories(userAgents);
        Files.createDirectories(projectAgents);

        Files.writeString(userAgents.resolve("reviewer.json"), """
                {
                  "name": "reviewer",
                  "role": "worker",
                  "instructions": "User reviewer profile",
                  "tools": ["read_file"],
                  "model": "glm-5.1"
                }
                """);
        Path projectProfile = projectAgents.resolve("reviewer.json");
        Files.writeString(projectProfile, """
                {
                  "name": "reviewer",
                  "role": "REVIEWER",
                  "instructions": "Project reviewer profile",
                  "tools": ["read_file", "search_code"],
                  "model": "deepseek-v4-pro"
                }
                """);
        Files.writeString(userAgents.resolve("planner.json"), """
                {
                  "name": "planner",
                  "role": "PLANNER",
                  "instructions": "Plan the work",
                  "tools": []
                }
                """);

        AgentProfileLoader loader = new AgentProfileLoader(userAgents, projectAgents);
        Map<String, AgentProfile> profiles = loader.load();

        assertEquals(2, profiles.size());
        AgentProfile reviewer = profiles.get("reviewer");
        assertEquals(AgentRole.REVIEWER, reviewer.getRole());
        assertEquals("Project reviewer profile", reviewer.getInstructions());
        assertEquals(List.of("read_file", "search_code"), reviewer.getTools());
        assertEquals("deepseek-v4-pro", reviewer.getModel());
        assertEquals(projectProfile.toAbsolutePath().normalize(), reviewer.getSourcePath());

        assertEquals(AgentRole.PLANNER, profiles.get("planner").getRole());
    }

    @Test
    void defaultsToolsToEmptyList(@TempDir Path tempDir) throws Exception {
        Path userAgents = tempDir.resolve("user-agents");
        Files.createDirectories(userAgents);
        Files.writeString(userAgents.resolve("worker.json"), """
                {
                  "name": "worker",
                  "role": "worker",
                  "instructions": "Do the work"
                }
                """);

        AgentProfileLoader loader = new AgentProfileLoader(userAgents, tempDir.resolve("project-agents"));

        assertEquals(List.of(), loader.load().get("worker").getTools());
    }

    @Test
    void ignoresNonJsonFiles(@TempDir Path tempDir) throws Exception {
        Path userAgents = tempDir.resolve("user-agents");
        Files.createDirectories(userAgents);
        Files.writeString(userAgents.resolve("notes.md"), "not a profile");

        AgentProfileLoader loader = new AgentProfileLoader(userAgents, tempDir.resolve("project-agents"));

        assertTrue(loader.load().isEmpty());
    }

    @Test
    void rejectsUnsupportedRole(@TempDir Path tempDir) throws Exception {
        Path userAgents = tempDir.resolve("user-agents");
        Files.createDirectories(userAgents);
        Files.writeString(userAgents.resolve("bad.json"), """
                {
                  "name": "architect",
                  "role": "architect",
                  "instructions": "Design everything"
                }
                """);

        AgentProfileLoader loader = new AgentProfileLoader(userAgents, tempDir.resolve("project-agents"));

        IOException ex = assertThrows(IOException.class, loader::load);
        assertTrue(ex.getMessage().contains("bad.json"));
        assertTrue(ex.getMessage().contains("unsupported agent profile role"));
    }

    @Test
    void rejectsProfileWithoutName(@TempDir Path tempDir) throws Exception {
        Path userAgents = tempDir.resolve("user-agents");
        Files.createDirectories(userAgents);
        Files.writeString(userAgents.resolve("nameless.json"), """
                {
                  "role": "worker",
                  "instructions": "Do the work"
                }
                """);

        AgentProfileLoader loader = new AgentProfileLoader(userAgents, tempDir.resolve("project-agents"));

        IOException ex = assertThrows(IOException.class, loader::load);
        assertTrue(ex.getMessage().contains("nameless.json"));
        assertTrue(ex.getMessage().contains("name is required"));
    }
}
