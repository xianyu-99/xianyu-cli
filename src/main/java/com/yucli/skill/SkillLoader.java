package com.yucli.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Skill 加载器：扫描目录并解析 SKILL.md 文件。
 *
 * SKILL.md 格式：
 * <pre>
 * ---
 * name: web-access
 * description: 联网访问决策手册
 * triggers: [web, 搜索, browser]
 * ---
 *
 * # 指令正文
 * ...
 * </pre>
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
            // 静默忽略：skills 目录不存在或不可读时返回空列表
        }
        return skills;
    }

    /**
     * 从类路径资源目录加载内置 Skill。
     */
    public static List<Skill> loadBuiltinSkills() {
        try {
            Path tempDir = extractBuiltinSkillsToTemp();
            if (tempDir != null) {
                return loadFromDirectory(tempDir);
            }
        } catch (Exception e) {
            // 内置 skill 加载失败不阻断启动
        }
        return Collections.emptyList();
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
                // 支持 YAML 数组格式: [a, b, c]
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

    private static Path extractBuiltinSkillsToTemp() throws IOException {
        // 从 classpath 的 /skills/ 目录提取到临时目录
        java.net.URL url = SkillLoader.class.getResource("/skills/");
        if (url == null) {
            return null;
        }
        Path tempDir = Files.createTempDirectory("yucli-skills");
        // 如果 URL 是 file: 协议（开发时直接从文件系统读取）
        if ("file".equals(url.getProtocol())) {
            return Path.of(url.getPath());
        }
        // jar 内资源：递归提取
        // 简化处理：开发/测试时从文件系统读取；打包后需要额外处理
        return null;
    }
}
