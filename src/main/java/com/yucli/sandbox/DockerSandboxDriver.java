package com.yucli.sandbox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DockerSandboxDriver implements CommandSandboxDriver {
    private static final String WORKSPACE = "/workspace";

    private final SandboxConfig config;

    public DockerSandboxDriver(SandboxConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        if (!config.enabled()) {
            throw new IllegalArgumentException("Docker sandbox requires enabled config");
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public CommandProcessSpec createProcessSpec(String normalizedCommand, Path projectRoot) {
        if (normalizedCommand == null || normalizedCommand.isBlank()) {
            throw new IllegalArgumentException("Command must not be blank");
        }
        Path root = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        String containerName = "yucli-sandbox-" + UUID.randomUUID().toString().replace("-", "");

        List<String> commandLine = new ArrayList<>();
        commandLine.add(config.dockerExecutable());
        commandLine.add("run");
        commandLine.add("--rm");
        commandLine.add("--name");
        commandLine.add(containerName);
        commandLine.add("--network");
        commandLine.add(config.network());
        if (config.memory() != null) {
            commandLine.add("--memory");
            commandLine.add(config.memory());
        }
        if (config.cpus() != null) {
            commandLine.add("--cpus");
            commandLine.add(config.cpus());
        }
        commandLine.add("-v");
        commandLine.add(root + ":" + WORKSPACE + ":" + config.mountMode());
        commandLine.add("-w");
        commandLine.add(WORKSPACE);
        commandLine.add(config.dockerImage());
        commandLine.add("sh");
        commandLine.add("-lc");
        commandLine.add(normalizedCommand);

        List<String> cleanup = List.of(config.dockerExecutable(), "rm", "-f", containerName);
        return new CommandProcessSpec(commandLine, null, cleanup, "docker:" + config.dockerImage());
    }

    @Override
    public String statusText() {
        return config.statusText();
    }
}
