package com.yucli.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionProfileLoaderTest {

    @Test
    void loadReturnsDefaultProfileWhenFilesAreMissing(@TempDir Path tempDir) throws Exception {
        PermissionProfile profile = PermissionProfileLoader.load(
                tempDir.resolve("missing-user.json"),
                tempDir.resolve("missing-project.json")
        );

        assertEquals(PermissionProfile.DEFAULT_MODE, profile.mode());
        assertEquals(PermissionProfileDecision.Type.ASK, profile.decision("read_file", "{}").type());
        assertTrue(profile.allow().isEmpty());
        assertTrue(profile.deny().isEmpty());
        assertTrue(profile.ask().isEmpty());
    }

    @Test
    void projectConfigOverridesModeAndMergesRules(@TempDir Path tempDir) throws Exception {
        Path userConfig = tempDir.resolve("user-permissions.json");
        Path projectConfig = tempDir.resolve("project-permissions.json");
        Files.writeString(userConfig, """
                {
                  "mode": "default",
                  "allow": ["read_file"],
                  "deny": ["execute_command:curl*"],
                  "ask": ["write_file"]
                }
                """);
        Files.writeString(projectConfig, """
                {
                  "mode": "project",
                  "allow": ["list_dir"],
                  "deny": ["mcp__danger__*"],
                  "ask": ["execute_command"]
                }
                """);

        PermissionProfile profile = PermissionProfileLoader.load(userConfig, projectConfig);

        assertEquals("project", profile.mode());
        assertEquals(List.of("read_file", "list_dir"), profile.allow());
        assertEquals(List.of("execute_command:curl*", "mcp__danger__*"), profile.deny());
        assertEquals(List.of("write_file", "execute_command"), profile.ask());
        assertEquals(2, profile.sourcePaths().size());
    }

    @Test
    void mergedProfileKeepsDenyPriority(@TempDir Path tempDir) throws Exception {
        Path userConfig = tempDir.resolve("user-permissions.json");
        Path projectConfig = tempDir.resolve("project-permissions.json");
        Files.writeString(userConfig, """
                {
                  "allow": ["execute_command"],
                  "deny": ["execute_command:rm*"]
                }
                """);
        Files.writeString(projectConfig, """
                {
                  "ask": ["execute_command"]
                }
                """);

        PermissionProfile profile = PermissionProfileLoader.load(userConfig, projectConfig);

        assertEquals(
                PermissionProfileDecision.Type.DENY,
                profile.decision("execute_command", "{\"command\":\"rm -rf target\"}").type()
        );
        assertEquals(
                PermissionProfileDecision.Type.ALLOW,
                profile.decision("execute_command", "{\"command\":\"mvn test\"}").type()
        );
    }

    @Test
    void loadDefaultUsesYuCLIProjectPermissionPath(@TempDir Path tempDir) throws Exception {
        Path homeDir = tempDir.resolve("home");
        Path projectRoot = tempDir.resolve("project");
        Path projectConfigDir = projectRoot.resolve(".YuCLI");
        Files.createDirectories(projectConfigDir);
        Files.writeString(projectConfigDir.resolve("permissions.json"), """
                {
                  "deny": ["mcp__danger__*"]
                }
                """);

        String oldHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", homeDir.toString());
            PermissionProfile profile = PermissionProfileLoader.loadDefault(projectRoot);

            assertEquals(PermissionProfile.DEFAULT_MODE, profile.mode());
            assertEquals(
                    PermissionProfileDecision.Type.DENY,
                    profile.decision("mcp__danger__drop", "{}").type()
            );
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    @Test
    void invalidJsonReturnsIOException(@TempDir Path tempDir) throws Exception {
        Path userConfig = tempDir.resolve("permissions.json");
        Files.writeString(userConfig, "{ invalid json");

        IOException ex = assertThrows(IOException.class,
                () -> PermissionProfileLoader.load(userConfig, null));

        assertTrue(ex.getMessage().contains("invalid permission profile"));
    }
}
