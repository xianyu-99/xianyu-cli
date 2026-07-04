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
