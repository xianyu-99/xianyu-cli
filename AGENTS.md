# AGENTS.md

这份文档是 `YuCLI` 仓库给各类 Agent / 新线程使用的首读入口。

目标只有两个：

1. 让首次进入仓库的线程，能在几分钟内建立对项目的正确认识。
2. 把后续协作规则沉淀到一个稳定入口，避免规则只存在于历史对话里。

如果本仓库的行为、目录结构、约定或协作方式发生了稳定变化，请在同一次改动里同步更新本文件。

## 信息优先级

当不同文档描述不一致时，按下面的优先级理解：

1. 代码实际行为
2. `AGENTS.md`
3. `README.md`
4. `ROADMAP.md`
5. `CLAUDE.md`

`ROADMAP.md` 代表演进方向，不代表已经交付。

## 项目快照

- 项目名：`YuCLI`
- 定位：一个面向商业使用的 Java Agent CLI 产品，对标 Claude Code，从最初的 ReAct 循环持续演进到完整 Agent 产品形态
- 当前主线：主线规划 19 期。已完成第 1 期 `ReAct`、第 2 期 `Plan-and-Execute + DAG`、第 3 期 `Memory + 上下文工程`、第 4 期 `RAG 检索 + 代码库理解`、第 5 期 `Multi-Agent 协作 + 角色分工`、第 6 期 `HITL 人工审批 + 危险操作拦截`（含 HITL 增强：路径围栏 / 命令快速拒绝 / 操作审计）、第 7 期 `异步执行 + 并行工具调用`、第 8 期 `多模型适配 + 运行时切换`、第 9 期 `联网能力 + Web 工具`、第 10 期 `MCP 协议核心（stdio + Streamable HTTP）`、第 11 期 `MCP 高级能力首批（resources 双轨 + prompts 查看 + 被动通知）`、第 12 期 `长上下文工程`、第 13 期 `Chrome DevTools MCP`、第 14 期 `CDP 会话复用 + 登录态访问`、第 15 期 `Skill 系统 + web-access Skill`、第 16 期 `TUI 产品化`、第 17 期 `OAuth 2.0 认证`、第 18 期 `插件系统`、第 19 期 `会话持久化`
- 下一步：sampling / recovery / 其他 MCP 增强
- 当前用户可感知版本：CLI Banner 显示 `v19.0.0`
- 当前 Maven 产物版本：`pom.xml` 为 `19.0.0`
- 结论：当前 Jar 名应为 `yucli-19.0.0.jar`，CLI Banner 显示 `v19.0.0`

## 运行前提

- Java 17+
- Maven
- 可用的默认模型 API Key：`ANTHROPIC_API_KEY`（默认 provider 为 `anthropic`，默认 DeepSeek Anthropic 兼容端点）；也兼容 Claude Code 常用的 `ANTHROPIC_AUTH_TOKEN`。如需默认使用通用 OpenAI provider，可设置 `YUCLI_DEFAULT_PROVIDER=openai`
- 可选模型 Key：`GLM_API_KEY`、`DEEPSEEK_API_KEY`、`QWEN_API_KEY`、`OPENAI_API_KEY`

模型配置当前读取顺序以代码为准：

1. `~/.YuCLI/config.json` 中对应 provider 的 `apiKey` / `model` / `baseUrl` / `reasoningEffort` / `wireApi`
2. 环境变量：`ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` / `GLM_API_KEY` / `DEEPSEEK_API_KEY` / `QWEN_API_KEY` / `OPENAI_API_KEY` 等
3. 仓库当前目录下的 `.env`
4. 用户主目录下的 `.env`

`.env.example` 当前包含：

```bash
ANTHROPIC_API_KEY=your_api_key_here
# ANTHROPIC_AUTH_TOKEN=your_api_key_here
# ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic
# ANTHROPIC_MODEL=deepseek-v4-pro
# ANTHROPIC_REASONING_EFFORT=xhigh
# GLM_API_KEY=your_api_key_here
# DEEPSEEK_API_KEY=your_deepseek_api_key_here
# QWEN_API_KEY=your_qwen_api_key_here
# QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
# QWEN_MODEL=qwen3-coder-plus
# QWEN_REASONING_EFFORT=high
# OPENAI_API_KEY=your_openai_compatible_key_here
# OPENAI_BASE_URL=https://api.openai.com/v1
# OPENAI_MODEL=gpt-4o
# OPENAI_WIRE_API=responses
# OPENAI_REASONING_EFFORT=high
EMBEDDING_PROVIDER=ollama
EMBEDDING_MODEL=nomic-embed-text:latest
EMBEDDING_BASE_URL=http://localhost:11434
# EMBEDDING_API_KEY=your_api_key_here
# YuCLI_LOG_LEVEL=INFO
# YuCLI_LOG_DIR=/Users/yourname/.YuCLI/logs
# YuCLI_LOG_MAX_HISTORY=7
# YuCLI_LOG_MAX_FILE_SIZE=10MB
# YuCLI_LOG_TOTAL_SIZE_CAP=100MB
```

长期记忆默认持久化位置：

1. `~/.YuCLI/memory/long_term_memory.json`
2. 如果传入 `-DYuCLI.memory.dir=/path/to/dir`，则优先使用该目录

代码索引（RAG）默认持久化位置：

1. `~/.YuCLI/rag/codebase.db`
2. 如果传入 `-DYuCLI.rag.dir=/path/to/dir`，则优先使用该目录

操作审计日志默认持久化位置（以代码实际行为为准）：

1. 系统属性：`-DYuCLI.audit.dir=/path/to/dir`
2. 环境变量：`YuCLI_AUDIT_DIR`
3. 默认值：`~/.YuCLI/audit/audit-YYYY-MM-DD.jsonl`（按天分文件，JSONL 格式）

Embedding 配置读取顺序（以代码实际行为为准）：

1. 环境变量：`EMBEDDING_PROVIDER`、`EMBEDDING_MODEL`、`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`
2. 系统属性（同上）
3. `.env`：优先 `-DYuCLI.env.dir=/path/to/dir/.env` 所在目录，其次仓库当前目录 `.env`，最后用户主目录 `.env`
4. 默认值：`ollama` / `nomic-embed-text:latest` / `http://localhost:11434`

日志配置读取顺序（以代码实际行为为准）：

1. 系统属性：`YuCLI.log.dir`、`YuCLI.log.level`、`YuCLI.log.maxHistory`、`YuCLI.log.maxFileSize`、`YuCLI.log.totalSizeCap`
2. 环境变量或 `.env`：`YuCLI_LOG_DIR`、`YuCLI_LOG_LEVEL`、`YuCLI_LOG_MAX_HISTORY`、`YuCLI_LOG_MAX_FILE_SIZE`、`YuCLI_LOG_TOTAL_SIZE_CAP`
3. 默认值：`~/.YuCLI/logs` / `INFO` / `7` / `10MB` / `100MB`

ReAct / SubAgent 预算配置读取顺序（以代码实际行为为准）：

1. 系统属性：`YuCLI.react.token.budget`、`YuCLI.react.stagnation.window`、`YuCLI.react.hard.max.iterations`
2. 默认值：`300000` / `3` / `50`

LLM HTTP 超时配置读取顺序（以代码实际行为为准）：

1. 系统属性：`YuCLI.llm.connect.timeout.seconds`、`YuCLI.llm.read.timeout.seconds`、`YuCLI.llm.write.timeout.seconds`、`YuCLI.llm.call.timeout.seconds`
2. 默认值：`60` / `300` / `60` / `600`（单位：秒）

注意：SSE 流式接口下，OkHttp 的 `readTimeout` 是"两次 read 之间最大间隔"而非请求总时长；GLM-5.1 在生成大段 reasoning_content 时服务端可能长时间静默，所以默认值放宽到 300 秒，再用 `callTimeout` 兜底整个请求。

Reasoning effort 配置读取顺序（以代码实际行为为准）：

1. `~/.YuCLI/config.json` 中对应 provider 的 `reasoningEffort`
2. provider 专属环境变量 / `.env`：`ANTHROPIC_REASONING_EFFORT`、`ANTHROPIC_MODEL_REASONING_EFFORT`、`QWEN_REASONING_EFFORT`、`OPENAI_REASONING_EFFORT` 等
3. 通用环境变量 / `.env`：`MODEL_REASONING_EFFORT`、`YUCLI_REASONING_EFFORT`
4. 未配置时不发送 `reasoning_effort` 字段，由服务端默认决定

当前 Anthropic Messages wire 和 OpenAI-compatible Chat Completions wire 都会在配置存在时发送顶层 `reasoning_effort`；OpenAI Responses wire 会发送 `reasoning.effort`。常见值包括 `low` / `medium` / `high` / `xhigh` / `max`，具体是否生效以所接入网关为准；CLI 启动和 `/model` 会显示当前读取到的值。

OpenAI wire 配置读取顺序：

1. `~/.YuCLI/config.json` 中对应 provider 的 `wireApi`
2. provider 专属环境变量 / `.env`：`OPENAI_WIRE_API`、`OPENAI_WIRE`
3. 通用环境变量 / `.env`：`MODEL_WIRE_API`、`YUCLI_WIRE_API`
4. 未配置时默认使用 Chat Completions wire；配置为 `responses` 时使用 `/v1/responses`

