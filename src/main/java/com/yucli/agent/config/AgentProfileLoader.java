package com.yucli.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class AgentProfileLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path userProfilesDir;
    private final Path projectProfilesDir;

    public AgentProfileLoader(Path projectDir) {
        this(
                Path.of(System.getProperty("user.home"), ".YuCLI", "agents"),
                projectDir.resolve(".YuCLI").resolve("agents")
        );
    }

    public AgentProfileLoader(Path userProfilesDir, Path projectProfilesDir) {
        this.userProfilesDir = userProfilesDir;
        this.projectProfilesDir = projectProfilesDir;
    }

    /**
     * Loads user-level profiles first, then project-level profiles.
     * Profiles with the same name are overridden by project-level definitions.
     */
    public Map<String, AgentProfile> load() throws IOException {
        Map<String, AgentProfile> profiles = new LinkedHashMap<>();
        loadDirectory(userProfilesDir, profiles);
        loadDirectory(projectProfilesDir, profiles);
        return profiles;
    }

    private void loadDirectory(Path dir, Map<String, AgentProfile> profiles) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException("agent profiles path is not a directory: " + dir);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isJsonFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        for (Path file : files) {
            AgentProfile profile = readProfile(file);
            profiles.put(profile.getName(), profile);
        }
    }

    private AgentProfile readProfile(Path file) throws IOException {
        try {
            AgentProfile profile = MAPPER.readValue(file.toFile(), AgentProfile.class);
            profile.setSourcePath(file.toAbsolutePath().normalize());
            profile.validate();
            return profile;
        } catch (Exception e) {
            throw new IOException("invalid agent profile " + file + ": " + rootMessage(e), e);
        }
    }

    private boolean isJsonFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
