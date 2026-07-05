package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record HookDecision(Decision decision, String reason, JsonNode arguments) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Decision {
        ALLOW,
        DENY,
        MODIFY
    }

    public HookDecision {
        decision = decision == null ? Decision.ALLOW : decision;
        if (decision == Decision.DENY && (reason == null || reason.isBlank())) {
            reason = "hook rejected this operation";
        }
    }

    public boolean allowed() {
        return decision != Decision.DENY;
    }

    public boolean modified() {
        return decision == Decision.MODIFY && arguments != null && !arguments.isNull();
    }

    public String argumentsJsonOr(String originalArgumentsJson) {
        if (!modified()) {
            return originalArgumentsJson;
        }
        try {
            return MAPPER.writeValueAsString(arguments);
        } catch (Exception e) {
            return originalArgumentsJson;
        }
    }

    public static HookDecision allow() {
        return new HookDecision(Decision.ALLOW, null, null);
    }

    public static HookDecision block(String reason) {
        return new HookDecision(Decision.DENY, reason, null);
    }

    public static HookDecision modify(JsonNode arguments) {
        return new HookDecision(Decision.MODIFY, null, arguments);
    }

    public static HookDecision modify(JsonNode arguments, String reason) {
        return new HookDecision(Decision.MODIFY, reason, arguments);
    }
}
