# YuCLI

MCP-Native Agent CLI，对标 Claude Code。Java 17 实现，支持多模型、多 Agent 协作、MCP 协议、浏览器操控、插件系统。

```
╔══════════════════════════════════════════════════════════╗
║   ██████╗  █████╗ ██╗ ██████╗██╗     ██╗                ║
║   ██╔══██╗██╔══██╗██║██╔════╝██║     ██║                ║
║   ██████╔╝███████║██║██║     ██║     ██║                ║
║   ██╔═══╝ ██╔══██║██║██║     ██║     ██║                ║
║   ██║     ██║  ██║██║╚██████╗███████╗██║                ║
║   ╚═╝     ╚═╝  ╚═╝╚═╝ ╚═════╝╚══════╝╚═╝                ║
║      MCP-Native Agent CLI v19.0.0                       ║
╚══════════════════════════════════════════════════════════╝
```

## 快速开始

### 安装

```bash
# 克隆并编译
git clone https://github.com/xianyu-99/xianyu-cli.git
cd xianyu-cli
mvn clean package -DskipTests

# 全局安装（可选）
# Mac/Linux:
bash install.sh
# Windows:
powershell -ExecutionPolicy Bypass -File install.ps1
```

### 配置

```bash
# 方式一：配置文件 ~/.YuCLI/config.json
{
  "defaultProvider": "deepseek",
  "providers": {
    "deepseek": { "apiKey": "your-key" },
    "glm": { "apiKey": "your-key" },
    "anthropic": { "apiKey": "your-key" }
  }
}

# 方式二：环境变量
export DEEPSEEK_API_KEY=your-key
```

### 运行

```bash
java -jar target/yucli-19.0.0.jar
# 或全局安装后直接：
yucli
```

## 核心功能

**Agent 模式**
- ReAct 单代理循环（默认）
- Plan-and-Execute 计划模式（`/plan`）
- Multi-Agent 协作（`/team`）：规划者 + 执行者 + 检查者

**多模型支持**
- DeepSeek V4、GLM-5.1、Anthropic Claude
- 运行时切换：`/model deepseek`、`/model glm`
- Prompt Caching、流式输出、Token 统计

**MCP 协议**
- stdio 子进程 + Streamable HTTP 远程 server
- 工具自动注册为 `mcp__{server}__{tool}`
- Resources 双轨：模型自动调用 + 用户 `@server:protocol://path` 引用
- Prompts 查看与注入、通知被动响应
- OAuth 2.0 + PKCE 认证（`/mcp auth`）
- Server 崩溃自动重启（指数退避）

**记忆系统**
- 短期记忆：对话历史 + 自动摘要压缩
- 长期记忆：`/save` 保存关键事实，跨会话复用
- 长/短上下文双模式（自动适配模型窗口大小）

**代码理解**
- RAG 语义检索：`/search`、`/index`
- 代码关系图谱：`/graph`
- AST 分析 + SQLite 向量存储

**浏览器操控**
- Chrome DevTools Protocol，零额外依赖
- 打开页面、截图、点击、输入、执行 JS
- 多标签页管理，复用已有 Chrome 登录态

**安全机制**
- HITL 人工审批（`/hitl on`）
- 路径围栏、命令黑名单、资源上限
- 结构化审计日志（`/audit`）

**插件系统**
- Java ServiceLoader SPI + URLClassLoader 隔离
- 命名空间隔离：`plugin__{name}__{tool}`
- 状态持久化，`/plugin enable|disable`

**会话管理**
- 自动保存、手动保存（`/session save`）
- 加载、删除、导出（`/session load|delete|export`）
- 恢复上次会话（`/resume`）

## 命令列表