Web 搜索 provider 配置读取顺序（以代码实际行为为准）：

1. 环境变量 / 系统属性 / `.env` 中的 `SEARCH_PROVIDER`：显式指定 `zhipu` / `serpapi` / `searxng`
2. 未指定时按 Key/URL 自动判断（优先级从高到低）：
   - `GLM_API_KEY` 存在 → `zhipu`（智谱 Web Search，与 GLM 推理共用 Key，国内首选）
   - `SERPAPI_KEY` 存在 → `serpapi`
   - `SEARXNG_URL` 存在 → `searxng`
3. 都没有时返回 `zhipu` 占位 provider，`web_search` 工具会提示用户配置

各 provider 配置读取顺序（环境变量 / 系统属性 / `.env`）：
- `zhipu`：`GLM_API_KEY`（必填，与 LLM 推理共用）+ `ZHIPU_SEARCH_ENGINE`（可选，默认 `search_std`，可选 `search_pro` / `search_pro_sogou` / `search_pro_quark`）
- `serpapi`：`SERPAPI_KEY`
- `searxng`：`SEARXNG_URL`（推荐本地 `docker run --rm -p 8888:8888 searxng/searxng`）

Web 抓取（`web_fetch`）安全策略（实现位于 `src/main/java/com/yucli/web/NetworkPolicy.java`）：

- scheme 白名单：仅允许 `http` / `https`
- 主机黑名单：屏蔽 `localhost`、`0.0.0.0`、loopback / link-local / site-local 地址（基础 SSRF 围栏，不防 DNS rebinding）
- 响应体上限：5MB（流式截断，避免 OOM）
- 整体超时：30 秒（OkHttp `callTimeout`）
- 限流：默认每 60 秒最多 30 次请求

MCP 配置读取顺序（以代码实际行为为准）：

1. 用户级：`~/.YuCLI/mcp.json`
2. 项目级：`.YuCLI/mcp.json`
3. 按 server 名 merge，项目级覆盖用户级

配置格式兼容 Claude Code 的 `claude_desktop_config.json`：`command` + `args` 表示 stdio server，`url` + `headers` 表示 Streamable HTTP server。`${PROJECT_DIR}` 和 `${HOME}` 是内置变量；其他 `${VAR}` 从环境变量读取，缺失会直接报错。没有 MCP 配置文件时，MCP 子系统仍默认开启，但不会启动外部 server，避免首次运行被 `npx` / `uvx` 冷启动阻塞。

官方 MCP 配置示例位于 `examples/mcp/mcp.json`，默认不自动加载，且示例 server 都带 `disabled: true`，复制到 `.YuCLI/mcp.json` 或 `~/.YuCLI/mcp.json` 后需要按项目实际情况审阅、补凭据并启用。

## 常用命令

```bash
cp .env.example .env
mvn clean package
java -jar target/yucli-19.0.0.jar
mvn clean compile exec:java -Dexec.mainClass="com.yucli.cli.Main"
mvn test
```

验证 RAG 相关测试：

```bash
mvn test -Dtest=CodeChunkerTest,CodeAnalyzerTest,VectorStoreTest,CodeIndexTest
```

如果只是验证一个测试类：

```bash
mvn test -Dtest=ExecutionPlanTest
```

手动运行 EvalHarness（会调用真实 LLM、执行本地 setup/verify 脚本，默认测试不会运行）：

```bash
mvn test -Dtest=EvalHarness -DYuCLI.eval.enabled=true
```

EvalHarness 当前读取 `src/test/resources/eval/cases.json`，支持 `mode=react|plan|team`（默认 `react`）和平台脚本字段 `setupScriptWindows` / `setupScriptUnix` / `verifyScriptWindows` / `verifyScriptUnix`。每个用例在独立临时目录运行，`YuCLI.audit.dir` 会临时指向该目录下的 `audit/`，hook 只加载该目录内的 `.YuCLI/hooks.json`。

## 当前产品行为

### 1. ReAct 模式

- 默认模式
- 主入口在 `src/main/java/com/yucli/agent/Agent.java`
- 维护对话历史
- 退出条件由 LLM 自决：只要它不再返回 `tool_calls`、直接给出 `content`，循环就结束
- `AgentBudget`（`src/main/java/com/yucli/agent/AgentBudget.java`）只承担保险阀职责，三种兜底任一命中即收尾：
  - 累计 `inputTokens + outputTokens` 超过 token 预算（默认 300_000）
  - 连续 N 轮（默认 3）出现完全相同的工具名 + 参数，判定为死循环
  - 累计轮数超过硬上限（默认 50），最终防御
- 不再使用"固定最多 10 轮"的策略；新代码改动前阅读 `AgentBudget` 的注释比读老 README 更可靠
- 支持工具调用后继续思考
- 用户默认看到的是流式输出的模型 `reasoning_content`（如果接口返回）和回复内容；ReAct 同一次用户输入只打印一次 `🧠 思考过程` 标题，工具调用前后的后续推理继续归在同一块下；ReAct 流式头标签使用 `🤖 回复`（而非 `最终结果`，避免在模型调用工具前先 narrate 时误导用户）；Plan 阶段同样走流式展示；终端会先渲染常见 Markdown 再输出；工具参数、工具返回片段、Token 使用量不再作为默认用户输出
- 会写入短期记忆

### 2. Plan-and-Execute 模式

- 通过 `/plan` 或 `/plan <任务>` 进入
- 主入口在 `src/main/java/com/yucli/agent/PlanExecuteAgent.java`
- 流程是：规划 -> 用户审阅 -> 执行 DAG -> 汇总结果
- 计划执行完后会回到默认 `ReAct`
- 简单任务应优先生成最小计划；不要为了凑步数引入无关读写文件或中间落盘步骤

### 3. Plan 审阅交互

以 `Main.java` 当前实现为准：

- `Enter`：执行当前计划
- `Ctrl+O`：展开完整计划
- `ESC`：如果当前在展开视图则先折叠，否则取消本次计划
- `I`：输入补充要求并重新规划

注意：

- 这里不是 README 里旧描述的“只有 Enter / ESC / I”
- 原始按键处理依赖 JLine raw mode
- 方向键属于终端控制序列，不应被误判成 `ESC` 取消
- 涉及这块的改动，不能只看字符串，要连输入模式和回退路径一起看

### 4. Memory 系统

- 主模块在 `src/main/java/com/yucli/memory/`
- 默认包含：短期记忆、长期记忆、摘要压缩、事实提取、Token 预算、相关记忆检索
- 注入到 system prompt 的“相关记忆”应只来自长期记忆；当前轮用户输入和短期对话已经在消息历史里，不应再被当成“历史记忆”重复注入
- 长期记忆默认只通过显式命令 `/save <事实>` 写入；不要在每轮对话结束或 `/clear` 时自动提取事实
- 长期记忆只应保存跨会话仍成立的稳定事实；一次性任务请求、临时文件名/目录名、模型猜测或“用户想要你做什么”这类指令，不应落入长期记忆
- CLI 命令：
  - `/memory` 或 `/mem`：查看当前记忆状态
  - `/memory clear`：清空长期记忆
  - `/save <事实>`：手动保存关键事实
- `ReAct` 和 `Plan-and-Execute` 两条主路径都应写回记忆；改动其中一条时，另一条也要检查

### 5. RAG 系统

- 主模块在 `src/main/java/com/yucli/rag/`
- 默认包含：EmbeddingClient、VectorStore（SQLite）、CodeChunker、CodeAnalyzer、CodeIndex、CodeRetriever
- CLI 命令：
  - `/index [路径]`：索引代码库
  - `/search <查询>`：语义检索代码
  - `/graph <类名>`：查看代码关系图谱
- Agent 工具：`search_code`（语义检索代码库）、`web_search`（联网搜索）、`web_fetch`（抓取已知 URL）
- 在 ReAct 和 Plan 模式下，Agent 会自动检索代码上下文辅助回答
- `web_search` 通过 `SearchProvider` 抽象接入，当前内置三个实现：
  - `zhipu`（默认，与 GLM 推理共用 `GLM_API_KEY`，0.01–0.05 元/次，中文搜索质量高，国内首选）
  - `serpapi`（国际通用，需 `SERPAPI_KEY`，付费即开即用）
  - `searxng`（开源自托管，需 `SEARXNG_URL`，免费但需本地 docker 实例）
  - Provider 不可用时工具返回引导提示，不会让整轮 Agent 失败
- `web_fetch` 走「OkHttp + Jsoup + 简易 readability」本地链路，对静态/SSR 页面有效；遇到 SPA / 防爬墙会返回空正文 + `已知边界` 提示，不会反复重试。JS 渲染 / 登录态访问留给第 13/14 期 CDP 路线

### 6. Multi-Agent 协作模式

- 通过 `/team` 或 `/team <任务>` 进入
- 主入口在 `src/main/java/com/yucli/agent/AgentOrchestrator.java`
- 采用主从架构：编排器（Orchestrator）为"主"，子代理（SubAgent）为"从"
- 三个角色：
  - 规划者（Planner）：拆解任务为执行步骤
  - 执行者（Worker）：调用工具执行具体操作（默认 2 个 Worker 轮询分配）
  - 检查者（Reviewer）：审查执行结果质量
