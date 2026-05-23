package com.yucli.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseSkillContent_valid() {
        String content = """
                ---
                name: web-access
                description: 联网访问决策手册
                triggers: [web, 搜索, browser]
                ---

                # Web Access
                使用 web_search 查找信息。
                """;

        Skill skill = SkillLoader.parseSkillContent(content, tempDir);
        assertNotNull(skill);
        assertEquals("web-access", skill.name());
        assertEquals("联网访问决策手册", skill.description());
        assertEquals(List.of("web", "搜索", "browser"), skill.triggers());
        assertTrue(skill.body().contains("使用 web_search"));
        assertTrue(skill.enabled());
    }

    @Test
    void testParseSkillContent_noFrontmatter() {
        Skill skill = SkillLoader.parseSkillContent("没有 frontmatter", tempDir);
        assertNull(skill);
    }

    @Test
    void testParseSkillContent_emptyTriggers() {
        String content = """
                ---
                name: test
                description: test desc
                triggers: []
                ---
                body
                """;
        Skill skill = SkillLoader.parseSkillContent(content, tempDir);
        // triggers 为空时应返回 null（无法匹配任何东西的 skill 无意义）
        assertNull(skill);
    }

    @Test
    void testLoadFromDirectory() throws Exception {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: test-skill
                description: 测试 Skill
                triggers: [test]
                ---
                测试内容
                """);

        List<Skill> skills = SkillLoader.loadFromDirectory(tempDir);
        assertEquals(1, skills.size());
        assertEquals("test-skill", skills.get(0).name());
    }

    @Test
    void testLoadFromDirectory_missingSkillMd() throws Exception {
        Path skillDir = tempDir.resolve("empty-dir");
        Files.createDirectories(skillDir);

        List<Skill> skills = SkillLoader.loadFromDirectory(tempDir);
        assertTrue(skills.isEmpty());
    }

    @Test
    void testLoadFromDirectory_nonExistent() {
        List<Skill> skills = SkillLoader.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertTrue(skills.isEmpty());
    }
}
