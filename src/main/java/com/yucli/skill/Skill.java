package com.yucli.skill;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 数据模型。
 *
 * 一个 Skill 是一个可复用的提示词单元，包含：
 * - 元数据（名称、描述、触发词列表）
 * - 指令体（Markdown 格式，触发时注入对话）
 * - 可选的 scripts/ 和 references/ 子目录
 */
public record Skill(
        String name,
        String description,
        List<String> triggers,
        String body,
        Path baseDir,
        boolean enabled
) {

    /**
     * 检查给定用户输入是否命中本 Skill 的任意触发词。
     */
    public boolean matches(String userInput) {
        if (userInput == null || triggers == null || triggers.isEmpty()) {
            return false;
        }
        String lower = userInput.toLowerCase();
        for (String trigger : triggers) {
            if (lower.contains(trigger.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成注入 system prompt 的 metadata 摘要（一行）。
     */
    public String toMetadataLine() {
        String triggerList = String.join("/", triggers);
        return String.format("- %s (%s) — 触发词: %s", name, description, triggerList);
    }

    /**
     * 生成触发时注入对话的完整指令文本。
     */
    public String toExpandedInstruction() {
        return String.format(
            "【Skill: %s】\n%s\n【Skill 结束】",
            name, body
        );
    }
}
