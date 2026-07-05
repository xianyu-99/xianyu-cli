package com.yucli.policy;

public record PermissionProfileDecision(Type type, String reason) {

    public enum Type {
        ALLOW,
        DENY,
        ASK
    }

    public static PermissionProfileDecision allow(String reason) {
        return new PermissionProfileDecision(Type.ALLOW, reason);
    }

    public static PermissionProfileDecision deny(String reason) {
        return new PermissionProfileDecision(Type.DENY, reason);
    }

    public static PermissionProfileDecision ask(String reason) {
        return new PermissionProfileDecision(Type.ASK, reason);
    }

    public boolean isAllow() {
        return type == Type.ALLOW;
    }

    public boolean isDeny() {
        return type == Type.DENY;
    }

    public boolean isAsk() {
        return type == Type.ASK;
    }
}
