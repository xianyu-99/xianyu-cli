package com.yucli.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yucli.agent.AgentRole;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentProfile {
    private String name;
    private AgentRole role;
    private String instructions;
    private List<String> tools = new ArrayList<>();
    private List<String> allowedPaths = new ArrayList<>();
    private List<String> deniedCommands = new ArrayList<>();
    private String workingDirectory;
    private String model;
    private Path sourcePath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public AgentRole getRole() {
        return role;
    }

    @JsonProperty("role")
    public void setRole(String role) {
        this.role = parseRole(role);
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths == null ? new ArrayList<>() : new ArrayList<>(allowedPaths);
    }

    public List<String> getDeniedCommands() {
        return deniedCommands;
    }

    public void setDeniedCommands(List<String> deniedCommands) {
        this.deniedCommands = deniedCommands == null ? new ArrayList<>() : new ArrayList<>(deniedCommands);
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = trimToNull(workingDirectory);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = trimToNull(model);
    }

    @JsonIgnore
    public Path getSourcePath() {
        return sourcePath;
    }

    @JsonIgnore
    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    void validate() {
        if (name == null) {
            throw new IllegalArgumentException("agent profile name is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("agent profile role is required");
        }
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("agent profile instructions are required");
        }
        for (String tool : tools) {
            if (tool == null || tool.isBlank()) {
                throw new IllegalArgumentException("agent profile tools cannot contain blank values");
            }
        }
        validateNoBlank("allowedPaths", allowedPaths);
        validateNoBlank("deniedCommands", deniedCommands);
    }

    private void validateNoBlank(String fieldName, List<String> values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("agent profile " + fieldName + " cannot contain blank values");
            }
        }
    }

    private AgentRole parseRole(String role) {
        String normalized = trimToNull(role);
        if (normalized == null) {
            return null;
        }
        try {
            return AgentRole.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unsupported agent profile role: " + role + " (expected PLANNER, WORKER, REVIEWER)"
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
