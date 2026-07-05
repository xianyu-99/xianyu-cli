package com.yucli.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSandboxDriverTest {

    @Test
    void buildsDockerRunCommandWithProjectMount(@TempDir Path projectRoot) {
        SandboxConfig config = SandboxConfig.from(
                Map.of(
                        "YuCLI.sandbox.enabled", "true",
                        "YuCLI.sandbox.docker.executable", "docker-test",
                        "YuCLI.sandbox.docker.image", "yucli/dev:latest",
                        "YuCLI.sandbox.docker.network", "none",
                        "YuCLI.sandbox.docker.mount", "ro",
                        "YuCLI.sandbox.docker.memory", "1g",
                        "YuCLI.sandbox.docker.cpus", "1.5"
                ),
                Map.of()
        );
        DockerSandboxDriver driver = new DockerSandboxDriver(config);

        CommandProcessSpec spec = driver.createProcessSpec("mvn test", projectRoot);

        List<String> command = spec.commandLine();
        assertEquals("docker-test", command.get(0));
        assertEquals("run", command.get(1));
        assertTrue(command.contains("--rm"));
        assertTrue(command.contains("--name"));
        assertTrue(command.stream().anyMatch(part -> part.startsWith("yucli-sandbox-")));
        assertTrue(command.contains("--network"));
        assertTrue(command.contains("none"));
        assertTrue(command.contains("--memory"));
        assertTrue(command.contains("1g"));
        assertTrue(command.contains("--cpus"));
        assertTrue(command.contains("1.5"));
        assertTrue(command.contains(projectRoot.toAbsolutePath().normalize() + ":/workspace:ro"));
        assertTrue(command.contains("yucli/dev:latest"));
        assertEquals("sh", command.get(command.size() - 3));
        assertEquals("-lc", command.get(command.size() - 2));
        assertEquals("mvn test", command.get(command.size() - 1));
        assertEquals(List.of("docker-test", "rm", "-f", containerName(command)), spec.cleanupCommand());
    }

    private static String containerName(List<String> command) {
        int nameFlag = command.indexOf("--name");
        return command.get(nameFlag + 1);
    }
}
