package com.yucli.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yucli.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, PluginInfo> plugins = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final Path configDir;
    private final Path pluginsDir;
    private final Path stateFile;
    private final Map<String, Boolean> persistedState = new ConcurrentHashMap<>();

    public PluginManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.configDir = Path.of(System.getProperty("user.home"), ".YuCLI");
        this.pluginsDir = configDir.resolve("plugins");
        this.stateFile = configDir.resolve("plugins.json");
    }

    public PluginManager(ToolRegistry toolRegistry, Path configDir) {
        this.toolRegistry = toolRegistry;
        this.configDir = configDir;
        this.pluginsDir = configDir.resolve("plugins");
        this.stateFile = configDir.resolve("plugins.json");
    }

    public void loadAll() {
        loadPersistedState();
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            log.warn("创建插件目录失败: {}", e.getMessage());
            return;
        }

        try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    loadPlugin(jar.toString());
                } catch (Exception e) {
                    log.warn("加载插件失败 {}: {}", jar.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描插件目录失败: {}", e.getMessage());
        }

        for (PluginInfo info : plugins.values()) {
            if (info.state() == PluginState.LOADED && shouldEnable(info.instance().name())) {
                enablePlugin(info.instance().name());
            }
        }
    }

    public void loadPlugin(String jarPath) {
        Path path = Path.of(jarPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("插件 JAR 不存在: " + jarPath);
        }

        try {
            URL jarUrl = path.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jarUrl}, getClass().getClassLoader());

            ServiceLoader<YuPlugin> loader = ServiceLoader.load(YuPlugin.class, classLoader);
            Iterator<YuPlugin> iterator = loader.iterator();

            if (!iterator.hasNext()) {
                classLoader.close();
                throw new IllegalArgumentException("JAR 中未发现 YuPlugin 实现: " + jarPath);
            }

            boolean anyRegistered = false;
            while (iterator.hasNext()) {
                YuPlugin plugin = iterator.next();
                String name = plugin.name();
                if (plugins.containsKey(name)) {
                    log.warn("插件 {} 已加载，跳过重复加载", name);
                    continue;
                }

                PluginContext context = new PluginContext(toolRegistry, configDir, name);
                plugin.onLoad(context);

                PluginInfo info = new PluginInfo(plugin, PluginState.LOADED, jarPath, classLoader);
                plugins.put(name, info);
                anyRegistered = true;
                log.info("插件已加载: {} v{}", name, plugin.version());
            }
            if (!anyRegistered) {
                classLoader.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("加载插件失败: " + jarPath + " - " + e.getMessage(), e);
        }
    }

    public void enablePlugin(String name) {
        PluginInfo info = plugins.get(name);
        if (info == null) {
            throw new IllegalArgumentException("插件不存在: " + name);
        }
        if (info.state() == PluginState.ENABLED) {
            return;
        }

        try {
            info.instance().onEnable();
            info.setState(PluginState.ENABLED);
            persistedState.put(name, true);
            savePersistedState();
            log.info("插件已启用: {}", name);
        } catch (Exception e) {
            info.setState(PluginState.ERROR);
            log.error("启用插件失败 {}: {}", name, e.getMessage());
            throw new RuntimeException("启用插件失败: " + name, e);
        }
    }

    public void disablePlugin(String name) {
        PluginInfo info = plugins.get(name);
        if (info == null) {
            throw new IllegalArgumentException("插件不存在: " + name);
        }
        if (info.state() == PluginState.DISABLED) {
            return;
        }

        try {
            info.instance().onDisable();
            info.setState(PluginState.DISABLED);
            persistedState.put(name, false);
            savePersistedState();
            toolRegistry.unregisterPluginTools("plugin__" + name + "__");
            log.info("插件已禁用: {}", name);
        } catch (Exception e) {
            info.setState(PluginState.ERROR);
            log.error("禁用插件失败 {}: {}", name, e.getMessage());
            throw new RuntimeException("禁用插件失败: " + name, e);
        }
    }

    public void unloadPlugin(String name) {
        PluginInfo info = plugins.get(name);
        if (info == null) {
            throw new IllegalArgumentException("插件不存在: " + name);
        }

        try {
            if (info.state() == PluginState.ENABLED) {
                info.instance().onDisable();
            }
            info.instance().onUnload();
            plugins.remove(name);

            // Only close classLoader if no other plugins share it
            boolean shared = plugins.values().stream()
                    .anyMatch(p -> p.classLoader() == info.classLoader());
            if (!shared) {
                info.classLoader().close();
            }
            log.info("插件已卸载: {}", name);
        } catch (Exception e) {
            log.warn("卸载插件失败 {}: {}", name, e.getMessage());
            plugins.remove(name);
        }
    }

    public void reloadAll() {
        for (String name : new ArrayList<>(plugins.keySet())) {
            unloadPlugin(name);
        }
        loadAll();
    }

    public List<PluginInfo> listPlugins() {
        return List.copyOf(plugins.values());
    }

    public PluginInfo getPlugin(String name) {
        return plugins.get(name);
    }

    private boolean shouldEnable(String name) {
        Boolean enabled = persistedState.get(name);
        return enabled == null || enabled;
    }

    @SuppressWarnings("unchecked")
    private void loadPersistedState() {
        if (!Files.exists(stateFile)) {
            return;
        }
        try {
            Map<String, Boolean> state = mapper.readValue(stateFile.toFile(), Map.class);
            persistedState.clear();
            persistedState.putAll(state);
        } catch (IOException e) {
            log.warn("加载插件状态文件失败: {}", e.getMessage());
        }
    }

    private void savePersistedState() {
        try {
            Files.createDirectories(stateFile.getParent());
            mapper.writeValue(stateFile.toFile(), persistedState);
        } catch (IOException e) {
            log.warn("保存插件状态文件失败: {}", e.getMessage());
        }
    }
}
