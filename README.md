# YuCLI

MCP-Native Agent CLI，对标 Claude Code。Java 17 实现，支持多模型、多 Agent 协作、MCP 协议、浏览器操控、插件系统。

```
YuCLI  v19.0.0  session=local  ready
██╗   ██╗██╗   ██╗ ██████╗██╗     ██╗
╚██╗ ██╔╝██║   ██║██╔════╝██║     ██║
 ╚████╔╝ ██║   ██║██║     ██║     ██║
  ╚██╔╝  ██║   ██║██║     ██║     ██║
   ██║   ╚██████╔╝╚██████╗███████╗██║
   ╚═╝    ╚═════╝  ╚═════╝╚══════╝╚═╝
输入消息开始对话   ·   /help 查看命令   ·   /exit 退出
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
  "defaultProvider": "anthropic",
  "providers": {
    "anthropic": {
      "apiKey": "your-key",
      "baseUrl": "https://api.deepseek.com/anthropic",
      "model": "deepseek-v4-pro"
    },
    "deepseek": { "apiKey": "your-key" },
    "glm": { "apiKey": "your-key" }
  }
}

# 方式二：环境变量
export ANTHROPIC_API_KEY=your-key
```

### 运行

```bash
java -jar target/yucli-19.0.0.jar
# 或全局安装后直接：
yucli

# Headless / CI:
java -jar target/yucli-19.0.0.jar run "summarize this repo" --json
java -jar target/yucli-19.0.0.jar run "review recent changes" --mode team --jsonl
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
| `/loop` | 查看 ReAct 循环保险阀状态 |
| `/eval [cases\|run]` | 查看 EvalHarness 用例格式或手动运行说明，不默认调用真实 LLM |
| `/agents` | 查看用户级和项目级 SubAgent Profile 配置 |
| `/hooks` | 查看 `PreToolUse` / `PostToolUse` Hooks 状态 |
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

## Loop / Eval 说明

### `/loop`

`/loop` 是只读状态命令，用来查看 ReAct 循环的当前兜底规则。ReAct 是否继续由模型返回的 `tool_calls` 决定；`AgentBudget` 只负责三类保险阀：Token 预算、重复工具调用停滞检测、硬轮数上限。该命令不会调用 LLM，也不会执行工具。

### `/eval`

`/eval` 是 EvalHarness 的说明入口，只打印用例格式和手动运行命令，不会默认触发真实 LLM 评测。

- `/eval cases`：查看 `src/test/resources/eval/cases.json` 的字段约定
- `/eval run`：查看显式启用手动评测的 Maven 命令

Eval case 顶层是 JSON array，每个对象字段如下：

```json
{
  "id": "file-write-1",
  "instruction": "Create a file named hello.txt.",
  "setupScript": "",
  "verifyScript": "if (Test-Path 'hello.txt') { exit 0 } else { exit 1 }"
}
```

字段说明：

- `id`：稳定用例 ID
- `instruction`：发给 Agent 的任务
- `setupScript`：可选，运行前在临时目录执行
- `verifyScript`：可选，运行后在临时目录执行，退出码 `0` 表示通过

默认 `mvn test` 不会运行真实 LLM 评测。需要手动评测时必须显式启用：

```bash
mvn test -Dtest=EvalHarness -DYuCLI.eval.enabled=true
```

这会调用真实 LLM、执行本地 setup/verify 脚本，并消耗 API 配额。

## Headless Run

适合 CI / scripts / GitHub Actions 包装：

```bash
yucli run "summarize this repo" --json
yucli run "review recent changes" --mode team --jsonl
```

输出字段：`task / mode / success / result / error / durationMs`。支持 `react`、`plan`、`team` 三种 mode；`plan` 会自动执行计划，`team` 会加载 SubAgent Profiles。

## Hooks / SubAgent Profiles

### Hooks

YuCLI 支持可配置工具生命周期 hook。默认读取：

1. `~/.YuCLI/hooks.json`
2. `.YuCLI/hooks.json`

当前事件：

- `PreToolUse`：工具执行前触发；hook 命令非 0、超时、执行失败或结构化 `deny` 会阻断本次工具调用
- `PostToolUse`：工具执行后触发；失败只打印警告，不改变工具结果
- `PreToolUse` stdout 可返回 `{"decision":"allow|deny|modify","reason":"...","arguments":{...}}`；`modify` 会替换后续工具调用参数
- `/hooks` 可查看当前 hook 状态、事件计数、matcher、命令数量和 timeout

配置示例：

```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "write_file", "commands": ["python scripts/check_write.py"], "timeoutSeconds": 5 }
    ],
    "PostToolUse": [
      { "matcher": "*", "command": "python scripts/log_tool.py" }
    ]
  }
}
```

`matcher` 支持精确工具名、`*`、前缀通配如 `mcp__*`。hook 命令通过 stdin 接收 JSON payload。

### SubAgent Profiles

YuCLI 已支持加载自定义 SubAgent Profile 配置，并已接入 `/team` / `/team <任务>` 的 Multi-Agent 编排器。

默认读取：

1. `~/.YuCLI/agents/*.json`
2. `.YuCLI/agents/*.json`

同名 profile 由项目级覆盖用户级。可用 `/agents` 查看当前加载结果。存在 `WORKER` profile 时，worker 池由这些 profile 决定；否则回退默认 `worker-1` / `worker-2`。

```json
{
  "name": "reviewer",
  "role": "REVIEWER",
  "instructions": "审查执行结果，指出风险和缺口。",
  "tools": ["read_file", "search_code"],
  "model": "glm-5.1"
}
```

## License

MIT
