package com.yucli.hook;

import java.util.Arrays;
import java.util.Locale;

public enum HookEvent {
    PRE_TOOL_USE("PreToolUse"),
    POST_TOOL_USE("PostToolUse");

    private final String configName;

    HookEvent(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public static HookEvent fromConfigName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("hook event is required");
        }
        String normalized = value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(event -> event.configName.replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported hook event: " + value));
    }
}
