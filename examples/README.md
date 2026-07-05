# YuCLI Examples

这些示例默认不会被 YuCLI 自动加载。需要使用时，把对应文件复制到项目级 `.YuCLI/` 目录或用户级 `~/.YuCLI/` 目录。

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
