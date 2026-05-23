package com.yucli.skill;

import java.nio.file.Path;
import java.util.*;

/**
 * Skill 注册中心：管理所有已加载 Skill 的启用/禁用/查询。
 */
public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Set<String> disabled = new HashSet<>();

    public SkillRegistry() {
        // 加载内置 skill
        List<Skill> builtins = SkillLoader.loadBuiltinSkills();
        for (Skill s : builtins) {
            skills.put(s.name(), s);
        }
    }

    /**
     * 从用户目录加载自定义 skill。
     */
    public void loadUserSkills(Path userSkillsDir) {
        List<Skill> loaded = SkillLoader.loadFromDirectory(userSkillsDir);
        for (Skill s : loaded) {
            // 用户 skill 可以覆盖内置 skill
            skills.put(s.name(), s);
        }
    }

    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    public List<Skill> getEnabledSkills() {
        return skills.values().stream()
                .filter(s -> !disabled.contains(s.name()))
                .toList();
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public boolean isEnabled(String name) {
        return skills.containsKey(name) && !disabled.contains(name);
    }

    public void enable(String name) {
        disabled.remove(name);
    }

    public void disable(String name) {
        if (skills.containsKey(name)) {
            disabled.add(name);
        }
    }

    public void reload() {
        skills.clear();
        disabled.clear();
        List<Skill> builtins = SkillLoader.loadBuiltinSkills();
        for (Skill s : builtins) {
            skills.put(s.name(), s);
        }
    }

    /**
     * 生成用于注入 system prompt 的 metadata 文本。
     */
    public String buildMetadataSection() {
        List<Skill> enabled = getEnabledSkills();
        if (enabled.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n可用 Skill（当用户输入包含触发词时自动展开）：\n");
        for (Skill s : enabled) {
            sb.append(s.toMetadataLine()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 检查用户输入是否命中任何启用的 Skill，返回展开的指令文本。
     * 如有多个命中，按注册顺序拼接。
     */
    public String expandMatchingSkills(String userInput) {
        StringBuilder sb = new StringBuilder();
        for (Skill s : getEnabledSkills()) {
            if (s.matches(userInput)) {
                sb.append(s.toExpandedInstruction()).append("\n\n");
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    public String getStatusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("已加载 ").append(skills.size()).append(" 个 Skill:\n");
        for (Skill s : skills.values()) {
            String status = disabled.contains(s.name()) ? "❌ 禁用" : "✅ 启用";
            sb.append(String.format("  %s %s — %s%n", status, s.name(), s.description()));
        }
        return sb.toString();
    }
}