- 协作流程：规划 -> 按依赖顺序分配给 Worker -> Reviewer 审查 -> 通过则完成，未通过则带反馈重试
- 同一个依赖批次内部 **真正并行执行**（Worker 最多并发数为池大小，默认 2）。每个并发步骤使用独立的 PrintStream 缓冲输出流，在批次结束后按 step_id 顺序统一 flush 到 stdout，既保证了多线程写操作不互相交错乱序，又实现了多 Agent 并发高效干活。
- 冲突解决：每步最多重试 2 次，超过次数保留当前结果
- Reviewer 审查结果解析不出来（空内容、缺 approved 字段、既无肯定也无否定关键词）时，采取保守策略判为未通过
- 如果某步失败导致其依赖步骤无法执行，Orchestrator 会显式提示 `⏭️ 步骤 [step_x] 因前置步骤失败被跳过`
- SubAgent 在执行阶段遭遇 `IOException`（LLM 调用失败、超时等）时返回 `AgentMessage.Type.ERROR`，调用方需要独立于 RESULT 处理
- 所有子代理共享同一个 ToolRegistry 与 MemoryManager（与 ReAct 模式共享项目路径与记忆上下文，避免重复加载长期记忆）
- 任务执行完后回到默认 `ReAct`
- `ReAct`、`Plan-and-Execute` 和 `Multi-Agent` 三条路径都应写回记忆

#### 6.1 可配置 SubAgent Profile

- 主模块在 `src/main/java/com/yucli/agent/config/`，已接入 `AgentOrchestrator` 执行路径
- Profile 搜索目录：
  - 用户级：`~/.YuCLI/agents/*.json`
  - 项目级：`.YuCLI/agents/*.json`
- 合并规则：先加载用户级，再加载项目级；同名 profile 由项目级覆盖用户级
- `/team` / `/team <任务>` 创建 Multi-Agent 团队时会自动加载这些 profile：
  - `PLANNER`：优先使用名为 `planner` 的 profile，否则使用第一个 planner profile，否则回退默认 planner
  - `WORKER`：只要存在 worker profile，worker 池大小就等于 worker profile 数量；没有 worker profile 时回退默认 `worker-1` / `worker-2`
  - `REVIEWER`：优先使用名为 `reviewer` 的 profile，否则使用第一个 reviewer profile，否则回退默认 reviewer
- `SubAgent` 会在默认角色 prompt 后追加 profile 的 `instructions` 与工具白名单提示；`tools` 同时是运行时硬白名单：SubAgent 只会看到匹配的工具定义，越权 tool-call 会在进入底层 `ToolRegistry` 前由 `ScopedToolRegistry` 拒绝。为空时不限制。`deniedTools` / `allowedCommands` / `allowedPaths` / `deniedCommands` / `workingDirectory` 可给单个 SubAgent 增加独立工具、命令和路径 scope，同样由 `ScopedToolRegistry` 运行时硬拦截。`deniedTools` 会从工具定义和执行两侧生效；`allowedCommands` 为空时不限制，非空时 `execute_command` 只能执行匹配的命令，且 `deniedCommands` 优先。
- 官方示例 profile 位于 `examples/agents/*.json`，默认不自动加载；复制到 `.YuCLI/agents/` 或 `~/.YuCLI/agents/` 后生效
- 当前 JSON 格式：

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

- `name`、`role`、`instructions` 必填；`role` 支持 `PLANNER` / `WORKER` / `REVIEWER`（大小写不敏感）；`tools` / `deniedTools` / `allowedCommands` / `allowedPaths` / `deniedCommands` 默认空列表；`workingDirectory` / `model` 可选，其中 `model` 仅解析保存，暂不切换运行模型

### 7. HITL 审批系统

- 主模块在 `src/main/java/com/yucli/hitl/`
- 通过 `/hitl on` 启用、`/hitl off` 关闭，默认关闭
- 通过 `/hitl` 查看当前状态
- 危险工具：`write_file`（中危）、`execute_command`（高危）、`create_project`（中危）
- 非危险工具（`read_file`、`list_dir`、`search_code`）不受影响，直接执行
- 审批决策选项：
  - `y` / Enter：批准本次操作
  - `a`：本次会话全部放行同类操作（`APPROVED_ALL`，省去重复确认）
  - `n`：拒绝，可附拒绝原因
  - `s`：跳过本步骤
  - `m`：修改参数后执行
- 关键设计：`HitlToolRegistry` 继承 `ToolRegistry`，通过覆写 `executeTool()` 实现透明拦截；HITL 关闭时与普通 `ToolRegistry` 行为完全一致
- `/clear` 命令同时清除本次会话中积累的"全部放行"记录
- fail-safe：无法识别的输入会重新提示，**不会**默认批准；连续 5 次无效输入则保守判为 REJECTED
- 修改参数（`m`）输入的 JSON 会先用 Jackson 校验语法，非法则提示并回到主菜单重选
- 并发安全：`TerminalHitlHandler.requestApproval` 整体 `synchronized`，多 Agent 并行场景下审批提示会串行展示、避免 stdout / stdin 互相打架；`approvedAllTools` 使用 `ConcurrentHashMap.newKeySet()`
- 审批框展示采用"显示列宽"算法（CJK / 全角 / emoji 按 2 列计算），保证中文和表情符号下边框仍然对齐
- 参数展示按 JSON 结构解析逐字段展示；长字符串（> 120 字符）显示前 120 字符预览 + 总长度，换行替换为 `⏎` 以便肉眼可读
- 审批框上方会打印 `────────── ⚠️ HITL 审批请求 ──────────` 作为视觉分隔符，与上游 `🤖 回复` / `执行输出` 区视觉分离
- 流式渲染器在进入 tool-call 迭代前会调用 `resetBetweenIterations()`：`TerminalMarkdownRenderer` 按换行才 flush，没做这一步 HITL 提示会"跨过"还在 pending 缓冲区里的 reasoning/content 文本，造成标题与内容错位。Agent / SubAgent / PlanExecuteAgent 三条路径都做了相同处理；其中 ReAct 会重建渲染器但不会重复打印同一次用户输入的 `🧠 思考过程` 标题

#### 7.1 HITL 增强：路径围栏 / 命令快速拒绝 / 操作审计

HITL 是"用户在场时确认"，本子段是 HITL 之外的辅助层。路径围栏 / 命令黑名单不是沙箱；真正的可选命令进程隔离见下方 Docker sandbox。主模块在 `src/main/java/com/yucli/policy/`：

- `PathGuard`：`read_file` / `write_file` / `list_dir` / `create_project` 在执行前必须经过它，强制把路径限定在项目根之内。处理三类越界——绝对路径外逃、`..` 穿越、符号链接逃逸（向上找最近存在祖先做 `Files.toRealPath`，再把剩余段接回）
- `CommandGuard`：`execute_command` 进入 HITL 之前的 fast-fail 黑名单（sudo / rm -rf 全盘 / mkfs / dd of=/dev / fork bomb / curl|sh / find / / chmod 777 / / shutdown）。**定位是辅助 HITL，不是主防线**——黑名单永远列不全（base64 解码后执行、`eval`、写 `~/.bashrc` 持久化等都漏），它只是减少 HITL 弹窗骚扰。真正的安全责任在 HITL 审批
- `ResourceLimit` 类约束：`write_file` 单文件 5MB；`execute_command` 60 秒超时 + 8KB 输出截断（与第 7 期共用）
- `CommandSandboxDriver`：`execute_command` 支持可选 Docker sandbox，默认关闭。启用后 `ToolRegistry` 在通过 HITL / hooks / PermissionProfile / CommandGuard 后，把命令交给 `docker run --rm` 执行；项目目录挂载到容器 `/workspace`，默认镜像 `maven:3.9-eclipse-temurin-17`，默认 `--network none`，默认挂载 `rw`，超时/取消时尝试 `docker rm -f` 清理容器。配置读取系统属性、环境变量和 `.env`：`YuCLI.sandbox.enabled` / `YUCLI_SANDBOX_ENABLED`、`YuCLI.sandbox.docker.image` / `YUCLI_SANDBOX_DOCKER_IMAGE`、`YuCLI.sandbox.docker.network` / `YUCLI_SANDBOX_NETWORK`、`YuCLI.sandbox.docker.mount` / `YUCLI_SANDBOX_MOUNT`、`YuCLI.sandbox.docker.memory` / `YUCLI_SANDBOX_MEMORY`、`YuCLI.sandbox.docker.cpus` / `YUCLI_SANDBOX_CPUS`
- `AuditLog`：危险工具（`write_file` / `execute_command` / `create_project`）调用一律落一行 JSONL 到 `~/.YuCLI/audit/audit-YYYY-MM-DD.jsonl`，字段：`timestamp / tool / args / outcome (allow|deny|error) / reason / approver (hitl|policy|none) / durationMs`。审计写入失败仅 stderr 提示，不影响主流程
- `PermissionProfile`：读取 `~/.YuCLI/permissions.json` 和 `.YuCLI/permissions.json`，支持 `allow` / `deny` / `ask` 规则；`deny` 优先级最高，`allow` 会跳过 HITL，`ask` 或未命中规则继续交给 HITL / 默认策略处理；matcher 支持精确工具名、`*`、前缀通配和 `tool:argument-substring`
- 拦截出口：`PathGuard` / `CommandGuard` / 文件大小限制 都抛 `PolicyException`（`RuntimeException` 子类），由 `ToolRegistry.executeTool` 统一 catch、写 deny 审计、返回 `🛡️ 策略拒绝: ...`
- 与 HITL 协同顺序：`HitlToolRegistry` 先用 PermissionProfile 对原始参数做预判（显式 deny 不弹 HITL，显式 allow 跳过 HITL）；需要审批时再进入 HITL；审批通过后进入 `ToolRegistry`，`PreToolUse` 可修改参数，随后再次检查 PermissionProfile，再进入策略层和真实工具。HITL 拒绝/跳过写 `approver=hitl` 审计；Permission/策略拒绝写 `approver=policy` 审计；**用户无法批准策略拒绝的请求**
- CLI 命令：
  - `/policy`：查看安全策略状态（项目根 / 危险工具 / 黑名单 / 审计目录）
  - `/permissions`：查看当前权限 Profile 规则和来源
  - `/audit [N]`：看今日最近 N 条审计（默认 10，最大 100）
