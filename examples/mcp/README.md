# MCP Examples

这些示例是可复制后修改的起点。除非你把它们复制到 `.YuCLI/mcp.json` 或 `~/.YuCLI/mcp.json`，否则 YuCLI 不会自动加载。

`mcp.json` 里的所有 server 默认都带 `disabled: true`，避免第一次运行时自动启动 `npx` / `uvx` 或连接远程 HTTP server。

## 项目级启用

```bash
mkdir -p .YuCLI
cp examples/mcp/mcp.json .YuCLI/mcp.json
```

PowerShell：

```powershell
New-Item -ItemType Directory -Force .YuCLI
Copy-Item examples\mcp\mcp.json .YuCLI\mcp.json
```

复制后先审阅配置，移除目标 server 的 `disabled: true`，然后重启 YuCLI 或执行 `/mcp enable <name>`。

## 说明

- `command` + `args` 表示 stdio MCP server。
- `url` + `headers` 表示 Streamable HTTP MCP server。
- `${PROJECT_DIR}` 和 `${HOME}` 是内置变量。
- 其他 `${VAR}` 占位符从环境变量读取。
- OAuth 示例需要按 provider 填写 `clientId`、`scopes`、授权端点和 token 端点。
