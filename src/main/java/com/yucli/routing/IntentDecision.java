package com.yucli.routing;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record IntentDecision(
        Set<String> suggestedTools,
        boolean needsWrite,
        boolean needsCommand,
        boolean needsWeb,
        boolean needsBrowser,
        boolean needsMcp,
        boolean needsPlugin,
        String risk,
        double confidence,
        String reason
) {
    public IntentDecision {
        suggestedTools = suggestedTools == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(suggestedTools));
        risk = normalizeRisk(risk);
        reason = reason == null ? "" : reason.trim();
        confidence = Math.max(0, Math.min(1, confidence));
    }

    public static IntentDecision empty() {
        return new IntentDecision(Set.of(), false, false, false, false,
                false, false, "low", 0, "");
    }

    private static String normalizeRisk(String value) {
        if (value == null || value.isBlank()) {
            return "low";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "deny", "high", "medium", "low" -> normalized;
            default -> "low";
        };
    }
}