- 提示词联动：`Agent` / `PlanExecuteAgent` / `SubAgent` 三处都告知 LLM 安全策略硬规则与 `🛡️ 策略拒绝` 输出格式，避免 LLM 原样重试同一条违规请求
- **沙箱边界**：Docker sandbox 是实用级进程/文件系统隔离，不等同于 microVM；`rw` 挂载时仍可修改当前项目目录，`ro` 更安全但会让构建、测试输出、代码生成类任务失败。更强的 gVisor / Firecracker / per-SubAgent 独立文件系统仍属于后续 runtime isolation 路线。

#### 7.2 可配置 Hooks

- 主模块在 `src/main/java/com/yucli/hook/`
- 当前支持工具、Prompt 与 Agent 生命周期 hook；已接入 `ToolRegistry.executeTool()`、CLI/headless prompt submit、`Agent` / `PlanExecuteAgent` / `AgentOrchestrator`、`SubAgent.execute()`、`MemoryManager.compressIfNeeded()`
- 默认读取顺序：
  - 用户级：`~/.YuCLI/hooks.json`
  - 项目级：`.YuCLI/hooks.json`
- 当前支持事件：
  - `PreToolUse`：工具执行前触发；hook 命令非 0、HTTP 非 2xx、超时、执行失败或结构化 `deny` 会阻断本次工具调用，返回 `[Hook] PreToolUse 拒绝: ...`
  - `PostToolUse`：工具执行后触发；失败只向 stderr 打印警告，不改变工具结果
  - `UserPromptSubmit`：用户输入提交给 Agent 前触发；只对真正会运行 Agent 的输入触发，不拦 `/clear`、`/hooks`、`/exit`、`exit`、`q`、`退出` 等内部命令；支持 `deny` / `modify`，`modify.arguments.prompt` 会替换后续输入
  - `AgentStart` / `AgentFinish`：ReAct、Plan、Team 顶层 run 生命周期；warning-only
  - `SubAgentStart` / `SubAgentFinish`：Planner / Worker / Reviewer 子代理生命周期；warning-only
  - `PreCompact`：短期记忆压缩前触发；warning-only，失败不阻断压缩
- hook 执行器字段：
  - `command` / `commands`：本地命令，通过 stdin 接收 JSON payload
  - `url` / `urls`：HTTP POST JSON payload，2xx 视为成功
  - `prompt` / `prompts`：使用当前 LLM 做结构化 hook 决策，不传工具列表，避免 hook 内部递归 tool-call；未配置 LLM 时阻断型事件会拒绝，非阻断事件只 warning
- HTTP hook 支持 `headers`、`authToken`、`signatureSecret`、`retryCount`、`retryBackoffMillis`：`authToken` 自动补 `Authorization: Bearer ...`，`signatureSecret` 生成 `X-YuCLI-Signature: sha256=...`，429/5xx/超时/网络错误按 `retryCount` 重试。HTTP hook 的 `url` / `urls`、`headers` 值、`authToken`、`signatureSecret` 支持 `${ENV_NAME}` 环境变量占位符；缺失变量会导致当前 hook 配置文件被忽略，stderr 只打印缺失变量名，不打印原始配置值。hook 错误输出会对 token/key/password/secret/authorization 做脱敏；`/hooks` 会展示 URL，secret 优先放在 header / `authToken` / `signatureSecret` 中
- 非阻断事件可配置 `async: true` 后台执行，适合通知类 hook；阻断型事件仍同步执行，因为需要决定是否放行或修改参数
- `matcher` 支持精确值、`*`、前缀通配：
  - 工具事件匹配工具名，如 `write_file` / `mcp__*`
  - `UserPromptSubmit` 和顶层 Agent 事件匹配 mode：`react` / `plan` / `team`
  - SubAgent 事件匹配角色：`planner` / `worker` / `reviewer`
  - `PreCompact` 匹配 `short_term`
- 官方 hook recipes 位于 `examples/hooks/`，默认不自动加载；复制 `examples/hooks/hooks.json` 到 `.YuCLI/hooks.json` 后生效。示例脚本只使用 Python 标准库，启用前应按项目需要审阅和调整规则
- hook payload 固定包含 `event / hook_target / project_path / timestamp / arguments_raw / arguments`；工具事件额外包含 `tool_name / tool_call_id / result / elapsed_ms`，生命周期事件会把核心字段（如 `prompt`、`agent_type`、`agent_name`、`memory_scope`）同时放在顶层和 `arguments` 内
- 阻断型 hook（`PreToolUse` / `UserPromptSubmit`）stdout、HTTP body 或 LLM prompt 返回支持最后一行或整个输出结构化 JSON：
  - `{"decision":"allow"}`：显式放行
  - `{"decision":"deny","reason":"..."}`：阻断工具调用
  - `{"decision":"modify","arguments":{...}}`：`PreToolUse` 替换后续工具调用参数；`UserPromptSubmit` 只读取 `arguments.prompt` 替换后续 Agent 输入
