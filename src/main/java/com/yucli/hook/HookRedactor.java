package com.yucli.hook;

import java.util.Map;
import java.util.regex.Pattern;

final class HookRedactor {
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)(\\b(?:authorization|api[_-]?key|token|password|secret|key)\\b\\s*[=:]\\s*)([^\\s,;}]+)");
    private static final Pattern SECRET_JSON = Pattern.compile(
            "(?i)(\"(?:authorization|api[_-]?key|token|password|secret|key)\"\\s*:\\s*\")([^\"]*)(\")");

    private HookRedactor() {
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = SECRET_JSON.matcher(value).replaceAll("$1***$3");
        return SECRET_PAIR.matcher(redacted).replaceAll("$1***");
    }

    static Map<String, String> redactHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> redacted = new java.util.LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key != null && isSecretKey(key)) {
                redacted.put(key, "***");
            } else if (key != null && value != null) {
                redacted.put(key, redact(value));
            }
        });
        return redacted;
    }

    private static boolean isSecretKey(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("key");
    }
}
