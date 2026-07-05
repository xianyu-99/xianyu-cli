# YuCLI Examples

这些示例默认不会被 YuCLI 自动加载。需要使用时，把对应文件复制到项目级 `.YuCLI/` 目录或用户级 `~/.YuCLI/` 目录，并先按自己的项目规则审阅。

## Agent Profiles

示例目录：`examples/agents/`

项目级启用：

```bash
mkdir -p .YuCLI/agents
cp examples/agents/*.json .YuCLI/agents/
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force .YuCLI\agents
Copy-Item examples\agents\*.json .YuCLI\agents\
```

## Hook Recipes

示例目录：`examples/hooks/`

项目级启用：

```bash
mkdir -p .YuCLI
cp examples/hooks/hooks.json .YuCLI/hooks.json
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force .YuCLI
Copy-Item examples\hooks\hooks.json .YuCLI\hooks.json
```

`hooks.json` 中的命令引用 `examples/hooks/scripts/`，所以推荐把整个 `examples/hooks/` 目录保留在项目内，或复制后同步调整命令路径。

## MCP Examples

示例目录：`examples/mcp/`

项目级启用：

```bash
mkdir -p .YuCLI
cp examples/mcp/mcp.json .YuCLI/mcp.json
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force .YuCLI
Copy-Item examples\mcp\mcp.json .YuCLI\mcp.json
```

示例 server 默认都带 `disabled: true`，避免第一次运行时自动启动 `npx` / `uvx` 或访问远程 HTTP server。使用前先移除目标 server 的 `disabled` 字段或执行 `/mcp enable <name>`。

## Skill Examples

示例目录：`examples/skills/`

用户级启用：

```bash
mkdir -p ~/.YuCLI/skills
cp -R examples/skills/code-review ~/.YuCLI/skills/
cp -R examples/skills/mcp-research ~/.YuCLI/skills/
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force $HOME\.YuCLI\skills
Copy-Item -Recurse examples\skills\code-review $HOME\.YuCLI\skills\
Copy-Item -Recurse examples\skills\mcp-research $HOME\.YuCLI\skills\
```

启用后运行 `/skill reload` 和 `/skill list` 检查加载结果。

## Plugin Template

示例目录：`examples/plugins/`

在 YuCLI 中执行：

```text
/plugin template demo-tools
```

该命令会在当前工作目录生成 `demo-tools-yucli-plugin/`，包含 Maven 工程、`YuPlugin` 实现、ServiceLoader 描述文件和一个 `echo` 示例工具。
