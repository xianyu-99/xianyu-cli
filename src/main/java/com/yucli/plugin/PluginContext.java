package com.yucli.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.yucli.tool.ToolRegistry;
import com.yucli.web.SearchProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PluginContext {
    private final ToolRegistry toolRegistry;
    private final Path configDir;
    private final String pluginName;
    private final boolean registerImmediately;
    private final List<ToolDeclaration> toolDeclarations = new ArrayList<>();

    public PluginContext(ToolRegistry toolRegistry, Path configDir, String pluginName) {
        this(toolRegistry, configDir, pluginName, true);
    }

    private PluginContext(ToolRegistry toolRegistry, Path configDir, String pluginName, boolean registerImmediately) {
        this.toolRegistry = toolRegistry;
        this.configDir = configDir;
        this.pluginName = pluginName;
        this.registerImmediately = registerImmediately;
    }

    static PluginContext deferred(ToolRegistry toolRegistry, Path configDir, String pluginName) {
        return new PluginContext(toolRegistry, configDir, pluginName, false);
    }

    public void registerTool(String name, String description, JsonNode parameters, ToolExecutor executor) {
        ToolDeclaration declaration = new ToolDeclaration(name, description, parameters, executor);
        toolDeclarations.add(declaration);
        if (registerImmediately) {
            toolRegistry.registerPluginTool(pluginName, name, description, parameters, executor);
        }
    }

    List<ToolDeclaration> toolDeclarations() {
        return List.copyOf(toolDeclarations);
    }

    public void registerSearchProvider(SearchProvider provider) {
        toolRegistry.setSearchProvider(provider);
    }

    public Path getConfigDir() {
        return configDir;
    }

    record ToolDeclaration(String name, String description, JsonNode parameters, ToolExecutor executor) {}
}
