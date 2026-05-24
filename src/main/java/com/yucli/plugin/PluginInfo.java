package com.yucli.plugin;

import java.net.URLClassLoader;

public class PluginInfo {
    private final YuPlugin instance;
    private PluginState state;
    private final String jarPath;
    private final URLClassLoader classLoader;

    public PluginInfo(YuPlugin instance, PluginState state, String jarPath, URLClassLoader classLoader) {
        this.instance = instance;
        this.state = state;
        this.jarPath = jarPath;
        this.classLoader = classLoader;
    }

    public YuPlugin instance() { return instance; }
    public PluginState state() { return state; }
    public String jarPath() { return jarPath; }
    public URLClassLoader classLoader() { return classLoader; }

    void setState(PluginState state) { this.state = state; }
}
