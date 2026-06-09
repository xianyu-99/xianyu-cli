package com.yucli.skill;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Skill 加载器：扫描目录或 classpath 资源并解析 SKILL.md 文件。
 */
public class SkillLoader {

    /**
     * 扫描 skillsDir 下的所有子目录，加载其中的 SKILL.md。
     */
    public static List<Skill> loadFromDirectory(Path skillsDir) {
        if (skillsDir == null || !Files.isDirectory(skillsDir)) {
            return Collections.emptyList();
        }
        List<Skill> skills = new ArrayList<>();
        try (Stream<Path> entries = Files.list(skillsDir)) {
            entries.filter(Files::isDirectory).forEach(dir -> {
                Skill skill = parseSkillDir(dir);
                if (skill != null) {
                    skills.add(skill);
                }
            });
        } catch (IOException e) {
            // skills 目录不存在或不可读时保持启动容错，返回空列表。
        }
        return skills;
    }

    /**
     * 从 classpath /skills 资源加载内置 Skill。
     */
    public static List<Skill> loadBuiltinSkills() {
        return loadBuiltinSkills(SkillLoader.class.getClassLoader());
    }

    static List<Skill> loadBuiltinSkills(ClassLoader classLoader) {
        if (classLoader == null) {
            return Collections.emptyList();
        }
        List<Skill> skills = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("skills");
            while (resources.hasMoreElements()) {
                skills.addAll(loadBuiltinSkillsFromUrl(resources.nextElement()));
            }
            if (skills.isEmpty() && classLoader instanceof URLClassLoader urlClassLoader) {
                skills.addAll(loadBuiltinSkillsFromUrlClassLoader(urlClassLoader));
            }
        } catch (Exception e) {
            // 内置 Skill 加载失败不阻断启动。
        }
        return skills;
    }

    private static Skill parseSkillDir(Path dir) {
        Path skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return null;
        }
        try {
            String content = Files.readString(skillFile);
            return parseSkillContent(content, dir);
        } catch (IOException e) {
            return null;
        }
    }

    static Skill parseSkillContent(String content, Path dir) {
        content = content.trim();
        if (!content.startsWith("---")) {
            return null;
        }

        int frontmatterEnd = content.indexOf("---", 3);
        if (frontmatterEnd == -1) {
            return null;
        }

        String frontmatter = content.substring(3, frontmatterEnd).trim();
        String body = content.substring(frontmatterEnd + 3).trim();

        String name = extractField(frontmatter, "name");
        String description = extractField(frontmatter, "description");
        List<String> triggers = extractListField(frontmatter, "triggers");

        if (name == null || name.isBlank() || triggers.isEmpty()) {
            return null;
        }

        return new Skill(name, description, triggers, body, dir, true);
    }

    private static String extractField(String frontmatter, String key) {
        String prefix = key + ":";
        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private static List<String> extractListField(String frontmatter, String key) {
        String prefix = key + ":";
        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).trim();
                if (value.startsWith("[") && value.endsWith("]")) {
                    value = value.substring(1, value.length() - 1);
                }
                List<String> result = new ArrayList<>();
                for (String item : value.split(",")) {
                    item = item.trim();
                    if (!item.isEmpty()) {
                        result.add(item);
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    private static List<Skill> loadBuiltinSkillsFromUrl(URL url) throws IOException, URISyntaxException {
        if ("file".equals(url.getProtocol())) {
            return loadFromDirectory(Path.of(url.toURI()));
        }
        if ("jar".equals(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            String root = connection.getEntryName();
            if (root == null || root.isBlank()) {
                root = "skills";
            }
            return loadFromJar(connection.getJarFile(), root);
        }
        return Collections.emptyList();
    }

    private static List<Skill> loadBuiltinSkillsFromUrlClassLoader(URLClassLoader classLoader)
            throws IOException, URISyntaxException {
        List<Skill> skills = new ArrayList<>();
        for (URL url : classLoader.getURLs()) {
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Path path = Path.of(url.toURI());
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(path.toFile())) {
                    skills.addAll(loadFromJar(jarFile, "skills"));
                }
            } else if (Files.isDirectory(path.resolve("skills"))) {
                skills.addAll(loadFromDirectory(path.resolve("skills")));
            }
        }
        return skills;
    }

    private static List<Skill> loadFromJar(JarFile jarFile, String root) throws IOException {
        String prefix = root.endsWith("/") ? root : root + "/";
        List<Skill> skills = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                continue;
            }

            String relative = entry.getName().substring(prefix.length());
            int slash = relative.indexOf('/');
            if (slash <= 0 || !relative.substring(slash + 1).equals("SKILL.md")) {
                continue;
            }

            String skillDir = relative.substring(0, slash);
            try (var input = jarFile.getInputStream(entry)) {
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                Skill skill = parseSkillContent(content, Path.of("skills", skillDir));
                if (skill != null) {
                    skills.add(skill);
                }
            }
        }
        return skills;
    }
}
