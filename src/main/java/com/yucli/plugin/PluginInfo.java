package com.yucli.plugin;

import java.net.URLClassLoader;
import java.util.List;

public class PluginInfo {
    private final YuPlugin instance;
    private PluginState state;
    private final String jarPath;
    private final URLClassLoader classLoader;
    private final List<PluginContext.ToolDeclaration> toolDeclarations;
    private final boolean toolDeclarationsCaptured;

    public PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader) {
        this(instance, state, jarPath, classLoader, List.of(), false);
    }

    PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader,
               List<PluginContext.ToolDeclaration> toolDeclarations) {
        this(instance, state, jarPath, classLoader, toolDeclarations, true);
    }

    private PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader,
                       List<PluginContext.ToolDeclaration> toolDeclarations, boolean toolDeclarationsCaptured) {
        this.instance = instance;
        this.state = state;
        this.jarPath = jarPath;
        this.classLoader = classLoader;
        this.toolDeclarations = toolDeclarations == null ? List.of() : List.copyOf(toolDeclarations);
        this.toolDeclarationsCaptured = toolDeclarationsCaptured;
    }

    public YuPlugin instance() { return instance; }
    public PluginState state() { return state; }
    public String jarPath() { return jarPath; }
    public URLClassLoader classLoader() { return classLoader; }
    List<PluginContext.ToolDeclaration> toolDeclarations() { return toolDeclarations; }
    boolean toolDeclarationsCaptured() { return toolDeclarationsCaptured; }

    void setState(PluginState state) { this.state = state; }
}
