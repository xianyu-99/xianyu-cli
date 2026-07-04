package com.yucli.hook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookDefinitionTest {

    @Test
    void matchesExactWildcardAndPrefixPatterns() {
        assertTrue(new HookDefinition("*", List.of("echo ok"), 1).matches("write_file"));
        assertTrue(new HookDefinition("write_file", List.of("echo ok"), 1).matches("WRITE_FILE"));
        assertTrue(new HookDefinition("mcp__*", List.of("echo ok"), 1).matches("mcp__demo__read"));
        assertFalse(new HookDefinition("browser_*", List.of("echo ok"), 1).matches("write_file"));
    }

    @Test
    void normalizesSingleCommandAndCommandList() {
        HookDefinition definition = new HookDefinition();
        definition.setCommand(" echo one ");
        definition.setCommands(List.of("", " echo two "));

        assertEquals(List.of("echo one", "echo two"), definition.normalizedCommands());
    }
}
