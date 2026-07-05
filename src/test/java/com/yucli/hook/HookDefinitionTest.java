package com.yucli.hook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookDefinitionTest {

    @Test
    void parsesLifecycleEventNamesWithFlexibleSeparators() {
        assertEquals(HookEvent.USER_PROMPT_SUBMIT, HookEvent.fromConfigName("UserPromptSubmit"));
        assertEquals(HookEvent.USER_PROMPT_SUBMIT, HookEvent.fromConfigName("user_prompt_submit"));
        assertEquals(HookEvent.AGENT_START, HookEvent.fromConfigName("agent-start"));
        assertEquals(HookEvent.SUB_AGENT_FINISH, HookEvent.fromConfigName("SubAgentFinish"));
        assertEquals(HookEvent.PRE_COMPACT, HookEvent.fromConfigName("pre_compact"));
    }

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

    @Test
    void normalizesHttpAndPromptExecutors() {
        HookDefinition definition = new HookDefinition();
        definition.setUrl(" https://hooks.example/pre ");
        definition.setUrls(List.of("", " https://hooks.example/audit "));
        definition.setPrompt(" allow only safe writes ");
        definition.setPrompts(List.of(" ", " return deny for secrets "));

        assertEquals(List.of("https://hooks.example/pre", "https://hooks.example/audit"),
                definition.normalizedUrls());
        assertEquals(List.of("allow only safe writes", "return deny for secrets"),
                definition.normalizedPrompts());
        assertTrue(definition.hasExecutors());
    }

    @Test
    void normalizesHttpSecurityRetryAndAsyncOptions() {
        HookDefinition definition = new HookDefinition();
        definition.setHeaders(Map.of(" X-Team ", " agent-platform "));
        definition.setAuthToken(" hook-token ");
        definition.setRetryCount(9);
        definition.setRetryBackoffMillis(9_000L);
        definition.setAsync(true);

        assertEquals("agent-platform", definition.normalizedHeaders().get("X-Team"));
        assertEquals("Bearer hook-token", definition.normalizedHeaders().get("Authorization"));
        assertEquals(3, definition.normalizedRetryCount());
        assertEquals(2_000L, definition.normalizedRetryBackoffMillis());
        assertTrue(definition.asyncEnabled());
    }
}
