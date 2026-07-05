package com.yucli.policy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PermissionProfileLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PermissionProfileLoader() {
    }

    public static PermissionProfile loadDefault(Path projectRoot) throws IOException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot cannot be null");
        }
        Path userPath = Path.of(System.getProperty("user.home"), ".YuCLI", "permissions.json");
        Path projectPath = projectRoot.resolve(".YuCLI").resolve("permissions.json");
        return load(userPath, projectPath);
    }

    public static PermissionProfile load(Path userPath, Path projectPath) throws IOException {
        MergedPermissionConfig merged = new MergedPermissionConfig();
        List<Path> sources = new ArrayList<>();

        loadInto(userPath, merged, sources);
        loadInto(projectPath, merged, sources);

        if (sources.isEmpty()) {
            return PermissionProfile.defaultProfile();
        }
        return new PermissionProfile(merged.mode, merged.allow, merged.deny, merged.ask, sources);
    }

    private static void loadInto(Path path, MergedPermissionConfig merged, List<Path> sources) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("permission profile path is not a file: " + path);
        }

        PermissionConfig config = readConfig(path);
        if (config.mode != null && !config.mode.isBlank()) {
            merged.mode = config.mode.trim();
        }
        appendRules(merged.allow, config.allow);
        appendRules(merged.deny, config.deny);
        appendRules(merged.ask, config.ask);
        sources.add(path.toAbsolutePath().normalize());
    }

    private static PermissionConfig readConfig(Path path) throws IOException {
        try {
            return MAPPER.readValue(path.toFile(), PermissionConfig.class);
        } catch (Exception e) {
            throw new IOException("invalid permission profile " + path + ": " + rootMessage(e), e);
        }
    }

    private static void appendRules(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String rule : source) {
            if (rule != null && !rule.isBlank()) {
                target.add(rule.trim());
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static final class MergedPermissionConfig {
        private String mode = PermissionProfile.DEFAULT_MODE;
        private final List<String> allow = new ArrayList<>();
        private final List<String> deny = new ArrayList<>();
        private final List<String> ask = new ArrayList<>();
    }

    private static final class PermissionConfig {
        public String mode;
        public List<String> allow;
        public List<String> deny;
        public List<String> ask;
    }
}
