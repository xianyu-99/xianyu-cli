package com.yucli.sandbox;

import java.nio.file.Path;

public interface CommandSandboxDriver {
    boolean enabled();

    CommandProcessSpec createProcessSpec(String normalizedCommand, Path projectRoot);

    String statusText();

    static CommandSandboxDriver fromEnvironment() {
        return fromConfig(SandboxConfig.fromEnvironment());
    }

    static CommandSandboxDriver fromConfig(SandboxConfig config) {
        if (config == null || !config.enabled()) {
            return DisabledSandboxDriver.INSTANCE;
        }
        if ("docker".equals(config.driver())) {
            return new DockerSandboxDriver(config);
        }
        throw new IllegalArgumentException("Unsupported sandbox driver: " + config.driver());
    }
}
