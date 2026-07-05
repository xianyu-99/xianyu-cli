package com.yucli.plugin;

import com.yucli.ProductInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PluginTemplateGenerator {
    private static final Pattern DASH_RUN = Pattern.compile("-+");
    private static final Pattern NON_ID_CHAR = Pattern.compile("[^a-z0-9]+");

    private PluginTemplateGenerator() {
    }

    public static GeneratedTemplate generate(String rawName, Path outputRoot) throws IOException {
        String pluginId = normalizePluginId(rawName);
        Path root = outputRoot == null ? Path.of(".") : outputRoot;
        Path projectDir = root.resolve(pluginId + "-yucli-plugin").toAbsolutePath().normalize();
        ensureWritableTarget(projectDir);

        String packageName = "com.yucli.plugins." + packageSuffix(pluginId);
        String className = className(pluginId);
        String artifactId = pluginId + "-yucli-plugin";
        String packagePath = packageName.replace('.', '/');
        Path javaDir = projectDir.resolve("src/main/java").resolve(packagePath);
        Path servicesDir = projectDir.resolve("src/main/resources/META-INF/services");

        Files.createDirectories(javaDir);
        Files.createDirectories(servicesDir);
        Files.writeString(projectDir.resolve("pom.xml"), pomXml(artifactId));
        Files.writeString(projectDir.resolve(".gitignore"), gitignore());
        Files.writeString(projectDir.resolve("README.md"), readme(pluginId, artifactId));
        Files.writeString(javaDir.resolve(className + ".java"), pluginJava(packageName, className, pluginId));
        Files.writeString(servicesDir.resolve(YuPlugin.class.getName()), packageName + "." + className + System.lineSeparator());

        return new GeneratedTemplate(projectDir, pluginId, packageName, className);
    }

    static String normalizePluginId(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Plugin name is required");
        }
        String normalized = NON_ID_CHAR.matcher(rawName.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
        normalized = DASH_RUN.matcher(normalized).replaceAll("-");
        normalized = trimDashes(normalized);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Plugin name must contain letters or numbers");
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "yucli-" + normalized;
        }
        return normalized;
    }

    private static void ensureWritableTarget(Path projectDir) throws IOException {
        if (!Files.exists(projectDir)) {
            return;
        }
        try (var entries = Files.list(projectDir)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalArgumentException("Target directory is not empty: " + projectDir);
            }
        }
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String packageSuffix(String pluginId) {
        List<String> parts = new ArrayList<>();
        for (String part : pluginId.split("-")) {
            if (part.isBlank()) {
                continue;
            }
            if (!Character.isLetter(part.charAt(0))) {
                parts.add("p" + part);
            } else {
                parts.add(part);
            }
        }
        return String.join(".", parts);
    }

    private static String className(String pluginId) {
        StringBuilder sb = new StringBuilder();
        for (String part : pluginId.split("-")) {
            if (part.isBlank()) {
                continue;
            }
            String normalized = Character.isLetter(part.charAt(0)) ? part : "p" + part;
            sb.append(Character.toUpperCase(normalized.charAt(0)));
            if (normalized.length() > 1) {
                sb.append(normalized.substring(1));
            }
        }
        if (!sb.toString().endsWith("Plugin")) {
            sb.append("Plugin");
        }
        return sb.toString();
    }

    private static String pomXml(String artifactId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.yucli.plugins</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.1.0</version>
                    <packaging>jar</packaging>

                    <properties>
                        <maven.compiler.release>17</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>com.yucli</groupId>
                            <artifactId>yucli</artifactId>
                            <version>%s</version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(artifactId, ProductInfo.VERSION);
    }

    private static String gitignore() {
        return """
                target/
                .idea/
                *.iml
                .classpath
                .project
                .settings/
                """;
    }

    private static String readme(String pluginId, String artifactId) {
        return """
                # %s

                这是通过 `/plugin template` 生成的最小 YuCLI 插件工程。

                ## 构建

                先在 YuCLI 源码仓库里把 YuCLI API 安装到本地 Maven 仓库：

                ```bash
                mvn -q -DskipTests install
                ```

                然后在当前插件工程里构建：

                ```bash
                mvn -q package
                ```

                ## 安装

                把生成的 jar 复制到 `~/.YuCLI/plugins/`，然后在 YuCLI 里重新加载插件：

                ```bash
                mkdir -p ~/.YuCLI/plugins
                cp target/%s-0.1.0.jar ~/.YuCLI/plugins/
                ```

                PowerShell：

                ```powershell
                New-Item -ItemType Directory -Force $HOME\\.YuCLI\\plugins
                Copy-Item target\\%s-0.1.0.jar $HOME\\.YuCLI\\plugins\\
                ```

                YuCLI 命令：

                ```text
                /plugin reload
                /plugin enable %s
                ```

                示例工具会注册为：`plugin__%s__echo`。
                """.formatted(pluginId, artifactId, artifactId, pluginId, pluginId);
    }

    private static String pluginJava(String packageName, String className, String pluginId) {
        return """
                package %s;

                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.node.ObjectNode;
                import com.yucli.plugin.PluginContext;
                import com.yucli.plugin.YuPlugin;

                public class %s implements YuPlugin {
                    private static final ObjectMapper MAPPER = new ObjectMapper();

                    @Override
                    public String name() {
                        return "%s";
                    }

                    @Override
                    public String description() {
                        return "Example YuCLI plugin generated from the template.";
                    }

                    @Override
                    public String version() {
                        return "0.1.0";
                    }

                    @Override
                    public void onLoad(PluginContext context) {
                        context.registerTool("echo", "Echo back the provided text.", echoSchema(), this::echo);
                    }

                    @Override
                    public void onEnable() {
                    }

                    @Override
                    public void onDisable() {
                    }

                    @Override
                    public void onUnload() {
                    }

                    private JsonNode echoSchema() {
                        ObjectNode root = MAPPER.createObjectNode();
                        root.put("type", "object");
                        ObjectNode properties = root.putObject("properties");
                        ObjectNode text = properties.putObject("text");
                        text.put("type", "string");
                        text.put("description", "Text to echo.");
                        root.putArray("required").add("text");
                        return root;
                    }

                    private String echo(JsonNode arguments) {
                        String text = arguments.path("text").asText("");
                        return "echo: " + text;
                    }
                }
                """.formatted(packageName, className, pluginId);
    }

    public record GeneratedTemplate(Path projectDir, String pluginId, String packageName, String className) {
    }
}
