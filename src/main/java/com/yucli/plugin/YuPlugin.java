package com.yucli.plugin;

public interface YuPlugin {
    String name();
    String description();
    String version();
    void onLoad(PluginContext context);
    void onEnable();
    void onDisable();
    void onUnload();
}
