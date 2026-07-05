package com.yucli.runtime.headless;

import java.util.Arrays;

public enum HeadlessRunMode {
    REACT("react"),
    PLAN("plan"),
    TEAM("team");

    private final String value;

    HeadlessRunMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static HeadlessRunMode parse(String rawMode) {
        String normalized = rawMode == null || rawMode.isBlank()
                ? REACT.value
                : rawMode.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported headless mode: " + rawMode));
    }
}
