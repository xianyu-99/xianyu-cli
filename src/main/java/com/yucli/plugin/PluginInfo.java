package com.yucli.plugin;

import com.yucli.web.SearchProvider;

import java.net.URLClassLoader;
import java.util.List;

public class PluginInfo {
    private final YuPlugin instance;
    private PluginState state;
    private final String jarPath;
    private final URLClassLoader classLoader;
    private final List<PluginContext.ToolDeclaration> toolDeclarations;
    private final SearchProvider searchProvider;
    private final boolean toolDeclarationsCaptured;

    public PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader) {
        this(instance, state, jarPath, classLoader, List.of(), null, false);
    }

    PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader,
               List<PluginContext.ToolDeclaration> toolDeclarations) {
        this(instance, state, jarPath, classLoader, toolDeclarations, null, true);
    }

    PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader,
               List<PluginContext.ToolDeclaration> toolDeclarations, SearchProvider searchProvider) {
        this(instance, state, jarPath, classLoader, toolDeclarations, searchProvider, true);
    }

    private PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader,
                       List<PluginContext.ToolDeclaration> toolDeclarations, SearchProvider searchProvider,
                       boolean toolDeclarationsCaptured) {
        this.instance = instance;
        this.state = state;
        this.jarPath = jarPath;
        this.classLoader = classLoader;
        this.toolDeclarations = toolDeclarations == null ? List.of() : List.copyOf(toolDeclarations);
        this.searchProvider = searchProvider;
        this.toolDeclarationsCaptured = toolDeclarationsCaptured;
    }

    public YuPlugin instance() { return instance; }
    public PluginState state() { return state; }
    public String jarPath() { return jarPath; }
    public URLClassLoader classLoader() { return classLoader; }
    List<PluginContext.ToolDeclaration> toolDeclarations() { return toolDeclarations; }
    SearchProvider searchProvider() { return searchProvider; }
    boolean toolDeclarationsCaptured() { return toolDeclarationsCaptured; }

    void setState(PluginState state) { this.state = state; }
}
