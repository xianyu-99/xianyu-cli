# Skill Examples

这些示例展示 `SKILL.md` 的基本格式。除非复制到用户 Skill 目录，否则 YuCLI 不会自动加载。

## 用户级安装

```bash
mkdir -p ~/.YuCLI/skills
cp -R examples/skills/code-review ~/.YuCLI/skills/
cp -R examples/skills/mcp-research ~/.YuCLI/skills/
```

PowerShell：

```powershell
New-Item -ItemType Directory -Force $HOME\.YuCLI\skills
Copy-Item -Recurse examples\skills\code-review $HOME\.YuCLI\skills\
Copy-Item -Recurse examples\skills\mcp-research $HOME\.YuCLI\skills\
```

然后执行：

```text
/skill reload
/skill list
```

## 格式

每个 Skill 目录包含一个 `SKILL.md`：

```markdown
---
name: skill-name
description: Short description
triggers: [keyword, phrase]
---

Instructions injected when a prompt matches one of the triggers.
```
