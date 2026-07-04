package com.yucli.hook;

public record HookDecision(boolean allowed, String reason) {
    public static HookDecision allow() {
        return new HookDecision(true, null);
    }

    public static HookDecision block(String reason) {
        String message = reason == null || reason.isBlank() ? "hook rejected this operation" : reason;
        return new HookDecision(false, message);
    }
}