- 非阻断事件（`PostToolUse`、Agent/SubAgent start/finish、`PreCompact`）会运行 command/http/prompt，但失败和 `deny/modify` 只向 stderr 打 warning，不改变主流程；配置 `async: true` 时这些执行器进入后台线程
- `/hooks` / `/hooks list`：查看当前 hook 启用状态、事件计数、matcher、command/http/prompt 数量、async 与 timeout
- 配置格式：

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
    "AgentFinish": [
      { "matcher": "*", "prompt": "Return allow after recording any notable run metadata." }
    ],
    "PostToolUse": [
      { "matcher": "*", "command": "python scripts/log_tool.py", "async": true }
    ]
  }
}
```

- HITL 与 hook 的协同顺序：`HitlToolRegistry` 先处理人工审批；审批通过后进入 `ToolRegistry`，再执行 `PreToolUse`、策略层、真实工具、`PostToolUse`
- HTTP hook、LLM prompt hook、非阻断异步 hook 已支持；阻断型 hook 必须同步执行

#### 7.3 Checkpoint / Undo

- 主模块在 `src/main/java/com/yucli/checkpoint/`
- CLI / headless 主入口会显式启用 checkpoint；裸 `new ToolRegistry()` 默认不写 checkpoint，避免嵌入式调用和单元测试污染 `~/.YuCLI`
- 默认持久化位置：`~/.YuCLI/checkpoints/<project-hash>/`
- `write_file` 和新建项目会在真实写入前调用 `CheckpointManager.checkpointBeforeWrite(...)`
- 如果目标原本存在且是普通文件，checkpoint 会复制原内容；如果目标原本不存在，checkpoint 会记录 missing，`/undo` 时删除该目标
- `/checkpoint`：查看最近 checkpoint
- `/undo`：恢复最近一次工具写入前状态，并删除对应 checkpoint 元数据；这是 YuCLI 工具层局部撤销，不修改 git 历史
- 当前不支持对已存在目录做完整目录快照；`create_project` 的可回滚路径主要覆盖新目录创建

### 8. 异步执行与并行工具调用

- 主入口在 `src/main/java/com/yucli/tool/ToolRegistry.java`
- `ToolRegistry.executeTools()` 负责批量执行同一轮 LLM 返回的多个工具调用
- 批量工具调用内部使用固定上限线程池并行执行，默认最多 4 个工具并发
- 返回结果保持原始 `tool_call` 顺序，调用方按这个顺序回灌 `tool` 消息，避免破坏 LLM 消息协议
- 批量工具调用有统一超时兜底，超时工具会返回 `工具执行超时（xx秒），已取消`
- `execute_command` 仍有独立的命令级超时，默认 60 秒
- `Agent`、`PlanExecuteAgent`、`SubAgent` 三条工具调用路径都应走 `executeTools()`，不要再各自手写逐个同步执行工具的 for-loop
- 系统提示词明确告知模型：同一轮多个工具调用会并行执行；如果工具之间有依赖关系，应分多轮调用
- `PlanExecuteAgent` 已支持同一 DAG 依赖批次内并行执行可执行任务
- `AgentOrchestrator` 已支持 Multi-Agent 同一依赖批次内部并行执行，默认最多 2 个 Worker 并发
- HITL 场景下危险工具仍会通过 `HitlToolRegistry.executeTool()` 透明拦截；终端审批由 `TerminalHitlHandler.requestApproval` 串行化，避免多线程同时抢 stdin/stdout

### 9. 联网能力（web_search + web_fetch）

- 主模块在 `src/main/java/com/yucli/web/`
- `SearchProvider` 接口 + 工厂：默认 `ZhipuSearchProvider`（与 GLM 推理共用 Key，国内首选），可切 `SerpApiSearchProvider` 或 `SearxngSearchProvider`，未来加 Brave / Tavily 只需实现接口
- `web_search` 工具不再返回拼接字符串，而是 provider 返回 `SearchResult` 列表（带 position / title / url / snippet / source），由 ToolRegistry 统一格式化
- `web_fetch` 工具链路：`NetworkPolicy.checkUrl()` → `acquire()`（限流）→ `WebFetcher.fetch()` → `HtmlExtractor.extract()`，全部本地，无第三方服务依赖
- `HtmlExtractor` 是简化版 readability：先按 `<article>` / `<main>` / `[role=main]` 选语义容器，否则按文本长度 - 链接占比惩罚 给 div/section 打分；最后递归转 Markdown（保留 h1-h6、列表、链接、代码块、表格）
- 边界明确：SPA / 防爬墙会返回 `body_empty: true` + `已知边界`提示，调用方不重试。JS 渲染 / 登录态留给第 13/14 期 CDP 路线
- 设计取舍：不做 LLM 二次摘要工具（混淆"工具 = 副作用"边界）；不引入 Jina（路线重叠，留到第 15 期 Skill 章节里作为 fallback）

### 10. MCP 协议接入

- 主模块在 `src/main/java/com/yucli/mcp/`
- 支持 stdio 子进程 server 与 Streamable HTTP 远程 server
- `McpConfigLoader` 读取 `~/.YuCLI/mcp.json` 和 `.YuCLI/mcp.json`，项目级按 server 名覆盖用户级
- `JsonRpcClient` 手写 JSON-RPC 2.0，请求 id 使用 `AtomicLong` 数字自增，请求响应配对用 `ConcurrentHashMap`
- `StdioTransport` 用 `ProcessBuilder` 启动子进程，stdout 读 newline-delimited JSON，stderr 单独 drain 到最近 200 行环形 buffer，避免 OS 缓冲填满造成死锁
- `StreamableHttpTransport` 复用 OkHttp，单 endpoint POST，支持 JSON 响应和 `text/event-stream` 的 SSE `data:` 消息，保存 `Mcp-Session-Id`
- `tools/list` 返回的工具会注册为 `mcp__{server}__{tool}`，并进入 LLM tool definitions
- `McpSchemaSanitizer` 会删 `$schema` / `$id` / `$ref`，把 `anyOf` / `oneOf` 降级为 object description，并截断超长 description
- `tools/call` 第一版只扁平化 text content；image / resource 返回 fallback 文本
- 所有 `mcp__` 前缀工具默认走 HITL 审批，并写入 AuditLog；审计参数会脱敏 Bearer、token、key、password、secret、authorization
- CLI 命令：
  - `/mcp`：查看所有 server 状态
  - `/mcp restart <name>`：重启单个 server
  - `/mcp logs <name>`：查看 stderr 环形 buffer
  - `/mcp disable <name>`：运行时禁用 server 并移除工具
  - `/mcp enable <name>`：运行时启用 server
- 当前不实现 `/mcp add` / `/mcp remove`，配置编辑通过文件 + 重启 YuCLI

### 11. MCP 高级能力首批

本期最早交付 resources / prompts / 被动通知 / 运行中取消；后续阶段已补 OAuth、sampling/createMessage 与 server 自动重启。

- resources 双轨：
  - 工具层：支持 resources capability 的 server 会自动注册 `mcp__{server}__list_resources` / `mcp__{server}__read_resource` 两个虚拟工具
  - 用户输入层：普通输入支持 `@server:protocol://path`，提交给 Agent 前由 `AtMentionExpander` 展开为 `<resource>` 内联块
- JLine 自动补全只接普通输入态；Plan / Team 审阅里的 raw-mode 单键路径不接 completer，避免干扰 `ESC` / `Ctrl+O`
- `McpResourceCache` 缓存 server 启动时的 resources list；收到 `notifications/resources/list_changed` 或 `notifications/resources/updated` 后只做失效标记，下次 list/read 重拉
- `notifications/tools/list_changed` 会触发该 server 工具列表全量替换，入口是 `ToolRegistry.replaceMcpToolsForServer(...)`
- `/mcp resources <name>`：查看 server 暴露的 resources
- `/mcp prompts <name>`：查看 server 暴露的 prompts；只查看，不执行 `prompts/get`，不注入对话流
- 官方 MCP 示例位于 `examples/mcp/`，用于 stdio、Streamable HTTP、header auth 与 OAuth 配置起步
- @-mention 读取 resource 记录 `approver=mention` 审计；通过虚拟工具读取仍走普通 `mcp__` 工具审计与 HITL 规则
- `/cancel`：任务运行期间输入 `/cancel` 并回车，请求取消当前 Agent run；ReAct、Plan、Team、工具批次与 `execute_command` 会在边界处检查 `CancellationToken`
- sampling/createMessage：`McpServerManager` 在启动时注册 sampling handler，路由到本地 LLM
- server 自动重启：stdio server 异常退出后自动重启（1s/5s/15s 退避，最多 3 次）
- prompts 注入：长上下文模式下 MCP prompt 模板自动注入 system prompt
- resources 自动注入：长上下文模式下 MCP resource URI + 描述自动注入 system prompt（第 12 期已实现）
- 当前明确不做：health ping / heartbeat、progress notification UI

### 11.1 Skill 系统

- 主模块在 `src/main/java/com/yucli/skill/`
- `SkillLoader` 扫描目录或 classpath 下的 `SKILL.md`，解析 frontmatter 中的 `name` / `description` / `triggers`
- `SkillRegistry` 加载内置 Skill 与用户级 `~/.YuCLI/skills/*/SKILL.md`；用户 Skill 可覆盖内置同名 Skill
- ReAct 入口会按触发词把匹配 Skill 的说明注入当前任务上下文；`/skill list`、`/skill on <name>`、`/skill off <name>`、`/skill reload` 管理启用状态
- `SKILL.md` 基本格式：

```markdown
---
name: code-review
description: Focused checklist for code review tasks
triggers: [review, code review]
---

Instructions injected when the prompt matches a trigger.
```

- 官方 Skill 示例位于 `examples/skills/`，默认不自动加载；复制到 `~/.YuCLI/skills/` 后运行 `/skill reload`
- 当前没有 `/skill template` 命令；可复制 `examples/skills/*/SKILL.md` 作为起点

### 12. TUI 模式

- 通过 `/tui` 命令启动，基于 Lanterna 3.1.1 的全屏终端 UI
- 主入口在 `src/main/java/com/yucli/tui/TuiApplication.java`
- 布局：顶部菜单栏、左侧对话区、右侧工作区（文件 / 代码 / 配置 Tab 切换）、底部状态栏
- 左侧对话区：`ChatPanel`，消息历史（带颜色区分 user/agent/system）+ 输入框 + 发送按钮
- 右侧三个 Tab：
  - `文件`：`FileTreePanel`，基于 `ActionListBox`，支持目录进入/返回、Enter 打开文件、文件数上限 50
  - `代码`：`CodePanel`，只读代码查看器，点击文件树中的文件自动加载
  - `配置`：`ConfigPanel`，模型名/模式名切换、清空历史、打开 .env 文件
- Tab 切换通过顶部按钮、菜单栏按钮或 F1/F2/F3 触发，自定义面板替换实现（Lanterna 3.1.1 无原生 TabbedPanel）
- 状态栏显示当前模型、当前模式、快捷键提示
- TUI 共享状态通过 `TuiContext` 事件总线：`onTabSwitch`、`onAction`、`fireTabSwitch`、`fireAction`
- 退出 TUI 后自动回到 CLI 模式，对话历史等状态在 `TuiContext` 中维护

### 13. OAuth 2.0 认证

- 主模块在 `src/main/java/com/yucli/mcp/auth/`
- 支持 OAuth 2.0 Authorization Code + PKCE 流程
- `McpOAuthClient`：PKCE 生成、授权 URL 构建、token 交换与刷新
- `OAuthCallbackServer`：本地 HTTP server 监听回调，CSRF state 校验，5 分钟超时
- `TokenStore`：持久化到 `~/.YuCLI/mcp-tokens.json`，按 server name 索引
- `TokenProvider` 接口：`StreamableHttpTransport` 自动注入 `Authorization: Bearer` header，401 自动 refresh 重试
- 配置：`mcp.json` 中 `oauth: true`、`clientId`、`scopes`、`authorizationEndpoint`、`tokenEndpoint`
- CLI 命令：
  - `/mcp auth <server>`：触发 OAuth 授权流程
  - `/mcp auth status`：查看各 server 认证状态和 token 过期时间
  - `/mcp auth revoke <server>`：清除已存储 token
