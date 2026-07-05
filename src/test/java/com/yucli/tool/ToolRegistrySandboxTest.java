package com.yucli.tool;

import com.yucli.sandbox.CommandProcessSpec;
import com.yucli.sandbox.CommandSandboxDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrySandboxTest {

    @Test
    void executeCommandUsesConfiguredSandboxDriver(@TempDir Path tempDir) {
        RecordingSandboxDriver sandbox = new RecordingSandboxDriver();
        ToolRegistry registry = new ToolRegistry();
        registry.setProjectPath(tempDir.toString());
        registry.setCommandSandboxDriver(sandbox);

        String result = registry.executeTool("execute_command", "{\"command\":\"ignored command\"}");

        assertEquals("ignored command", sandbox.lastCommand);
        assertEquals(tempDir.toAbsolutePath().normalize(), sandbox.lastProjectRoot);
        assertTrue(result.contains("sandboxed"));
        assertEquals("fake-sandbox", registry.commandSandboxStatus());
    }

    @Test
    void nullSandboxDriverDisablesSandbox() {
        ToolRegistry registry = new ToolRegistry();

        registry.setCommandSandboxDriver(null);

        assertEquals("disabled", registry.commandSandboxStatus());
    }

    private static final class RecordingSandboxDriver implements CommandSandboxDriver {
        private String lastCommand;
        private Path lastProjectRoot;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public CommandProcessSpec createProcessSpec(String normalizedCommand, Path projectRoot) {
            lastCommand = normalizedCommand;
            lastProjectRoot = projectRoot.toAbsolutePath().normalize();
            return new CommandProcessSpec(ToolRegistry.shellCommand("echo sandboxed"),
                    projectRoot, List.of(), "fake");
        }

        @Override
        public String statusText() {
            return "fake-sandbox";
        }
    }
}
