package com.yucli.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTemplateGeneratorTest {

    @Test
    void generatesMavenServiceLoaderPluginTemplate(@TempDir Path tempDir) throws Exception {
        PluginTemplateGenerator.GeneratedTemplate generated =
                PluginTemplateGenerator.generate("Demo Tools", tempDir);

        Path projectDir = tempDir.resolve("demo-tools-yucli-plugin");
        assertEquals(projectDir.toAbsolutePath().normalize(), generated.projectDir());
        assertEquals("demo-tools", generated.pluginId());
        assertEquals("com.yucli.plugins.demo.tools", generated.packageName());
        assertEquals("DemoToolsPlugin", generated.className());

        Path javaFile = projectDir.resolve("src/main/java/com/yucli/plugins/demo/tools/DemoToolsPlugin.java");
        Path serviceFile = projectDir.resolve("src/main/resources/META-INF/services/com.yucli.plugin.YuPlugin");

        assertTrue(Files.isRegularFile(projectDir.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(projectDir.resolve("README.md")));
        assertTrue(Files.isRegularFile(javaFile));
        assertTrue(Files.isRegularFile(serviceFile));
        assertTrue(Files.readString(javaFile).contains("context.registerTool(\"echo\""));
        assertEquals("com.yucli.plugins.demo.tools.DemoToolsPlugin" + System.lineSeparator(),
                Files.readString(serviceFile));
    }

    @Test
    void refusesToOverwriteNonEmptyDirectory(@TempDir Path tempDir) throws Exception {
        Path existing = tempDir.resolve("demo-yucli-plugin");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("keep.txt"), "keep");

        assertThrows(IllegalArgumentException.class,
                () -> PluginTemplateGenerator.generate("demo", tempDir));
    }

    @Test
    void normalizesPluginIdForMavenAndPackageNames() {
        assertEquals("yucli-99-demo-plugin",
                PluginTemplateGenerator.normalizePluginId(" 99 Demo_Plugin! "));
    }
}
