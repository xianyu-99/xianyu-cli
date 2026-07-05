package com.yucli.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionProfileTest {

    @Test
    void defaultProfileDoesNotDenyUnknownTools() {
        PermissionProfile profile = PermissionProfile.defaultProfile();

        PermissionProfileDecision decision = profile.decision("unknown_tool", "{\"value\":true}");

        assertEquals(PermissionProfileDecision.Type.ASK, decision.type());
        assertTrue(decision.reason().contains("默认策略"));
    }

    @Test
    void denyRulesHavePriorityOverAllowAndAskRules() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of("execute_command"),
                List.of("execute_command:rm*"),
                List.of("execute_command")
        );

        PermissionProfileDecision decision = profile.decision(
                "execute_command",
                "{\"command\":\"rm -rf target/classes\"}"
        );

        assertEquals(PermissionProfileDecision.Type.DENY, decision.type());
        assertTrue(decision.reason().contains("execute_command:rm*"));
    }

    @Test
    void exactToolAllowRuleMatches() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of("read_file"),
                List.of(),
                List.of()
        );

        assertEquals(
                PermissionProfileDecision.Type.ALLOW,
                profile.decision("read_file", "{\"path\":\"README.md\"}").type()
        );
        assertEquals(
                PermissionProfileDecision.Type.ASK,
                profile.decision("write_file", "{\"path\":\"README.md\"}").type()
        );
    }

    @Test
    void prefixWildcardRuleMatchesToolName() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of(),
                List.of("mcp__danger__*"),
                List.of("mcp__*")
        );

        assertEquals(
                PermissionProfileDecision.Type.DENY,
                profile.decision("mcp__danger__delete_all", "{}").type()
        );
        assertEquals(
                PermissionProfileDecision.Type.ASK,
                profile.decision("mcp__safe__read", "{}").type()
        );
    }

    @Test
    void starRuleMatchesAnyTool() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of(),
                List.of(),
                List.of("*")
        );

        assertEquals(
                PermissionProfileDecision.Type.ASK,
                profile.decision("plugin__custom__tool", "{}").type()
        );
    }

    @Test
    void argumentSubstringRuleMatchesRawArguments() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of("execute_command"),
                List.of("execute_command:rm*"),
                List.of()
        );

        assertEquals(
                PermissionProfileDecision.Type.DENY,
                profile.decision("execute_command", "{\"command\":\"RM -rf target\"}").type()
        );
        assertEquals(
                PermissionProfileDecision.Type.ALLOW,
                profile.decision("execute_command", "{\"command\":\"mvn test\"}").type()
        );
    }

    @Test
    void statusTextIncludesModeAndRuleCounts() {
        PermissionProfile profile = new PermissionProfile(
                "default",
                List.of("read_file"),
                List.of("execute_command:rm*"),
                List.of("write_file")
        );

        String status = profile.statusText();

        assertTrue(status.contains("mode=default"));
        assertTrue(status.contains("allow(1)"));
        assertTrue(status.contains("deny(1)"));
        assertTrue(status.contains("ask(1)"));
    }
}