- HITL：首次 OAuth 授权需用户确认（中危）
- AuditLog：记录 auth / refresh / revoke 事件

### 14. 插件系统

- 主模块在 `src/main/java/com/yucli/plugin/`
- `YuPlugin` 接口：`name()`、`description()`、`version()`、`onLoad(PluginContext)`、`onEnable()`、`onDisable()`、`onUnload()`
- `PluginContext`：受限 API 表面，`registerTool()`、`registerSearchProvider()`、`getConfigDir()`
- `PluginManager`：扫描 `~/.YuCLI/plugins/*.jar`，`URLClassLoader` + `ServiceLoader<YuPlugin>` 发现与加载
- `PluginTemplateGenerator`：`/plugin template <name>` 在当前工作目录生成最小 Maven 插件工程，包含 `YuPlugin` 实现、ServiceLoader 描述文件和 `echo` 示例工具；目标目录非空时拒绝覆盖
- 插件工具以 `plugin__` 前缀注册到 `ToolRegistry`，走 HITL 审批
- 插件状态持久化到 `~/.YuCLI/plugins.json`
- CLI 命令：
  - `/plugin list`：查看所有插件状态
  - `/plugin enable <name>`：启用插件
  - `/plugin disable <name>`：禁用插件
  - `/plugin reload`：重新加载所有插件
  - `/plugin template <name>`：生成 Java 插件模板
- 官方插件模板说明位于 `examples/plugins/`

### 15. 会话持久化

- 主模块在 `src/main/java/com/yucli/session/`
- `Session` 数据模型：sessionId、createdAt、updatedAt、modelName、provider、taskSummary、totalTokens、messages
- `SessionMessage`：role、content、timestamp、tokenCount、toolName、toolResult
- `SessionSerializer`：基于 Jackson 的 JSON 序列化，存储到 `~/.YuCLI/sessions/{sessionId}.json`
- `SessionManager`：会话 CRUD、自动保存调度（shutdown hook）、`findMostRecentUnclosed`、`findSessionByPartialId`
- `MemoryManager` 扩展：`exportToSession()` 和 `loadFromSession(Session)`
- 启动时检测未关闭会话，提示 `/resume` 恢复
- CLI 命令：
  - `/session list`：查看所有会话
  - `/session save [name]`：保存当前会话
  - `/session load <id>`：加载指定会话（支持部分 ID 匹配）
  - `/session delete <id>`：删除会话
  - `/session export <id> [path]`：导出会话
  - `/resume`：恢复最近未关闭的会话

### 16. Loop / Eval 轻量入口

- `/loop`：只读状态命令，展示 ReAct 循环的当前兜底规则。它基于 `AgentBudget` 暴露 Token 预算、重复工具调用停滞检测窗口、硬轮数上限；不调用 LLM、不执行工具、不改变会话状态
- `/eval`：只读说明入口，展示 EvalHarness 的用途、用例格式和手动运行命令；不运行 harness、不调用真实 LLM
- `/eval cases`：说明 `src/test/resources/eval/cases.json` 的 JSON array 格式；核心字段为 `id / mode / instruction / setupScript* / verifyScript*`，其中 `mode` 可选 `react` / `plan` / `team` 且默认 `react`，脚本字段支持通用版和 Windows / Unix 平台覆盖
- `/eval run`：只打印显式启用命令 `mvn test -Dtest=EvalHarness -DYuCLI.eval.enabled=true` 和风险提示
- `EvalHarness` 默认通过 `@EnabledIfSystemProperty(named = "YuCLI.eval.enabled", matches = "true")` 跳过，不能改成默认执行真实 LLM。涉及 eval harness 的改动至少保留一个测试确认默认门禁仍在
- Eval case 的 `setupScript` / `verifyScript` 都在临时目录内执行，`verifyScript` 退出码 `0` 表示通过；新增用例时不要依赖真实用户目录、全局状态或不可回收的副作用
- 当前内置 benchmark 用例覆盖 ReAct 文件读写、命令策略拒绝审计、Plan-and-Execute 文件转换、PreToolUse hook 拦截；Harness 会把 `YuCLI.audit.dir` 临时指向用例目录下的 `audit/`，并且只加载用例目录里的 `.YuCLI/hooks.json`，避免用户级 hook 污染结果

### 17. Headless Run

- 非交互入口：`yucli run <task> [--mode react|plan|team] [--json|--jsonl]`
- Jar 运行示例：`java -jar target/yucli-19.0.0.jar run "summarize this repo" --json`
- `run` 会在 banner / JLine / MCP 交互初始化之前处理，适合 CI、脚本和 GitHub Actions 包装
- 输出为单行 JSON / JSONL，字段包括 `task / mode / success / result / error / durationMs`
- 当前支持 `react`、`plan`、`team` 三种 mode；`plan` 使用自动执行的 plan review handler，`team` 会加载 SubAgent Profile
- Headless 模式会捕获内部流式 stdout，避免污染 JSON；如果 Agent 因流式输出返回空字符串，会把捕获的 transcript 写回 `result`
- 退出码：成功为 `0`，运行失败为 `1`，参数错误为 `2`

## 仓库结构

```text
src/main/java/com/yucli
├── agent/
│   ├── Agent.java
│   ├── PlanExecuteAgent.java
│   ├── AgentRole.java
│   ├── AgentMessage.java
│   ├── SubAgent.java
│   ├── AgentOrchestrator.java
│   └── config/
│       ├── AgentProfile.java
│       └── AgentProfileLoader.java
├── cli/
│   ├── Main.java
│   ├── CliCommandParser.java
│   └── PlanReviewInputParser.java
├── llm/
│   └── GLMClient.java
├── browser/
│   ├── CdpWebSocketClient.java
│   ├── ChromeLauncher.java
│   ├── ChromeDiscovery.java
│   ├── CdpSession.java
│   └── BrowserToolProvider.java
├── memory/
│   ├── ConversationMemory.java
│   ├── LongTermMemory.java
│   ├── MemoryManager.java
│   ├── MemoryRetriever.java
│   ├── ContextCompressor.java
│   ├── MemoryEntry.java
│   └── TokenBudget.java
├── plan/
│   ├── ExecutionPlan.java
│   ├── Planner.java
│   └── Task.java
├── rag/
│   ├── EmbeddingClient.java
│   ├── VectorStore.java
│   ├── CodeChunk.java
│   ├── CodeChunker.java
│   ├── CodeAnalyzer.java
│   ├── CodeRelation.java
│   ├── CodeIndex.java
│   └── CodeRetriever.java
├── tool/
│   └── ToolRegistry.java
├── mcp/
│   ├── McpClient.java
│   ├── McpServerManager.java
│   ├── McpServer.java
│   ├── config/
│   ├── jsonrpc/
│   ├── protocol/
│   ├── resources/
│   ├── mention/
│   ├── notifications/
│   ├── transport/
│   └── auth/
│       ├── McpOAuthClient.java
│       ├── OAuthCallbackServer.java
│       ├── TokenProvider.java
│       └── TokenStore.java
├── plugin/
│   ├── YuPlugin.java
│   ├── PluginContext.java
│   ├── PluginManager.java
│   ├── PluginTemplateGenerator.java
│   ├── PluginState.java
│   ├── PluginInfo.java
│   └── ToolExecutor.java
├── sandbox/
│   ├── SandboxConfig.java
│   ├── CommandSandboxDriver.java
│   ├── CommandProcessSpec.java
│   └── DockerSandboxDriver.java
├── session/
│   ├── Session.java
│   ├── SessionMessage.java
│   ├── SessionSerializer.java
│   └── SessionManager.java
├── hitl/
│   ├── ApprovalPolicy.java
│   ├── ApprovalRequest.java
│   ├── ApprovalResult.java
│   ├── HitlHandler.java
│   ├── TerminalHitlHandler.java
│   └── HitlToolRegistry.java
├── hook/
│   ├── HookManager.java
│   ├── HookConfigLoader.java
│   ├── HookConfig.java
│   ├── HookDefinition.java
│   ├── HookDecision.java
│   ├── HookEvent.java
│   ├── HookAsyncExecutor.java
│   ├── HookRedactor.java
│   └── HookCommandExecutor.java
├── checkpoint/
│   ├── CheckpointManager.java
│   └── CheckpointEntry.java
├── runtime/
│   ├── CancellationContext.java
│   ├── CancellationToken.java
│   └── headless/
│       ├── HeadlessRunner.java
│       ├── HeadlessRunRequest.java
│       ├── HeadlessRunResult.java
│       ├── HeadlessRunMode.java
│       ├── HeadlessTaskExecutor.java
│       └── JsonlEventWriter.java
├── web/
│   ├── SearchProvider.java
│   ├── ZhipuSearchProvider.java
│   ├── SerpApiSearchProvider.java
│   ├── SearxngSearchProvider.java
│   ├── SearchProviderFactory.java
│   ├── SearchResult.java
│   ├── WebFetcher.java
│   ├── HtmlExtractor.java
│   ├── NetworkPolicy.java
│   └── FetchResult.java
└── policy/
    ├── PolicyException.java
    ├── PathGuard.java
    ├── CommandGuard.java
    ├── PermissionProfile.java
    ├── PermissionProfileDecision.java
    ├── PermissionProfileLoader.java
    └── AuditLog.java
```

