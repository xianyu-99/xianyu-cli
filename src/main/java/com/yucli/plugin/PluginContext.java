package com.yucli.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.yucli.tool.ToolRegistry;
import com.yucli.web.SearchProvider;

import java.nio.file.Path;

public class PluginContext {
    private final ToolRegistry toolRegistry;
    private final Path configDir;
    private final String pluginName;

    public PluginContext(ToolRegistry toolRegistry, Path configDir, String pluginName) {
        this.toolRegistry = toolRegistry;
        this.configDir = configDir;
        this.pluginName = pluginName;
    }

    public void registerTool(String name, String description, JsonNode parameters, ToolExecutor executor) {
        toolRegistry.registerPluginTool(pluginName, name, description, parameters, executor);
    }

    public void registerSearchProvider(SearchProvider provider) {
        toolRegistry.setSearchProvider(provider);
    }

    public Path getConfigDir() {
        return configDir;
    }
}
