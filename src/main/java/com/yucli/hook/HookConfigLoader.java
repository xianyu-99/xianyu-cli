package com.yucli.hook;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HookConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final Map<String, String> environment;

    public HookConfigLoader() {
        this(System.getenv());
    }

    HookConfigLoader(Map<String, String> environment) {
        this.environment = environment == null ? Map.of() : new LinkedHashMap<>(environment);
    }

    public HookManager loadDefault(Path projectDir) {
        Path normalizedProjectDir = projectDir == null
                ? Path.of(System.getProperty("user.dir"))
                : projectDir;
        return load(normalizedProjectDir,
                Path.of(System.getProperty("user.home"), ".YuCLI", "hooks.json"),
                normalizedProjectDir.resolve(".YuCLI").resolve("hooks.json"));
    }

    public HookManager load(Path projectDir, Path... configFiles) {
        Map<HookEvent, List<HookDefinition>> merged = new EnumMap<>(HookEvent.class);
        if (configFiles != null) {
            for (Path configFile : configFiles) {
                mergeConfig(merged, configFile);
            }
        }
        return new HookManager(merged, projectDir);
    }

    private void mergeConfig(Map<HookEvent, List<HookDefinition>> merged, Path configFile) {
        if (configFile == null || !Files.exists(configFile)) {
            return;
        }
        try {
            HookConfig config = MAPPER.readValue(configFile.toFile(), HookConfig.class);
            Map<HookEvent, List<HookDefinition>> resolvedByEvent = new EnumMap<>(HookEvent.class);
            for (Map.Entry<String, List<HookDefinition>> entry : config.getHooks().entrySet()) {
                HookEvent event = HookEvent.fromConfigName(entry.getKey());
                List<HookDefinition> definitions = entry.getValue() == null ? List.of() : entry.getValue();
                resolvedByEvent.computeIfAbsent(event, ignored -> new ArrayList<>())
                        .addAll(resolveHttpPlaceholders(definitions));
            }
            for (Map.Entry<HookEvent, List<HookDefinition>> entry : resolvedByEvent.entrySet()) {
                merged.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[Hook] Failed to read hook config, ignored " + configFile + ": " + e.getMessage());
        }
    }

    private List<HookDefinition> resolveHttpPlaceholders(List<HookDefinition> definitions) {
        List<HookDefinition> resolved = new ArrayList<>();
        for (HookDefinition definition : definitions) {
            resolved.add(resolveHttpPlaceholders(definition));
        }
        return resolved;
    }

    private HookDefinition resolveHttpPlaceholders(HookDefinition definition) {
        if (definition == null) {
            return null;
        }
        definition.setUrl(resolveEnvPlaceholders(definition.getUrl()));
        definition.setUrls(resolveEnvPlaceholders(definition.getUrls()));
        definition.setHeaders(resolveHeaderPlaceholders(definition.getHeaders()));
        definition.setAuthToken(resolveEnvPlaceholders(definition.getAuthToken()));
        definition.setSignatureSecret(resolveEnvPlaceholders(definition.getSignatureSecret()));
        return definition;
    }

    private List<String> resolveEnvPlaceholders(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (String value : values) {
            resolved.add(resolveEnvPlaceholders(value));
        }
        return resolved;
    }

    private Map<String, String> resolveHeaderPlaceholders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((key, value) -> resolved.put(key, resolveEnvPlaceholders(value)));
        return resolved;
    }

    private String resolveEnvPlaceholders(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = environment.get(name);
            if (replacement == null) {
                throw new IllegalArgumentException(
                        "Missing environment variable " + name + " referenced by hook config");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