测试目前主要覆盖：

- `CliCommandParserTest`
- `PlanReviewInputParserTest`
- `MainInputNormalizationTest`
- `ExecutionPlanTest`
- `MemoryEntryTest`
- `ConversationMemoryTest`
- `LongTermMemoryTest`
- `MemoryRetrieverTest`
- `MemoryManagerTest`
- `PlanExecuteAgentTest`
- `AgentRoleTest`
- `AgentMessageTest`
- `AgentOrchestratorTest`
- `AgentProfileLoaderTest`
- `EmbeddingClientTest`
- `SearchResultTest`、`NetworkPolicyTest`、`HtmlExtractorTest`、`WebFetcherTest`、`SearchProviderFactoryTest`、`ZhipuSearchProviderTest`
- `VectorStoreTest`
- `CodeChunkerTest`
- `CodeAnalyzerTest`
- `CodeIndexTest`
- `ApprovalPolicyTest`
- `ApprovalResultTest`
- `HitlToolRegistryTest`
- `TerminalHitlHandlerTest`
- `HookDefinitionTest`、`HookConfigLoaderTest`、`HookHttpClientTest`、`HookManagerDecisionTest`、`ToolRegistryHookTest`、`ScopedToolRegistryTest`
- `CheckpointManagerTest`
- `HeadlessRunnerTest`
- `ToolRegistryTest`、`ToolRegistrySandboxTest`
- `SandboxConfigTest`、`DockerSandboxDriverTest`
- `McpSchemaSanitizerTest`、`McpConfigLoaderTest`、`JsonRpcClientTest`、`McpClientTest`、`McpToolRegistrationTest`、`McpResourceCacheTest`、`AtMentionParserTest`、`AtMentionExpanderTest`、`AtMentionCompleterTest`、`NotificationRouterTest`
- `PathGuardTest`、`CommandGuardTest`、`AuditLogTest`、`PermissionProfileTest`、`PermissionProfileLoaderTest`
- `TokenStoreTest`、`McpOAuthClientTest`
- `SkillLoaderTest`、`SkillRegistryTest`
- `PluginManagerTest`、`PluginTemplateGeneratorTest`
- `SessionManagerTest`
- `BrowserToolProviderTest`
- `TuiApplicationTest`
- `EvalHarnessTest`
- `ExampleConfigTest`

这意味着当前自动化测试更偏解析、计划结构、RAG 核心模块、Multi-Agent 编排逻辑、HITL 审批策略、策略层拦截规则、MCP 协议核心组件和 MCP resources 输入层，不覆盖真实 LLM 联调、真实 Embedding API 联调、真实 npm/uvx MCP server 联调，也不覆盖终端交互的完整手工体验。

## 核心文件说明

### `src/main/java/com/yucli/cli/Main.java`

- CLI 入口
- Banner 输出
- `.env` / 环境变量读取
- 日志目录初始化与 logback 配置系统属性注入
- ReAct 与 Plan 模式切换
- JLine 单键交互、raw mode、bracketed paste 处理

### `src/main/java/com/yucli/agent/Agent.java`

- ReAct 主循环
- 对话历史维护
- 工具调用执行与结果回灌

### `src/main/java/com/yucli/agent/PlanExecuteAgent.java`

- 规划后执行主流程
- 计划审阅
- DAG 任务执行
- 并行批次执行
- 失败后重规划

### `src/main/java/com/yucli/agent/AgentOrchestrator.java`

- Multi-Agent 编排器（主从架构中的"主"）
- 管理规划者、执行者、检查者三个角色
- 按依赖顺序分配步骤给 Worker
- 检查者审查结果，未通过则带反馈重试（最多 2 次）
- 解析规划者输出的 JSON 执行计划
- 解析检查者输出的审批结果

### `src/main/java/com/yucli/agent/SubAgent.java`

- 可配置角色的轻量子代理
- 三个角色对应三套系统提示词（规划者/执行者/检查者）
- 维护独立对话历史
- 执行者可使用工具调用，规划者和检查者不使用工具
- 支持流式输出（按角色显示不同标签）

### `src/main/java/com/yucli/agent/AgentRole.java`

- Agent 角色枚举：PLANNER、WORKER、REVIEWER
- 每个角色有显示名和描述

### `src/main/java/com/yucli/agent/AgentMessage.java`

- Agent 间通信消息类型
- 五种消息类型：TASK、RESULT、FEEDBACK、APPROVAL、REJECTION

### `src/main/java/com/yucli/plan/Planner.java`

- 调用 LLM 生成计划 JSON
- 对明显简单的任务走最小计划快捷路径，避免过度规划
- 解析任务列表
- 重新编号为 `task_1`、`task_2`...
- 计算依赖关系和执行顺序

### `src/main/java/com/yucli/plan/ExecutionPlan.java`

- DAG 拓扑排序
- 可执行任务判定
- 进度、状态、可视化与摘要

### `src/main/java/com/yucli/tool/ToolRegistry.java`

当前内置工具有 16 个：

- `read_file`
- `write_file`
- `list_dir`
- `execute_command`（在当前项目目录执行短时命令，默认 60 秒超时，不允许扫描 `/`、`~` 或整个文件系统）
- `create_project`
- `search_code`
- `web_search`（通过 `SearchProvider` 抽象，支持 zhipu / serpapi / searxng 三种实现；provider 未就绪时返回引导提示而非抛错）
- `web_fetch`（抓取 URL → 提取正文 → Markdown，本地实现；遇 SPA/防爬墙返回空正文 + 边界提示）
- `browser_navigate`（打开网页，支持 JS 渲染；优先连接已有 Chrome 实例，否则自动启动）
- `browser_screenshot`（页面/元素截图，保存 PNG）
- `browser_click`（CSS 选择器点击元素）
- `browser_type`（输入文本，可选提交）
- `browser_evaluate`（执行 JavaScript）
- `browser_get_dom`（获取页面文本内容）
- `browser_tab`（获取标签页列表 / 切换 / 创建 / 关闭）
- `browser_close`（关闭浏览器释放资源）

另外会动态注册 MCP 工具：

- `mcp__{server}__{tool}`：由 MCP server 的 `tools/list` 返回，`ToolRegistry.registerMcpTool()` 注入，执行时通过注册的 invoker 调用 `McpClient.callTool()`
- MCP 工具不是内置 8 个之一，但会进入同一套 LLM tool definitions、并行执行、HITL 和 AuditLog 流程
- 支持 resources capability 的 MCP server 还会注册两个虚拟工具：`mcp__{server}__list_resources` / `mcp__{server}__read_resource`

第七期新增的并行执行入口也在这里：

- `ToolInvocation`：封装一次工具调用的 id、工具名与 JSON 参数
- `ToolExecutionResult`：封装工具结果、耗时与是否超时
- `executeTools(List<ToolInvocation>)`：并行执行同一批工具调用，并按输入顺序返回结果

### `src/main/java/com/yucli/mcp/`

- `McpServerManager.java`：读取配置、并行启动 server、注册/移除 MCP 工具、处理 `/mcp` 系列命令
- `McpClient.java`：封装 `initialize`、`tools/list`、`tools/call`
- `jsonrpc/JsonRpcClient.java`：JSON-RPC 请求响应配对、通知路由和超时
- `transport/StdioTransport.java`：stdio 子进程三流管理，stderr 环形日志
- `transport/StreamableHttpTransport.java`：Streamable HTTP POST / SSE / session ID
- `protocol/McpSchemaSanitizer.java`：清洗 MCP inputSchema，避免模型不兼容 JSON Schema 子集
- `resources/`：resources/list + resources/read 的描述、缓存和虚拟工具封装
- `mention/`：解析 `@server:protocol://path`、JLine 候选补全、提交前展开 `<resource>` 块
- `notifications/NotificationRouter.java`：被动路由 MCP notification，当前处理工具列表变化和 resource cache 失效

### `src/main/java/com/yucli/llm/`

- `AnthropicClient`：Anthropic Messages wire，默认用于 DeepSeek Anthropic 兼容端点
- `AbstractOpenAiCompatibleClient`：OpenAI Chat Completions wire 的共用流式解析，负责消息、`reasoning_content`、tools、tool_calls、usage 解析
- `OpenAiResponsesClient`：OpenAI Responses wire，负责 `/v1/responses` 流式解析，并用 `reasoning.effort` 传递推理强度
- `DeepSeekClient` / `GLMClient` / `OpenAiCompatibleClient`：分别接入 DeepSeek、GLM、Qwen、通用 OpenAI-compatible provider；`*_BASE_URL` 可覆盖默认 endpoint

### `src/main/resources/logback.xml`

- 默认日志落盘配置
- 按天 + 按文件大小滚动
- 支持保留天数和总容量上限

## 当前已知边界

下面这些内容在路线图里出现了，但当前仓库还没有真正交付：

