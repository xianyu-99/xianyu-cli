package com.yucli.sandbox;

import java.nio.file.Path;

final class DisabledSandboxDriver implements CommandSandboxDriver {
    static final DisabledSandboxDriver INSTANCE = new DisabledSandboxDriver();

    private DisabledSandboxDriver() {
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public CommandProcessSpec createProcessSpec(String normalizedCommand, Path projectRoot) {
        throw new IllegalStateException("Sandbox is disabled");
    }

    @Override
    public String statusText() {
        return "disabled";
    }
}
