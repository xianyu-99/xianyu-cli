package com.yucli.policy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PermissionProfile {
    public static final String DEFAULT_MODE = "default";

    private final String mode;
    private final List<String> allow;
    private final List<String> deny;
    private final List<String> ask;
    private final List<Path> sourcePaths;
    private final List<PermissionRule> allowRules;
    private final List<PermissionRule> denyRules;
    private final List<PermissionRule> askRules;

    public PermissionProfile(String mode, List<String> allow, List<String> deny, List<String> ask) {
        this(mode, allow, deny, ask, List.of());
    }

    PermissionProfile(String mode, List<String> allow, List<String> deny, List<String> ask, List<Path> sourcePaths) {
        this.mode = normalizeMode(mode);
        this.allow = normalizeRules(allow);
        this.deny = normalizeRules(deny);
        this.ask = normalizeRules(ask);
        this.sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        this.allowRules = compileRules(this.allow);
        this.denyRules = compileRules(this.deny);
        this.askRules = compileRules(this.ask);
    }

    public static PermissionProfile defaultProfile() {
        return new PermissionProfile(DEFAULT_MODE, List.of(), List.of(), List.of());
    }

    public String mode() {
        return mode;
    }

    public List<String> allow() {
        return allow;
    }

    public List<String> deny() {
        return deny;
    }

    public List<String> ask() {
        return ask;
    }

    public List<Path> sourcePaths() {
        return sourcePaths;
    }

    public PermissionProfileDecision decision(String toolName, String argumentsJson) {
        PermissionRule denyMatch = firstMatch(denyRules, toolName, argumentsJson);
        if (denyMatch != null) {
            return PermissionProfileDecision.deny("命中 deny 规则: " + denyMatch.raw());
        }

        PermissionRule allowMatch = firstMatch(allowRules, toolName, argumentsJson);
        if (allowMatch != null) {
            return PermissionProfileDecision.allow("命中 allow 规则: " + allowMatch.raw());
        }

        PermissionRule askMatch = firstMatch(askRules, toolName, argumentsJson);
        if (askMatch != null) {
            return PermissionProfileDecision.ask("命中 ask 规则: " + askMatch.raw());
        }

        return PermissionProfileDecision.ask("未命中权限规则，交由 HITL/默认策略处理");
    }

    public String statusText() {
        List<String> lines = new ArrayList<>();
        lines.add("权限配置: mode=" + mode);
        lines.add("来源: " + formatSources());
        lines.add("allow(" + allow.size() + "): " + formatRules(allow));
        lines.add("deny(" + deny.size() + "): " + formatRules(deny));
        lines.add("ask(" + ask.size() + "): " + formatRules(ask));
        lines.add("默认: 未命中规则返回 ASK，交由 HITL/现有策略继续处理");
        return String.join(System.lineSeparator(), lines);
    }

    private static PermissionRule firstMatch(List<PermissionRule> rules, String toolName, String argumentsJson) {
        for (PermissionRule rule : rules) {
            if (rule.matches(toolName, argumentsJson)) {
                return rule;
            }
        }
        return null;
    }

    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? DEFAULT_MODE : mode.trim();
    }

    private static List<String> normalizeRules(List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            normalized.add(rule.trim());
        }
        return List.copyOf(normalized);
    }

    private static List<PermissionRule> compileRules(List<String> rules) {
        if (rules.isEmpty()) {
            return List.of();
        }
        List<PermissionRule> compiled = new ArrayList<>();
        for (String rule : rules) {
            compiled.add(PermissionRule.parse(rule));
        }
        return List.copyOf(compiled);
    }

    private String formatSources() {
        if (sourcePaths.isEmpty()) {
            return "默认配置";
        }
        List<String> values = new ArrayList<>();
        for (Path path : sourcePaths) {
            values.add(path.toString());
        }
        return String.join(", ", values);
    }

    private static String formatRules(List<String> rules) {
        return rules.isEmpty() ? "无" : String.join(", ", rules);
    }

    private static final class PermissionRule {
        private final String raw;
        private final String toolPattern;
        private final String argumentPattern;
        private final Pattern argumentRegex;

        private PermissionRule(String raw, String toolPattern, String argumentPattern) {
            this.raw = raw;
            this.toolPattern = toolPattern == null || toolPattern.isBlank() ? "*" : toolPattern.trim();
            this.argumentPattern = argumentPattern == null || argumentPattern.isBlank() ? null : argumentPattern.trim();
            this.argumentRegex = this.argumentPattern == null
                    ? null
                    : Pattern.compile(globToRegex(this.argumentPattern), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        }

        static PermissionRule parse(String raw) {
            int separator = raw.indexOf(':');
            if (separator < 0) {
                return new PermissionRule(raw, raw, null);
            }
            String toolPattern = raw.substring(0, separator);
            String argumentPattern = raw.substring(separator + 1);
            return new PermissionRule(raw, toolPattern, argumentPattern);
        }

        String raw() {
            return raw;
        }

        boolean matches(String toolName, String argumentsJson) {
            if (!matchesTool(toolName)) {
                return false;
            }
            if (argumentRegex == null) {
                return true;
            }
            String arguments = argumentsJson == null ? "" : argumentsJson;
            return argumentRegex.matcher(arguments).find();
        }

        private boolean matchesTool(String toolName) {
            String name = toolName == null ? "" : toolName.trim();
            if ("*".equals(toolPattern)) {
                return true;
            }
            if (toolPattern.endsWith("*")) {
                return name.startsWith(toolPattern.substring(0, toolPattern.length() - 1));
            }
            return name.equals(toolPattern);
        }

        private static String globToRegex(String value) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '*') {
                    regex.append(".*");
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            return regex.toString();
        }
    }
}