| 命令 | 说明 |
|------|------|
| `/plan [任务]` | Plan-and-Execute 模式 |
| `/team [任务]` | Multi-Agent 协作模式 |
| `/model <name>` | 切换模型（deepseek/glm/anthropic） |
| `/hitl on\|off` | 启用/关闭人工审批 |
| `/mcp` | 查看 MCP server 状态 |
| `/mcp restart\|logs\|disable\|enable <name>` | 管理 MCP server |
| `/mcp resources\|prompts <name>` | 查看 MCP resources/prompts |
| `/mcp auth <server>` | OAuth 认证 |
| `/mcp auth status\|revoke` | 查看/撤销认证 |
| `/plugin` | 查看插件 |
| `/plugin enable\|disable\|reload` | 管理插件 |
| `/session` | 查看会话列表 |
| `/session save\|load\|delete\|export` | 管理会话 |
| `/resume` | 恢复上次会话 |
| `/browser` | 浏览器状态 |
| `/index [路径]` | 代码库索引 |
| `/search <查询>` | 语义检索代码 |
| `/graph <类名>` | 代码关系图谱 |
| `/context` | 上下文状态 |
| `/memory` | 记忆状态 |
| `/memory clear` | 清空长期记忆 |
| `/save <事实>` | 保存关键事实 |
| `/policy` | 安全策略状态 |
| `/audit [N]` | 审计记录 |
| `/skill list\|on\|off` | 管理 Skill |
| `/tui` | 终端图形界面 |
| `/clear` | 清空对话历史 |
| `/exit` | 退出 |

## MCP 配置

`~/.YuCLI/mcp.json` 或项目内 `.YuCLI/mcp.json`：

```json
{
  "mcpServers": {
    "fetch": {
      "command": "uvx",
      "args": ["mcp-server-fetch"]
    },
    "remote-demo": {
      "url": "https://mcp.example.com/v1",
      "headers": {"Authorization": "Bearer ${REMOTE_TOKEN}"}
    }
  }
}
```

`command` = stdio server，`url` = Streamable HTTP server。

## 工具列表

| 工具 | 说明 |
|------|------|
| `read_file` | 读取文件 |
| `write_file` | 写入文件 |
| `list_dir` | 列出目录 |
| `execute_command` | 执行 Shell 命令 |
| `create_project` | 创建项目（java/python/node） |
| `search_code` | 语义检索代码 |
| `web_search` | 联网搜索 |
| `web_fetch` | 抓取 URL 正文 |
| `browser_navigate\|screenshot\|click\|type\|evaluate\|get_dom\|tab` | 浏览器操控 |
| `mcp__{server}__{tool}` | MCP 动态工具 |

## 项目结构

```
src/main/java/com/yucli/
├── agent/          # ReAct、Plan-and-Execute、Multi-Agent
├── browser/        # Chrome DevTools Protocol
├── cli/            # CLI 入口、命令解析
├── config/         # 配置管理
├── hitl/           # Human-in-the-Loop 审批
├── llm/            # LLM 客户端（DeepSeek/GLM/Anthropic）
├── mcp/            # MCP 协议
│   ├── auth/       # OAuth 2.0 + PKCE
│   ├── config/     # MCP 配置
│   ├── jsonrpc/    # JSON-RPC 2.0
│   ├── mention/    # @mention 引用
│   ├── protocol/   # 协议消息
│   ├── resources/  # Resources 管理
│   └── transport/  # stdio / Streamable HTTP
├── memory/         # 短期/长期记忆、压缩、检索
├── plan/           # 任务规划与执行
├── plugin/         # 插件系统
├── policy/         # 安全策略（路径围栏/命令黑名单/审计）
├── rag/            # RAG 检索、向量存储、AST 分析
├── runtime/        # 取消令牌
├── session/        # 会话持久化
├── skill/          # Skill 系统
├── tool/           # 工具注册表
├── tui/            # 终端图形界面
├── util/           # 工具类
└── web/            # Web 搜索与抓取
```

## 技术栈

Java 17 / Maven / OkHttp / Jackson / JLine3 / SQLite / JavaParser / Lanterna

## License

MIT
