package com.yucli.hook;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HookConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            for (Map.Entry<String, List<HookDefinition>> entry : config.getHooks().entrySet()) {
                HookEvent event = HookEvent.fromConfigName(entry.getKey());
                List<HookDefinition> definitions = entry.getValue() == null ? List.of() : entry.getValue();
                merged.computeIfAbsent(event, ignored -> new ArrayList<>()).addAll(definitions);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[Hook] Failed to read hook config, ignored " + configFile + ": " + e.getMessage());
        }
    }
}
