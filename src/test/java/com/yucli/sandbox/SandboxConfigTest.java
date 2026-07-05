package com.yucli.sandbox;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxConfigTest {

    @Test
    void defaultsToDisabledDockerConfig() {
        SandboxConfig config = SandboxConfig.from(Map.of(), Map.of());

        assertFalse(config.enabled());
        assertEquals("docker", config.driver());
        assertEquals("docker", config.dockerExecutable());
        assertEquals("maven:3.9-eclipse-temurin-17", config.dockerImage());
        assertEquals("none", config.network());
        assertEquals("rw", config.mountMode());
        assertEquals("disabled", config.statusText());
    }

    @Test
    void readsSystemPropertiesBeforeEnvironment() {
        SandboxConfig config = SandboxConfig.from(
                Map.of(
                        "YuCLI.sandbox.enabled", "true",
                        "YuCLI.sandbox.docker.image", "custom:dev",
                        "YuCLI.sandbox.docker.mount", "ro"
                ),
                Map.of(
                        "YUCLI_SANDBOX_ENABLED", "false",
                        "YUCLI_SANDBOX_DOCKER_IMAGE", "ignored:latest"
                )
        );

        assertTrue(config.enabled());
        assertEquals("custom:dev", config.dockerImage());
        assertEquals("ro", config.mountMode());
        assertTrue(config.statusText().contains("custom:dev"));
    }

    @Test
    void rejectsUnsupportedEnabledDriver() {
        assertThrows(IllegalArgumentException.class, () -> SandboxConfig.from(
                Map.of("YuCLI.sandbox.enabled", "true", "YuCLI.sandbox.driver", "firecracker"),
                Map.of()
        ));
    }

    @Test
    void rejectsInvalidMountMode() {
        assertThrows(IllegalArgumentException.class, () -> SandboxConfig.from(
                Map.of("YuCLI.sandbox.docker.mount", "maybe"),
                Map.of()
        ));
    }
}
