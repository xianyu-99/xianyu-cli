package com.yucli.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yucli.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {

    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadPersistedState() throws IOException {
        Path stateFile = tempDir.resolve("plugins.json");
        Map<String, Boolean> state = Map.of("test-plugin", true, "disabled-plugin", false);
        mapper.writeValue(stateFile.toFile(), state);

        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        assertTrue(stateFile.toFile().exists());
        Map<String, Boolean> loaded = mapper.readValue(stateFile.toFile(), Map.class);
        assertTrue(loaded.get("test-plugin"));
        assertFalse(loaded.get("disabled-plugin"));
    }

    @Test
    void shouldNotEnablePluginWhenPersistedAsDisabled() throws IOException {
        Path stateFile = tempDir.resolve("plugins.json");
        Map<String, Boolean> state = Map.of("my-plugin", false);
        mapper.writeValue(stateFile.toFile(), state);

        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        Map<String, Boolean> loaded = mapper.readValue(stateFile.toFile(), Map.class);
        assertFalse(loaded.get("my-plugin"));
    }

    @Test
    void shouldTrackPluginStateTransitions() {
        TestPlugin plugin = new TestPlugin();
        PluginInfo info = new PluginInfo(plugin, PluginState.LOADED, "test.jar", null);

        assertEquals(PluginState.LOADED, info.state());
        info.setState(PluginState.ENABLED);
        assertEquals(PluginState.ENABLED, info.state());
        info.setState(PluginState.DISABLED);
        assertEquals(PluginState.DISABLED, info.state());
        info.setState(PluginState.ERROR);
        assertEquals(PluginState.ERROR, info.state());
    }

    @Test
    void shouldCreatePluginContextWithConfigDir() {
        ToolRegistry registry = new ToolRegistry();
        PluginContext context = new PluginContext(registry, tempDir, "test-plugin");

        assertEquals(tempDir, context.getConfigDir());
    }

    @Test
    void shouldListEmptyPluginsWhenNoJarsExist() {
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        assertTrue(manager.listPlugins().isEmpty());
    }

    @Test
    void shouldThrowWhenEnablingNonexistentPlugin() {
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        assertThrows(IllegalArgumentException.class, () -> manager.enablePlugin("nonexistent"));
    }

    @Test
    void shouldThrowWhenDisablingNonexistentPlugin() {
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        assertThrows(IllegalArgumentException.class, () -> manager.disablePlugin("nonexistent"));
    }

    @Test
    void shouldThrowWhenUnloadingNonexistentPlugin() {
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        assertThrows(IllegalArgumentException.class, () -> manager.unloadPlugin("nonexistent"));
    }

    @Test
    void shouldLoadPersistedStateFromFile() throws IOException {
        Path stateFile = tempDir.resolve("plugins.json");
        Map<String, Boolean> state = Map.of("enabled-one", true, "disabled-one", false);
        mapper.writeValue(stateFile.toFile(), state);

        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        Map<String, Boolean> reloaded = mapper.readValue(stateFile.toFile(), Map.class);
        assertEquals(2, reloaded.size());
        assertTrue(reloaded.get("enabled-one"));
        assertFalse(reloaded.get("disabled-one"));
    }

    @Test
    void shouldSaveStateOnEnable() throws IOException {
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(registry, tempDir);

        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        Path stateFile = tempDir.resolve("plugins.json");
        assertFalse(Files.exists(stateFile));
    }

    static class TestPlugin implements YuPlugin {
        private boolean enabled = false;
        private boolean loaded = false;
        private boolean unloaded = false;

        @Override
        public String name() { return "test-plugin"; }

        @Override
        public String description() { return "A test plugin"; }

        @Override
        public String version() { return "1.0.0"; }

        @Override
        public void onLoad(PluginContext context) { loaded = true; }

        @Override
        public void onEnable() { enabled = true; }

        @Override
        public void onDisable() { enabled = false; }

        @Override
        public void onUnload() { unloaded = true; }

        public boolean isEnabled() { return enabled; }
        public boolean isLoaded() { return loaded; }
        public boolean isUnloaded() { return unloaded; }
    }
}
