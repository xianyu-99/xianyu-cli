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
    "deepseek": {
      "apiKey": "your-key",
      "baseUrl": "https://api.deepseek.com",
      "model": "deepseek-v4-flash"
    },
    "glm": { "apiKey": "your-key" },
    "qwen": {
      "apiKey": "your-key",
      "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "model": "qwen3-coder-plus"
    },
    "openai": {
      "apiKey": "your-key",
      "baseUrl": "https://api.openai.com/v1",
      "model": "gpt-4o"
    }
  }
}

# 方式二：环境变量
export ANTHROPIC_API_KEY=your-key
# QWEN_API_KEY=your-key
# OPENAI_API_KEY=your-key
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
- DeepSeek V4、GLM-5.1、Qwen、Anthropic Claude、通用 OpenAI-compatible
- 运行时切换：`/model deepseek`、`/model glm`、`/model qwen`、`/model openai`
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
- 权限 Profile（`~/.YuCLI/permissions.json` + `.YuCLI/permissions.json`）
- Checkpoint / Undo（`/checkpoint`、`/undo`）
- 结构化审计日志（`/audit`）

**插件系统**
- Java ServiceLoader SPI + URLClassLoader 隔离
- 命名空间隔离：`plugin__{name}__{tool}`
- 状态持久化，`/plugin enable|disable`
- 插件模板生成器：`/plugin template <name>`

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
| `/hooks` | 查看工具、Prompt 与生命周期 Hooks 状态 |
| `/plan [任务]` | Plan-and-Execute 模式 |
| `/team [任务]` | Multi-Agent 协作模式 |
| `/model <name>` | 切换模型（anthropic/deepseek/glm/qwen/openai） |
| `/hitl on\|off` | 启用/关闭人工审批 |
| `/mcp` | 查看 MCP server 状态 |
| `/mcp restart\|logs\|disable\|enable <name>` | 管理 MCP server |
| `/mcp resources\|prompts <name>` | 查看 MCP resources/prompts |
| `/mcp auth <server>` | OAuth 认证 |
| `/mcp auth status\|revoke` | 查看/撤销认证 |
| `/plugin` | 查看插件 |
| `/plugin enable\|disable\|reload` | 管理插件 |
| `/plugin template <name>` | 生成 Java 插件模板 |
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
| `/permissions` | 查看权限 Profile（allow / deny / ask） |
| `/checkpoint` | 查看最近工具写入快照 |
| `/undo` | 恢复最近一次工具写入前状态 |
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
├── checkpoint/     # 工具写入前快照与 undo
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
  "id": "react-file-read-write",
  "mode": "react",
  "instruction": "Read input.txt, then create summary.txt.",
  "setupScriptWindows": "Set-Content -Path 'input.txt' -Value 'hello'",
  "setupScriptUnix": "printf 'hello\\n' > input.txt",
  "verifyScriptWindows": "if (Test-Path 'summary.txt') { exit 0 } else { exit 1 }",
  "verifyScriptUnix": "test -f summary.txt"
}
```

字段说明：

- `id`：稳定用例 ID
- `mode`：可选，`react` / `plan` / `team`，默认 `react`
- `instruction`：发给 Agent 的任务
- `setupScript`：可选，运行前在临时目录执行
- `setupScriptWindows` / `setupScriptUnix`：可选，按平台覆盖 `setupScript`
- `verifyScript`：运行后在临时目录执行，退出码 `0` 表示通过
- `verifyScriptWindows` / `verifyScriptUnix`：可选，按平台覆盖 `verifyScript`

当前内置 benchmark 用例覆盖 ReAct 文件读写、命令策略拒绝审计、Plan-and-Execute 文件转换、PreToolUse hook 拦截。Harness 每个用例都在独立临时目录运行，并把 `YuCLI.audit.dir` 指向该目录下的 `audit/`，只加载临时目录里的 `.YuCLI/hooks.json`，避免依赖用户全局状态。

默认 `mvn test` 不会运行真实 LLM 评测。需要手动评测时必须显式启用：

```bash
mvn test -Dtest=EvalHarness -DYuCLI.eval.enabled=true
```

这会调用真实 LLM、执行本地 setup/verify 脚本，并消耗 API 配额。

## Permissions / Checkpoints

权限 Profile 用于在 HITL 之前增加显式 `allow` / `deny` / `ask` 规则。默认读取：

1. `~/.YuCLI/permissions.json`
2. `.YuCLI/permissions.json`

项目级规则会和用户级规则合并，项目级 `mode` 覆盖用户级。`deny` 优先级最高，`allow` 会跳过 HITL 审批，`ask` 或未命中规则会继续交给 HITL / 默认策略处理。

```json
{
  "mode": "default",
  "allow": ["read_file", "list_dir"],
  "deny": ["execute_command:rm*", "mcp__danger__*"],
  "ask": ["write_file", "execute_command", "mcp__*"]
}
```

`write_file` 和新建项目会在写入前创建 checkpoint。`/checkpoint` 查看最近快照，`/undo` 恢复最近一次工具写入前状态；它只恢复 YuCLI 工具记录的文件快照，不修改 git 历史。

## Headless Run

适合 CI / scripts / GitHub Actions 包装：

```bash
yucli run "summarize this repo" --json
yucli run "review recent changes" --mode team --jsonl
```

输出字段：`task / mode / success / result / error / durationMs`。支持 `react`、`plan`、`team` 三种 mode；`plan` 会自动执行计划，`team` 会加载 SubAgent Profiles。

## Hooks / SubAgent Profiles

### Hooks

YuCLI 支持可配置工具、Prompt 与生命周期 hook。默认读取：

1. `~/.YuCLI/hooks.json`
2. `.YuCLI/hooks.json`

当前事件：

- `PreToolUse`：工具执行前触发；hook 非 0、HTTP 非 2xx、超时、执行失败或结构化 `deny` 会阻断本次工具调用
- `PostToolUse`：工具执行后触发；失败只打印警告，不改变工具结果
- `UserPromptSubmit`：用户输入提交给 Agent 前触发；支持 `deny` / `modify`，`modify.arguments.prompt` 会替换后续 Agent 输入
- `AgentStart` / `AgentFinish`：ReAct、Plan、Team 顶层 run 生命周期；warning-only
- `SubAgentStart` / `SubAgentFinish`：Planner / Worker / Reviewer 子代理生命周期；warning-only
- `PreCompact`：短期记忆压缩前触发；warning-only，失败不阻断压缩

hook 执行器：

- `command` / `commands`：本地命令，通过 stdin 接收 JSON payload
- `url` / `urls`：HTTP POST JSON payload，2xx 视为成功
- `prompt` / `prompts`：使用当前 LLM 做结构化 hook 决策，不传工具列表，避免 hook 内部递归 tool-call

HTTP hook 支持 `headers`、`authToken`、`signatureSecret`、`retryCount`、`retryBackoffMillis`：`authToken` 会补 `Authorization: Bearer ...`，`signatureSecret` 会生成 `X-YuCLI-Signature: sha256=...`，429/5xx/超时/网络错误按 `retryCount` 重试。HTTP hook 的 `url` / `urls`、`headers` 值、`authToken`、`signatureSecret` 支持 `${ENV_NAME}` 环境变量占位符；缺失变量会导致当前 hook 配置文件被忽略，stderr 只打印缺失变量名，不打印原始配置值。hook 错误输出会对 token/key/password/secret/authorization 做脱敏；`/hooks` 会展示 URL，secret 优先放在 header / `authToken` / `signatureSecret` 中。

阻断型事件（`PreToolUse`、`UserPromptSubmit`）可返回 `{"decision":"allow|deny|modify","reason":"...","arguments":{...}}`；非阻断事件会忽略 `deny/modify`，只向 stderr 打印 warning。非阻断事件可设置 `"async": true` 后台执行，避免通知类 hook 阻塞主流程。`/hooks` 可查看当前 hook 状态、事件计数、matcher、command/http/prompt 数量、async 和 timeout。

配置示例：

```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "write_file", "commands": ["python scripts/check_write.py"], "timeoutSeconds": 5 }
    ],
    "UserPromptSubmit": [
      {
        "matcher": "plan",
        "url": "https://example.com/yucli/prompt-hook",
        "headers": {"X-YuCLI-Project": "demo"},
        "authToken": "${YUCLI_HOOK_TOKEN}",
        "signatureSecret": "${YUCLI_HOOK_SIGNATURE_SECRET}",
        "retryCount": 2,
        "retryBackoffMillis": 250
      }
    ],
    "PostToolUse": [
      { "matcher": "*", "command": "python scripts/log_tool.py", "async": true }
    ]
  }
}
```

`matcher` 支持精确值、`*`、前缀通配。工具事件匹配工具名，如 `write_file` / `mcp__*`；`UserPromptSubmit` 和顶层 Agent 事件匹配 mode，如 `react` / `plan` / `team`；SubAgent 事件匹配角色，如 `planner` / `worker` / `reviewer`；`PreCompact` 匹配 `short_term`。

官方 hook recipes 位于 `examples/hooks/`，默认不自动加载；复制 `examples/hooks/hooks.json` 到 `.YuCLI/hooks.json` 后生效。示例脚本只使用 Python 标准库，启用前应按项目需要审阅和调整规则。

### SubAgent Profiles

YuCLI 已支持加载自定义 SubAgent Profile 配置，并已接入 `/team` / `/team <任务>` 的 Multi-Agent 编排器。

默认读取：

1. `~/.YuCLI/agents/*.json`
2. `.YuCLI/agents/*.json`

同名 profile 由项目级覆盖用户级。可用 `/agents` 查看当前加载结果。存在 `WORKER` profile 时，worker 池由这些 profile 决定；否则回退默认 `worker-1` / `worker-2`。

`tools` 是运行时硬白名单：SubAgent 只会看到匹配的工具定义，越权 tool-call 会在进入底层 `ToolRegistry` 前被拒绝。为空时不限制。Profile 也可配置 `deniedTools`、`allowedCommands`、`allowedPaths`、`deniedCommands`、`workingDirectory`，用于给单个 SubAgent 增加独立工具/命令/路径 scope；这些限制同样由 `ScopedToolRegistry` 在运行时硬拦截。`deniedTools` 会从工具定义和执行两侧生效；`allowedCommands` 为空时不限制，非空时 `execute_command` 只能执行匹配的命令，且 `deniedCommands` 优先。

官方 profile 示例位于 `examples/agents/`，默认不自动加载；复制到 `.YuCLI/agents/` 或 `~/.YuCLI/agents/` 后生效。

```json
{
  "name": "reviewer",
  "role": "REVIEWER",
  "instructions": "审查执行结果，指出风险和缺口。",
  "tools": ["read_file", "search_code"],
  "deniedTools": ["write_file", "mcp__danger__*"],
  "allowedCommands": ["git status", "mvn test*"],
  "allowedPaths": ["src", "README.md"],
  "deniedCommands": ["git push", "curl*"],
  "workingDirectory": "src",
  "model": "glm-5.1"
}
```

## 生态模板

YuCLI 在 `examples/` 下提供可复制的生态样板：

- `examples/mcp/mcp.json`：stdio、Streamable HTTP、header auth、OAuth MCP server 模板。示例默认都是 `disabled: true`，需要审阅后再启用。
- `examples/skills/`：可复制的 `SKILL.md` 示例，覆盖代码审查和 MCP research 工作流。
- `examples/plugins/`：插件模板生成器用法。
- `examples/agents/` 与 `examples/hooks/`：SubAgent profile 与 hook recipes。

在 YuCLI 内生成 Java 插件脚手架：

```text
/plugin template demo-tools
```

该命令会生成 `demo-tools-yucli-plugin/`，包含 Maven 工程、`YuPlugin` 实现、ServiceLoader 元数据和一个 `echo` 示例工具。目标目录非空时会拒绝覆盖。

## 沙箱边界

YuCLI 当前已经有工具级 scope、路径围栏、命令拦截、HITL、hooks、审计日志，以及 SubAgent `allowedPaths` / `allowedCommands` 策略。

`execute_command` 现在支持可选 Docker 进程沙箱，默认关闭。开启后，命令会通过 `docker run --rm` 在容器里执行，项目目录挂载到 `/workspace`，容器默认 `--network none`，超时或取消时会尝试 `docker rm -f` 清理容器：

```bash
YUCLI_SANDBOX_ENABLED=true
YUCLI_SANDBOX_DOCKER_IMAGE=maven:3.9-eclipse-temurin-17
YUCLI_SANDBOX_NETWORK=none
YUCLI_SANDBOX_MOUNT=rw
```

对应系统属性：

```bash
java -DYuCLI.sandbox.enabled=true \
     -DYuCLI.sandbox.docker.image=maven:3.9-eclipse-temurin-17 \
     -jar target/yucli-19.0.0.jar
```

边界：Docker sandbox 是实用级进程/文件系统隔离，不等同于 microVM。`rw` 挂载时命令仍可修改当前项目目录；`ro` 更安全，但会让 `mvn test`、构建输出、代码生成类任务失败。更强的 gVisor / Firecracker / per-SubAgent 独立文件系统仍属于后续 runtime isolation 路线。

## License

MIT
