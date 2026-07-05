package com.yucli.sandbox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public record SandboxConfig(
        boolean enabled,
        String driver,
        String dockerExecutable,
        String dockerImage,
        String network,
        String mountMode,
        String memory,
        String cpus
) {
    public static final String DEFAULT_DRIVER = "docker";
    public static final String DEFAULT_DOCKER_EXECUTABLE = "docker";
    public static final String DEFAULT_DOCKER_IMAGE = "maven:3.9-eclipse-temurin-17";
    public static final String DEFAULT_NETWORK = "none";
    public static final String DEFAULT_MOUNT_MODE = "rw";

    public SandboxConfig {
        driver = defaultIfBlank(driver, DEFAULT_DRIVER).toLowerCase(Locale.ROOT);
        dockerExecutable = defaultIfBlank(dockerExecutable, DEFAULT_DOCKER_EXECUTABLE);
        dockerImage = defaultIfBlank(dockerImage, DEFAULT_DOCKER_IMAGE);
        network = defaultIfBlank(network, DEFAULT_NETWORK);
        mountMode = defaultIfBlank(mountMode, DEFAULT_MOUNT_MODE).toLowerCase(Locale.ROOT);
        memory = blankToNull(memory);
        cpus = blankToNull(cpus);

        if (enabled && !"docker".equals(driver)) {
            throw new IllegalArgumentException("Unsupported sandbox driver: " + driver);
        }
        if (!"rw".equals(mountMode) && !"ro".equals(mountMode)) {
            throw new IllegalArgumentException("Sandbox mount mode must be rw or ro");
        }
    }

    public static SandboxConfig disabled() {
        return new SandboxConfig(false, DEFAULT_DRIVER, DEFAULT_DOCKER_EXECUTABLE, DEFAULT_DOCKER_IMAGE,
                DEFAULT_NETWORK, DEFAULT_MOUNT_MODE, null, null);
    }

    public static SandboxConfig fromEnvironment() {
        return new SandboxConfig(
                parseBoolean(read("YuCLI.sandbox.enabled", "YUCLI_SANDBOX_ENABLED", null)),
                read("YuCLI.sandbox.driver", "YUCLI_SANDBOX_DRIVER", DEFAULT_DRIVER),
                read("YuCLI.sandbox.docker.executable", "YUCLI_SANDBOX_DOCKER", DEFAULT_DOCKER_EXECUTABLE),
                read("YuCLI.sandbox.docker.image", "YUCLI_SANDBOX_DOCKER_IMAGE", DEFAULT_DOCKER_IMAGE),
                read("YuCLI.sandbox.docker.network", "YUCLI_SANDBOX_NETWORK", DEFAULT_NETWORK),
                read("YuCLI.sandbox.docker.mount", "YUCLI_SANDBOX_MOUNT", DEFAULT_MOUNT_MODE),
                read("YuCLI.sandbox.docker.memory", "YUCLI_SANDBOX_MEMORY", null),
                read("YuCLI.sandbox.docker.cpus", "YUCLI_SANDBOX_CPUS", null)
        );
    }

    public static SandboxConfig from(Map<String, String> properties, Map<String, String> environment) {
        return new SandboxConfig(
                parseBoolean(read(properties, environment, "YuCLI.sandbox.enabled", "YUCLI_SANDBOX_ENABLED", null)),
                read(properties, environment, "YuCLI.sandbox.driver", "YUCLI_SANDBOX_DRIVER", DEFAULT_DRIVER),
                read(properties, environment, "YuCLI.sandbox.docker.executable", "YUCLI_SANDBOX_DOCKER", DEFAULT_DOCKER_EXECUTABLE),
                read(properties, environment, "YuCLI.sandbox.docker.image", "YUCLI_SANDBOX_DOCKER_IMAGE", DEFAULT_DOCKER_IMAGE),
                read(properties, environment, "YuCLI.sandbox.docker.network", "YUCLI_SANDBOX_NETWORK", DEFAULT_NETWORK),
                read(properties, environment, "YuCLI.sandbox.docker.mount", "YUCLI_SANDBOX_MOUNT", DEFAULT_MOUNT_MODE),
                read(properties, environment, "YuCLI.sandbox.docker.memory", "YUCLI_SANDBOX_MEMORY", null),
                read(properties, environment, "YuCLI.sandbox.docker.cpus", "YUCLI_SANDBOX_CPUS", null)
        );
    }

    public String statusText() {
        if (!enabled) {
            return "disabled";
        }
        String limits = "";
        if (memory != null || cpus != null) {
            limits = ", limits=" + (memory == null ? "memory:default" : "memory:" + memory)
                    + "/" + (cpus == null ? "cpus:default" : "cpus:" + cpus);
        }
        return "docker(image=" + dockerImage + ", network=" + network + ", mount=" + mountMode + limits + ")";
    }

    private static String read(String propertyKey, String envKey, String defaultValue) {
        String value = System.getProperty(propertyKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = readEnvFileValue(new File(".env"), envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = readEnvFileValue(new File(System.getProperty("user.home"), ".env"), envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    private static String read(Map<String, String> properties, Map<String, String> environment,
                               String propertyKey, String envKey, String defaultValue) {
        String value = properties == null ? null : properties.get(propertyKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = environment == null ? null : environment.get(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    private static String readEnvFileValue(File file, String key) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith(key + "=")) {
                    return line.substring((key + "=").length()).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