- 持久化后台任务队列 / 跨会话异步长任务调度
- runtime isolation：`execute_command` 已有默认关闭的 Docker sandbox driver；但还没有 gVisor / Firecracker / microVM，也没有每个 SubAgent 独立 filesystem 镜像和持久工作区
- Chrome DevTools MCP 已知边界：
  - 需要本地安装 Chrome/Chromium（自动查找系统安装，Windows/macOS/Linux 均支持，Edge 兜底）
  - 无头模式下部分网站可能有反爬检测
  - 复用已有 Chrome 实例需用户手动在 `--remote-debugging-port=9222` 上启动；程序会自动探测并优先复用
  - 登录态复用不隔离：复用的是用户默认 profile，如需隔离请额外启动带独立 `--user-data-dir` 的实例

不要把 `ROADMAP.md` 中”将来要做”误读成”现在已经有”。

## 修改时的硬规则

### 1. 改行为，不只改代码

如果你改的是用户可见行为，需要同步检查这些文档是否要更新：

- `AGENTS.md`
- `README.md`
- `ROADMAP.md`（仅在阶段目标或完成状态变化时）

### 2. 改命令入口，要联动这几处

如果修改 `/plan`、`/team`、`/loop`、`/eval`、`/agents`、`/hooks`、`run`、`/cancel`、`/hitl`、`/mcp`、`/policy`、`/permissions`、`/checkpoint`、`/undo`、`/audit`、`/browser`、`/skill`、`/tui`、`/clear`、`/memory`、`/save`、`/index`、`/search`、`/graph`、`/exit`、`/plugin`、`/session`、`/resume` 或输入解析：

- `Main.java`
- `CliCommandParser.java`
- 对应测试
- `README.md`
- `AGENTS.md`

当前输入解析约定补充：

- 任何未识别的 `/xxx` 都应在 CLI 层直接报“未知命令”
- 不要把未知 slash 命令回退给 Agent 当普通自然语言处理

### 3. 改 Plan 审阅交互，要联动这几处

如果修改 `Enter / ESC / I / Ctrl+O` 的行为：

- `Main.java`
- `PlanReviewInputParser.java`
- 相关测试
- `README.md`
- `AGENTS.md`

这块尤其要做真实手工验证，因为 raw mode 和行输入回退共存。

### 4. 改工具集，要联动这几处

如果新增、删除或修改工具：

- `ToolRegistry.java`
- `Agent.java` 的系统提示词
- `PlanExecuteAgent.java` 的执行提示词
- `SubAgent.java` 的 WORKER_PROMPT
- 如有必要，`Planner.java` 的规划提示词
- `README.md`
- `AGENTS.md`

不要只改工具注册，不改提示词，否则模型不会稳定学会使用。

### 5. 改模型或接口，要联动这几处

如果修改 GLM 模型名、接口地址、认证方式或配置项：

- `GLMClient.java`
- `Main.java`（如果配置读取方式变化）
- `.env.example`
- `README.md`
- `AGENTS.md`

### 5.1 改 Embedding 配置或向量存储，要联动这几处

如果修改 Embedding provider、模型、接口或向量存储结构：

- `EmbeddingClient.java`
- `VectorStore.java`
- `.env.example`
- `README.md`
- `AGENTS.md`

### 5.2 改搜索 / 抓取或网络策略，要联动这几处

如果新增 SearchProvider 实现，或调整 NetworkPolicy / WebFetcher 行为：

- `src/main/java/com/yucli/web/` 下相关文件
- `ToolRegistry.java` 的 `webSearch` / `webFetch` 实现
- `SearchProviderFactory.pickProvider` 的环境变量优先级
- `.env.example`：补充新的环境变量示例
- `README.md` 与 `AGENTS.md`：工具列表 / 安全策略段落
- 至少补一个相应的单元测试

### 5.3 改 Memory 持久化或预算策略，要联动这几处

- `MemoryManager.java`
- `LongTermMemory.java`
- `TokenBudget.java`
- 至少一个 memory 测试
- `README.md`
- `AGENTS.md`

### 5.4 改 HITL 增强（路径围栏 / 命令黑名单 / 审计 / 权限 Profile / checkpoint），要联动这几处

如果新增黑名单规则、调整 PathGuard 行为、改 AuditLog 字段、调整 PermissionProfile 规则、或修改 checkpoint/undo 行为：

- `src/main/java/com/yucli/policy/` 下相关文件
- `src/main/java/com/yucli/checkpoint/` 下相关文件
- `ToolRegistry.java`：执行入口与拦截路径
- `HitlToolRegistry.java`：HITL 审批与策略层审计的协同
- `Agent.java` / `PlanExecuteAgent.java` / `SubAgent.java` 的系统提示词：让 LLM 知道新增规则与 `🛡️ 策略拒绝` 输出格式
- `Main.java` / `CliCommandParser.java`：如果新增 `/policy`、`/permissions`、`/checkpoint`、`/undo`、`/audit` 行为变化
- `.env.example`：新增的环境变量示例（如 `YuCLI_AUDIT_DIR`）
- `README.md` 与 `AGENTS.md`：HITL 增强子段 + 命令列表
- 至少补一个对应的单元测试（`PathGuardTest` / `CommandGuardTest` / `AuditLogTest` / `PermissionProfileTest` / `CheckpointManagerTest`）

### 5.5 改 MCP 协议或 server 管理，要联动这几处

如果新增 MCP transport、调整配置格式、改变工具命名、改 `tools/list` / `tools/call` / `resources/list` / `resources/read` 行为、调整 `@server:protocol://path` 输入层、或新增 `/mcp` 子命令：

- `src/main/java/com/yucli/mcp/` 下相关文件
- `ToolRegistry.java`：MCP 工具注册、卸载、执行路由、审计判断
- `ApprovalPolicy.java` / `ApprovalRequest.java` / `HitlToolRegistry.java`：MCP 默认审批和展示信息
- `AuditLog.java`：确保 args 脱敏仍覆盖 token / key / password / authorization / Bearer
- `Main.java` / `CliCommandParser.java`：启动加载、命令解析和命令输出
- `Agent.java` / `PlanExecuteAgent.java` / `SubAgent.java`：提示词中的 MCP 工具说明和策略拒绝规则
- `.env.example`、`README.md`、`ROADMAP.md`、`AGENTS.md`
- 至少补一个对应的 MCP 单元测试（配置、schema、JSON-RPC、resources、mention、notification、工具注册或 CLI 解析）

### 6. 不要提交敏感信息或产物垃圾

- 不提交 `.env`
- 不把真实 API Key 写进代码或文档示例
- 不手改 `target/` 里的生成产物

### 7. 保持代码可读性

这个仓库强调结构清晰、逻辑可读，是要长期维护演进的商业产品。

所以改动时优先：

- 结构清晰
- 逻辑可读
- 文件职责明确
- 少量但必要的注释

不要为了"工程炫技"把简单逻辑过度抽象到难以理解和维护。

## 建议验证路径

### 文档或轻微重构

- 至少跑 `mvn test`

### 命令解析相关

```bash
mvn test -Dtest=CliCommandParserTest,PlanReviewInputParserTest,MainInputNormalizationTest
```

### 计划执行/DAG 相关

```bash
mvn test -Dtest=ExecutionPlanTest
```

### Multi-Agent 相关

```bash
mvn test -Dtest=AgentRoleTest,AgentMessageTest,AgentOrchestratorTest
```

### 改了交互或终端输入

除了测试，还应手工 smoke test：

1. 启动 CLI
2. 输入 `/plan`
3. 验证 `ESC` 取消待执行 plan
4. 验证 `Ctrl+O` 展开计划
5. 验证 `I` 可以补充要求后重规划
6. 验证执行完成后自动回到默认 `ReAct`

## 给新线程的工作建议

进入仓库后，建议按这个顺序建立上下文：

1. 先看本文件
2. 再看 `README.md`
3. 再看 `Main.java`
4. 然后根据任务进入对应模块

如果用户提的是：

- CLI 命令问题：先看 `Main.java` + `CliCommandParser.java`
- 规划/DAG 问题：先看 `PlanExecuteAgent.java` + `Planner.java` + `ExecutionPlan.java`
- 工具调用问题：先看 `ToolRegistry.java` + `Agent.java`
- API/模型问题：先看 `GLMClient.java`
- RAG/代码检索问题：先看 `CodeRetriever.java` + `CodeIndex.java` + `VectorStore.java`
- 代码分块/AST 问题：先看 `CodeChunker.java` + `CodeAnalyzer.java`
- Multi-Agent 协作问题：先看 `AgentOrchestrator.java` + `SubAgent.java` + `AgentRole.java` + `AgentMessage.java`
- 浏览器/CDP 问题：先看 `BrowserToolProvider.java` + `CdpSession.java` + `CdpWebSocketClient.java`

## 持续维护约定

以后凡是形成了稳定协作规则，例如：

- 哪些文件改动必须联动哪些测试
- 哪些行为以代码为准而 README 常滞后
- 哪些版本号或运行方式存在容易误判的地方
- 哪些模块是后续线程最容易踩坑的地方

都直接补进 `AGENTS.md`，不要只留在聊天记录里。
