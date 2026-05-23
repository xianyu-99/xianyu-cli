# YuCLI 项目架构全景分析

> **项目定位**：面向商业使用的 Java Agent CLI 产品，对标 Claude Code。从 ReAct 循环持续演进到完整 Agent 产品形态，已完成 11 期迭代（v11.0.0），路线图规划到第 16 期。

---

## 一、项目概览

| 维度 | 说明 |
|------|------|
| 项目名 | YuCLI |
| 语言 | Java 17+ |
| 构建工具 | Maven |
| 当前版本 | v11.0.0 |
| 默认 LLM | GLM-5.1（也支持 DeepSeek 等多模型切换） |
| 路线图 | 16 期迭代（第 1-11 期已完成，第 12-16 期规划中） |

**已完成迭代**：

| 期数 | 主题 | 核心产出 |
|------|------|----------|
| 1 | ReAct + Tool Call | ReAct 循环、GLM-5.1 集成、5 个基础工具 |
| 2 | Plan-and-Execute + DAG | 任务分解、依赖管理、计划可视化 |
| 3 | Memory + 上下文工程 | 短期/长期记忆、摘要压缩、Token 预算 |
| 4 | RAG 检索 + 代码库理解 | 代码向量化、语义检索、AST 分析 |
| 5 | Multi-Agent 协作 | 角色分工（规划者/执行者/检查者）、任务分配 |
| 6 | HITL 人工审批 | 危险操作拦截、路径围栏、命令快速拒绝、审计 |
| 7 | 异步执行 + 并行工具 | CompletableFuture 并行调度、流式隔离 |
| 8 | 多模型适配 | 运行时切换、OpenAI 兼容抽象层 |
| 9 | 联网能力 + Web 工具 | 多 search provider、web_fetch |
| 10 | MCP 协议核心 | stdio + Streamable HTTP、JSON-RPC 2.0 |
| 11 | MCP 高级能力 | resources 双轨、prompts 查看、被动通知、@-mention |

---

## 二、项目目录结构

```
YuCLI-main/
├── AGENTS.md                    # Agent 协作入口文档
├── CLAUDE.md                    # 兼容保留文件
├── README.md                    # 项目说明
├── ROADMAP.md                   # 16 期迭代路线图
├── pom.xml                      # Maven 配置
├── .env.example                 # 环境变量模板
├── docs/
│   ├── phase-10-mcp-core.md     # MCP 核心设计文档
│   └── phase-11-mcp-advanced.md # MCP 高级能力设计文档
├── demo/                        # 示例 Spring Boot 项目（用于 RAG 测试）
├── src/main/java/com/YuCLI/
│   ├── agent/                   # Agent 核心
│   │   ├── Agent.java           # ReAct 单 Agent 循环
│   │   ├── AgentBudget.java     # Token/轮数/停滞三重预算
│   │   ├── AgentMessage.java    # Agent 间通信消息
│   │   ├── AgentRole.java       # 角色枚举 (PLANNER/WORKER/REVIEWER)
│   │   ├── AgentOrchestrator.java # Multi-Agent 编排器（主从架构）
│   │   ├── PlanExecuteAgent.java  # Plan-and-Execute Agent
│   │   └── SubAgent.java        # 子 Agent（具名角色 + 对话历史）
│   ├── cli/                     # CLI 入口
│   │   ├── Main.java            # v11.0.0 主入口 (JLine3 终端)
│   │   ├── CliCommandParser.java
│   │   └── PlanReviewInputParser.java
│   ├── config/
│   │   └── YuCLIConfig.java    # 全局配置
│   ├── hitl/                    # Human-In-The-Loop
│   │   ├── ApprovalPolicy.java  # 审批策略（危险工具定义）
│   │   ├── ApprovalRequest.java
│   │   ├── ApprovalResult.java
│   │   ├── HitlHandler.java     # 审批处理器接口
│   │   ├── HitlToolRegistry.java # HITL 装饰器（覆写 executeTool）
│   │   └── TerminalHitlHandler.java # 终端交互审批
│   ├── llm/                     # LLM 客户端
│   │   ├── LlmClient.java       # 客户端接口
│   │   ├── LlmClientFactory.java # 工厂（按模型名创建）
│   │   ├── AbstractOpenAiCompatibleClient.java # OpenAI 兼容抽象基类
│   │   ├── GLMClient.java       # 智谱 GLM 适配
│   │   └── DeepSeekClient.java  # DeepSeek 适配
│   ├── mcp/                     # MCP (Model Context Protocol)
│   │   ├── McpClient.java       # MCP 客户端
│   │   ├── McpServer.java       # MCP Server 封装
│   │   ├── McpServerManager.java # Server 生命周期管理
│   │   ├── McpServerStatus.java
│   │   ├── config/
│   │   │   ├── McpConfigFile.java
│   │   │   ├── McpConfigLoader.java
│   │   │   └── McpServerConfig.java
│   │   ├── jsonrpc/
│   │   │   ├── JsonRpcClient.java   # JSON-RPC 2.0 实现
│   │   │   ├── JsonRpcMessage.java
│   │   │   └── JsonRpcException.java
│   │   ├── protocol/
│   │   │   ├── McpCallToolRequest.java / McpCallToolResult.java
│   │   │   ├── McpInitializeRequest.java / McpInitializeResult.java
│   │   │   ├── McpToolDescriptor.java / McpCapabilities.java
│   │   │   ├── McpContent.java
│   │   │   └── McpSchemaSanitizer.java
│   │   ├── transport/
│   │   │   ├── McpTransport.java          # 传输接口
│   │   │   ├── StdioTransport.java        # 子进程 stdin/stdout
│   │   │   └── StreamableHttpTransport.java # HTTP SSE
│   │   ├── resources/
│   │   │   ├── McpResourceCache.java
│   │   │   ├── McpResourceContent.java
│   │   │   ├── McpResourceDescriptor.java
│   │   │   └── McpResourceTool.java
│   │   ├── mention/
│   │   │   ├── AtMentionParser.java
│   │   │   ├── AtMentionExpander.java
│   │   │   └── AtMentionCompleter.java
│   │   └── notifications/
│   │       └── NotificationRouter.java
│   ├── memory/                  # Memory 系统
│   │   ├── Memory.java          # 记忆接口
│   │   ├── MemoryManager.java   # 门面类（统一管理）
│   │   ├── MemoryEntry.java     # 记忆条目
│   │   ├── ConversationMemory.java # 短期记忆
│   │   ├── LongTermMemory.java  # 长期记忆（JSON 文件持久化）
│   │   ├── MemoryRetriever.java # 记忆检索（BM25 相似度）
│   │   ├── MemoryQueryTokenizer.java # Jieba 分词
│   │   ├── ContextCompressor.java # Map-Reduce 摘要压缩
│   │   └── TokenBudget.java     # Token 预算管理
│   ├── plan/                    # 规划系统
│   │   ├── Planner.java
│   │   ├── ExecutionPlan.java
│   │   └── Task.java
│   ├── policy/                  # 安全策略
│   │   ├── PathGuard.java       # 路径围栏（越界检测）
│   │   ├── CommandGuard.java    # 命令快速拒绝（黑名单）
│   │   ├── AuditLog.java        # 操作审计日志（JSONL）
│   │   └── PolicyException.java
│   ├── rag/                     # RAG 检索系统
│   │   ├── CodeIndex.java       # 代码索引
│   │   ├── CodeRetriever.java   # 代码检索
│   │   ├── CodeChunker.java     # 代码分块
│   │   ├── CodeAnalyzer.java    # AST 分析
│   │   ├── CodeChunk.java
│   │   ├── CodeRelation.java
│   │   ├── VectorStore.java     # 向量存储 (SQLite + 余弦检索)
│   │   ├── EmbeddingClient.java
│   │   ├── SearchResultFormatter.java
│   │   └── RagQueryTokenizer.java
│   ├── runtime/                 # 运行时控制
│   │   ├── CancellationContext.java  # ThreadLocal 取消上下文
│   │   └── CancellationToken.java
│   ├── tool/                    # 工具系统
│   │   └── ToolRegistry.java    # 工具注册表（内置 + MCP 动态工具）
│   ├── util/
│   │   ├── AnsiStyle.java           # 终端样式
│   │   ├── TerminalMarkdownRenderer.java # Markdown 渲染
│   │   └── JiebaSegmenterFactory.java
│   └── web/                     # Web 能力
│       ├── WebFetcher.java      # 网页抓取
│       ├── HtmlExtractor.java   # HTML 提取
│       ├── NetworkPolicy.java   # 网络安全策略
│       ├── SearchProvider.java  # 搜索接口
│       ├── SearchProviderFactory.java
│       ├── SearchResult.java
│       ├── FetchResult.java
│       ├── ZhipuSearchProvider.java
│       ├── SerpApiSearchProvider.java
│       └── SearxngSearchProvider.java
└── src/test/                    # 测试（对应 main 结构）
```

---

## 三、Agent 对话流程图

### 3.1 总体流程

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                    YuCLI Agent 对话流程图                                  │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                          │
│                               ┌─────────────────────┐                                    │
│                               │   用户输入 (CLI)      │                                    │
│                               │ Main.java (JLine)   │                                    │
│                               └─────────┬───────────┘                                    │
│                                         │                                                │
│                    ┌────────────────────┼────────────────────┐                           │
│                    ▼                    ▼                     ▼                          │
│            ┌───────────┐    ┌──────────────────┐    ┌──────────────┐                    │
│            │ /plan 模式 │    │   /multi 模式     │    │  默认 ReAct  │                    │
│            │PlanExecute │    │AgentOrchestrator  │    │   Agent.run  │                    │
│            │   Agent    │    │  (Multi-Agent)    │    │  (单Agent循环)│                    │
│            └─────┬─────┘    └────────┬──────────┘    └──────┬───────┘                    │
│                  │                  │                      │                            │
│                  └──────────────────┼──────────────────────┘                            │
│                                     ▼                                                   │
│                    ┌─────────────────────────────────────┐                              │
│                    │         MemoryManager               │                              │
│                    │  ┌─────────────┐ ┌────────────────┐ │                              │
│                    │  │ 短期记忆     │ │ 长期记忆(LTM)   │ │                              │
│                    │  │Conversation │ │ LongTermMemory  │ │                              │
│                    │  │  Memory     │ │  (JSON持久化)   │ │                              │
│                    │  └─────────────┘ └────────────────┘ │                              │
│                    │  ┌─────────────┐ ┌────────────────┐ │                              │
│                    │  │ 上下文压缩   │ │  记忆检索       │ │                              │
│                    │  │ Compressor  │ │  Retriever      │ │                              │
│                    │  └─────────────┘ └────────────────┘ │                              │
│                    │  ┌─────────────────────────────────┐ │                              │
│                    │  │     TokenBudget (预算管理)      │ │                              │
│                    │  └─────────────────────────────────┘ │                              │
│                    └──────────────┬──────────────────────┘                              │
│                                   ▼                                                     │
│                    ┌─────────────────────────────────────┐                              │
│                    │          ToolRegistry               │                              │
│                    │  ┌──────────┐ ┌──────────┐         │                              │
│                    │  │ 内置工具  │ │MCP工具   │         │                              │
│                    │  │·file操作 │ │·动态注册 │         │                              │
│                    │  │·shell    │ │·namespaced│        │                              │
│                    │  │·code检索 │ │·外部server│        │                              │
│                    │  │·web搜索  │ └──────────┘         │                              │
│                    │  └──────────┘                       │                              │
│                    └──────────────┬──────────────────────┘                              │
│                                   │                                                     │
│                    ┌──────────────┼──────────────┐                                      │
│                    ▼              ▼              ▼                                      │
│            ┌───────────┐  ┌─────────────┐  ┌──────────────┐                            │
│            │ PathGuard │  │CommandGuard │  │  AuditLog    │                            │
│            │ 路径围栏   │  │ 命令快速拒绝 │  │  操作审计     │                            │
│            └─────┬─────┘  └──────┬──────┘  └──────┬───────┘                            │
│                  │               │                │                                     │
│                  └───────────────┼────────────────┘                                     │
│                                  ▼                                                      │
│                    ┌─────────────────────────────────────┐                              │
│                    │          HITL 人工审批层            │                              │
│                    │   HitlToolRegistry > ApprovalPolicy │                              │
│                    │   TerminalHitlHandler (终端交互)     │                              │
│                    └──────────────┬──────────────────────┘                              │
│                                   ▼                                                     │
│                    ┌─────────────────────────────────────┐                              │
│                    │         工具实际执行                 │                              │
│                    └──────────────┬──────────────────────┘                              │
│                                   ▼                                                     │
│                    ┌─────────────────────────────────────┐                              │
│                    │      结果回写 → Memory + LLM        │                              │
│                    └──────────────┬──────────────────────┘                              │
│                                   ▼                                                     │
│                    ┌─────────────────────────────────────┐                              │
│                    │    Budget 检查 (Token/轮数/停滞)     │                              │
│                    │    → 继续循环 或 返回结果给用户       │                              │
│                    └─────────────────────────────────────┘                              │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 LLM 调用链

```
                                LLM 调用链

  ToolRegistry
      │
      ▼
  ┌──────────────┐     ┌──────────────────┐     ┌──────────────┐     ┌──────────────────┐
  │  DeepSeek    │     │     GLM-5.1      │     │ 其他兼容API   │     │   未来模型       │
  │  Client      │     │    Client         │     │ (OpenAI兼容)  │     │   (扩展点)      │
  └──────┬───────┘     └────────┬─────────┘     └──────┬───────┘     └────────┬─────────┘
         │                      │                      │                      │
         └──────────────────────┼──────────────────────┘                      │
                                ▼                                              │
                    ┌──────────────────────────┐
                    │   AbstractOpenAiCompatible│
                    │   Client (OkHttp + SSE)   │
                    └──────────────────────────┘
```

### 3.3 ReAct 循环详细流程

```
                 ┌──────────────────────────────┐
                 │    用户输入 "帮我重构XX"      │
                 └──────────────┬───────────────┘
                                │
                                ▼
                 ┌──────────────────────────────┐
                 │  1. MemoryManager             │
                 │  · addUserMessage(输入)       │
                 │  · buildContextForQuery(输入) │
                 │    → 检索相关长期记忆注入      │
                 │    system prompt              │
                 └──────────────┬───────────────┘
                                │
                                ▼
                 ┌──────────────────────────────┐
                 │  2. LLM 思考 (Reasoning)      │
                 │  POST /chat/completions       │
                 │  (SSE 流式返回)               │
                 └──────────────┬───────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
          ┌──────────────────┐   ┌──────────────────┐
          │  LLM 返回文本    │   │  LLM 返回工具调用  │
          │  → 直接回复用户  │   │  (tool_calls)    │
          └──────────────────┘   └────────┬─────────┘
                                          │
                                          ▼
                               ┌──────────────────────┐
                               │ 3. ToolRegistry       │
                               │ · 解析 tool_calls    │
                               │ · 多个调用 → 并行执行  │
                               │ · MCP工具 → McpClient │
                               │   转发到外部 server   │
                               └──────────┬───────────┘
                                          │
                              ┌───────────┴───────────┐
                              ▼                       ▼
                    ┌──────────────────┐   ┌──────────────────┐
                    │  策略检查         │   │  HITL 审批       │
                    │  PathGuard       │   │  (危险工具)      │
                    │  CommandGuard    │   │  TerminalHitl    │
                    │  NetworkPolicy   │   │  Handler         │
                    └────────┬─────────┘   └────────┬─────────┘
                             │                      │
                             └──────────┬───────────┘
                                        ▼
                               ┌──────────────────────┐
                               │ 4. 工具执行           │
                               │ · AuditLog.record()  │
                               │ · 结果截断 500 字     │
                               │ · addToolResult()    │
                               └──────────┬───────────┘
                                          │
                                          ▼
                               ┌──────────────────────┐
                               │ 5. 回写 LLM          │
                               │ · addAssistantMessage│
                               │ · 工具结果加入历史    │
                               │ · 下一轮 LLM 推理    │
                               └──────────┬───────────┘
                                          │
                              ┌───────────┴───────────┐
                              ▼                       ▼
                    ┌──────────────────┐   ┌──────────────────┐
                    │ LLM 继续调用工具 │   │ LLM 返回最终结果  │
                    │ → 回到步骤 3     │   │ → 回复用户        │
                    └──────────────────┘   └──────────────────┘
```

### 3.4 Multi-Agent 协作流程

```
              ┌─────────────────────────────────────────────┐
              │              AgentOrchestrator               │
              └─────────────────────┬───────────────────────┘
                                    │
                                    ▼
              ┌─────────────────────────────────────────────┐
              │  第一阶段: 规划                              │
              │                                              │
              │  用户任务 ──→ Planner (规划者)               │
              │                 │                            │
              │                 ▼                            │
              │    ExecutionPlan (DAG)                       │
              │    · step-1: 分析XX   [依赖: 无]             │
              │    · step-2: 实现YY   [依赖: step-1]        │
              │    · step-3: 测试ZZ   [依赖: step-1]        │
              │    · step-4: 汇总     [依赖: step-2, step-3] │
              └─────────────────────┬───────────────────────┘
                                    │
                                    ▼
              ┌─────────────────────────────────────────────┐
              │  第二阶段: 执行（按 DAG 拓扑排序）           │
              │                                              │
              │  批次 1: [step-1]                            │
              │    → Worker-1 执行 ──→ Reviewer 检查        │
              │       · 失败 → 重试 (最多2次)               │
              │                                              │
              │  批次 2: [step-2, step-3]  ← 并行执行!      │
              │    → Worker-1 执行 step-2 ──→ Reviewer 检查 │
              │    → Worker-2 执行 step-3 ──→ Reviewer 检查 │
              │    (独立 PrintStream 缓冲，按序 flush)       │
              │                                              │
              │  批次 3: [step-4]                            │
              │    → Worker-1 执行 ──→ Reviewer 检查        │
              └─────────────────────┬───────────────────────┘
                                    │
                                    ▼
              ┌─────────────────────────────────────────────┐
              │  第三阶段: 汇总返回                          │
              │  所有步骤完成 → 合并结果 → 返回用户          │
              └─────────────────────────────────────────────┘
```

---

## 四、工具调用系统

### 4.1 架构

```
┌────────────────────────────────────────────────────────────┐
│                      ToolRegistry                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │                    内置工具 (8类)                     │ │
│  │                                                      │ │
│  │  文件操作: read_file / write_file / list_dir         │ │
│  │  Shell:    execute_command                           │ │
│  │  项目:     create_project                            │ │
│  │  代码检索: search_code (语义检索)                     │ │
│  │  Web:      web_search / web_fetch                    │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │                 MCP 动态工具                          │ │
│  │                                                      │ │
│  │  mcp__{server}__{tool} 命名空间格式                   │ │
│  │  · 从 MCP server 的 tools/list 获取                  │ │
│  │  · 运行时动态注册/注销                                │ │
│  │  · schema 安全清洗 (McpSchemaSanitizer)              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │                 并行执行引擎                          │ │
│  │                                                      │ │
│  │  · CompletableFuture 并行 (最多 4 个并发)            │ │
│  │  · 默认超时 60s (命令) / 90s (批量)                  │ │
│  │  · 命令输出截断 8000 字符                            │ │
│  │  · write_file 5MB 上限                               │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 4.2 工具执行流程

```
LLM 返回 tool_calls (JSON)
    │
    ▼
ToolRegistry.executeTool(name, argumentsJson)
    │
    ├── 是 MCP 工具? ──→ McpClient.callTool() ──→ 外部 MCP server
    │
    ├── 是内置工具? ──→ 查找 tools Map
    │       │
    │       ├── 文件类 → PathGuard.resolveSafe() 校验 → 执行
    │       ├── Shell类 → CommandGuard.check() 校验 → 执行
    │       └── Web类   → NetworkPolicy 校验 → 执行
    │
    └── 工具不存在 → 返回错误提示
```

---

## 五、MCP 协议集成

### 5.1 架构总览

```
┌──────────────────────────────────────────────────────────┐
│                    MCP 子系统架构                         │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  McpServerManager (生命周期管理)                          │
│      │                                                   │
│      ├── McpConfigLoader (读取 mcp.json)                 │
│      │    用户级: ~/.YuCLI/mcp.json                     │
│      │    项目级: .YuCLI/mcp.json                       │
│      │    格式兼容: claude_desktop_config.json            │
│      │                                                   │
│      ├── McpTransport (传输层)                           │
│      │    ├── StdioTransport (子进程 stdin/stdout)       │
│      │    └── StreamableHttpTransport (HTTP SSE)         │
│      │                                                   │
│      ├── McpClient (每server一个实例)                    │
│      │    ├── initialize (握手 + 能力协商)               │
│      │    ├── tools/list → 动态注册到 ToolRegistry       │
│      │    ├── tools/call → 命名空间 mcp__{name}__{tool}  │
│      │    ├── resources/list + resources/read             │
│      │    └── prompts/list + prompts/get                  │
│      │                                                   │
│      ├── JsonRpcClient (JSON-RPC 2.0 协议实现)           │
│      │                                                   │
│      ├── McpResourceCache (资源内容缓存)                  │
│      │                                                   │
│      └── NotificationRouter (被动通知路由)                │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 5.2 MCP 配置文件格式

兼容 Claude Code 的 `claude_desktop_config.json` 格式：

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"],
      "env": {}
    },
    "playwright": {
      "url": "http://localhost:3000/mcp",
      "headers": {
        "Authorization": "Bearer token"
      }
    }
  }
}
```

- `${PROJECT_DIR}` 和 `${HOME}` 是内置变量
- 其他 `${VAR}` 从环境变量读取，缺失会直接报错
- 没有 MCP 配置文件时，MCP 子系统默认开启但不启动外部 server

### 5.3 MCP 生命周期

```
加载配置 → 创建 McpServer → 创建 Transport → 启动连接 → 初始化握手
    │                                                          │
    │  1. initialize (能力协商)                                │
    │  2. notifications/initialized                            │
    │  3. tools/list → 动态注册到 ToolRegistry                 │
    │  4. resources/list → 缓存到 McpResourceCache             │
    │                                                          │
    ▼                                                          ▼
  运行时:                                                   关闭:
  · tools/call (通过命名空间)                                · server.close()
  · resources/read (URI 路径)                               · 注销所有 MCP 工具
  · @-mention 资源引用                                       · 清理传输连接
  · 被动通知接收 (NotificationRouter)
```

---

## 六、Memory 管理系统

### 6.1 三层记忆架构

```
┌───────────────────────────────────────────────────────┐
│                 MemoryManager (门面)                   │
├───────────────────────────────────────────────────────┤
│                                                       │
│  ┌─────────────────┐  ┌─────────────────┐            │
│  │ ConversationMemory│  │ LongTermMemory  │            │
│  │   (短期记忆)      │  │  (长期记忆)     │            │
│  │                   │  │                  │            │
│  │ · 内存 Map 存储   │  │ · JSON 文件持久化│            │
│  │ · 三种 Entry 类型:│  │ · FACT 类型条目 │            │
│  │   CONVERSATION   │  │ · 位置:           │            │
│  │   TOOL_RESULT    │  │   ~/.YuCLI/memory│            │
│  │   FACT           │  │   /long_term_     │            │
│  │                   │  │   memory.json     │            │
│  │ 存储策略:         │  │                  │            │
│  │ · 用户消息: 完整   │  │ · storeFact()    │            │
│  │ · 助手消息: 完整   │  │   关键信息持久化 │            │
│  │ · 工具结果: 500字  │  │                  │            │
│  └────────┬────────┘  └────────┬────────┘            │
│           │                    │                      │
│           └────────┬───────────┘                      │
│                    ▼                                  │
│  ┌─────────────────────────────────────┐              │
│  │         MemoryRetriever             │              │
│  │                                     │              │
│  │  短期记忆检索:                       │              │
│  │  · 全量返回最近 N 条                 │              │
│  │                                     │              │
│  │  长期记忆检索:                       │              │
│  │  · MemoryQueryTokenizer (Jieba分词) │              │
│  │  · BM25 相似度排序                  │              │
│  │  · 按 relevance score 过滤          │              │
│  └─────────────────────────────────────┘              │
│                    │                                  │
│  ┌─────────────────┴───────────────────┐              │
│  │  ContextCompressor   │  TokenBudget │              │
│  │                      │              │              │
│  │  · Map-Reduce 摘要   │ · 总预算:    │              │
│  │  · 将旧对话压缩为    │   200000     │              │
│  │    结构化摘要        │ · 短期预算:  │              │
│  │  · 保留关键事实和    │   32768      │              │
│  │    决策信息          │ · 压缩阈值   │              │
│  │                      │   动态触发   │              │
│  └──────────────────────┴─────────────┘              │
│                                                       │
│  工作流:                                              │
│  1. 每条消息 → addUserMessage / addAssistantMessage  │
│  2. 存储后 → compressIfNeeded (Token 超阈值触发压缩)  │
│  3. 新查询 → buildContextForQuery → 注入 system prompt│
│  4. 工具结果截断 500 字 → 防快速撑满预算              │
│                                                       │
└───────────────────────────────────────────────────────┘
```

### 6.2 Token 预算管理

```
AgentBudget (ReAct 层)
├── Token 预算 (默认 300000)
│   └── 超出 → 警告并强制退出
├── 停滞窗口 (默认 3)
│   └── 连续 N 轮无工具调用 → 判定死循环退出
├── 硬最大轮数 (默认 50)
│   └── 超过最大循环轮数 → 强制退出
└── 退出原因枚举: WITHIN_BUDGET / TOKEN_EXHAUSTED / STAGNATION / MAX_ITERATIONS

MemoryManager 层
├── TokenBudget (上下文窗口 200000)
│   └── 整体上下文超出 → 触发摘要压缩
├── ConversationMemory (短期预算 32768)
│   └── 短期记忆超出 → 触发 ContextCompressor
└── 压缩策略: Map-Reduce
    ├── Map: 分段提取关键信息
    └── Reduce: 合并为结构化摘要
```

---

## 七、安全设计

### 7.1 纵深防御五层体系

```
┌────────────────────────────────────────────────────────────┐
│                    安全防护纵深体系                          │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 0: System Prompt 软约束                        │ │
│  │                                                      │ │
│  │  · 系统提示词提前告知安全规则                          │ │
│  │  · 引导 LLM 避开破坏性操作                            │ │
│  │  · 提示工具选择优先级                                 │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                 │
│                          ▼                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 1: PathGuard (路径围栏)                         │ │
│  │                                                      │ │
│  │  所有文件类工具调用前强制校验:                         │ │
│  │  · 绝对路径逃逸检测 (/etc/passwd)                     │ │
│  │  · .. 目录穿越检测 (../../etc/passwd)                 │ │
│  │  · 符号链接逃逸检测 (项目内软链指向外部)               │ │
│  │  · 不存在路径也能校验 (向上找最近存在祖先 + realPath)  │ │
│  │  · 返回已规范化的安全绝对路径                          │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                 │
│                          ▼                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 2: CommandGuard (命令快速拒绝)                  │ │
│  │                                                      │ │
│  │  HITL 之前拦截，正则黑名单 fast-fail:                  │ │
│  │                                                      │ │
│  │  拦截规则 (9 条):                                     │ │
│  │  * sudo 提权                                        │ │
│  │  * rm -rf / ~ $HOME                                 │ │
│  │  * mkfs 格式化磁盘                                  │ │
│  │  * dd of=/dev/ 写入裸设备                            │ │
│  │  * fork bomb (:(){ :|:& };:)                        │ │
│  │  * curl/wget | sh/bash 管道执行远端脚本              │ │
│  │  * find / 或 find ~ 扫描文件系统                     │ │
│  │  * chmod -R 777 /                                   │ │
│  │  * shutdown / reboot / halt / poweroff              │ │
│  │                                                      │ │
│  │  放行: curl/git 等网络命令（不拦截）                   │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                 │
│                          ▼                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 3: NetworkPolicy (网络安全)                     │ │
│  │                                                      │ │
│  │  · scheme 白名单: 仅 http / https                     │ │
│  │  · 主机黑名单: localhost / 0.0.0.0 / loopback       │ │
│  │                link-local / site-local                │ │
│  │  · 响应体上限: 5MB (流式截断，防 OOM)                │ │
│  │  · 整体超时: 30 秒 (OkHttp callTimeout)              │ │
│  │  · 速率限制: 每 60 秒最多 30 次请求                   │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                 │
│                          ▼                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 4: HITL (人工审批)                             │ │
│  │                                                      │ │
│  │  HitlToolRegistry 覆写 executeTool:                  │ │
│  │  · ApprovalPolicy.requiresApproval(toolName)         │ │
│  │    → 内置工具: write_file / execute_command           │ │
│  │              / create_project                        │ │
│  │    → MCP 工具: 默认全部需要审批 + 审计                │ │
│  │  · 审批结果: 批准 / 拒绝 / 跳过 / 修改参数后批准     │ │
│  │  · 用户可修改工具参数 (effectiveArguments)           │ │
│  │  · 审批超时 60 秒，超时自动拒绝                       │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                 │
│                          ▼                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Layer 5: AuditLog (操作审计)                         │ │
│  │                                                      │ │
│  │  · JSONL 格式，按天分文件 audit-YYYY-MM-DD.jsonl     │ │
│  │  · 默认目录: ~/.YuCLI/audit/                        │ │
│  │  · 记录字段:                                          │ │
│  │    - timestamp: ISO 时间戳                            │ │
│  │    - tool: 工具名称                                  │ │
│  │    - args: 参数 (截断 1000 字符)                      │ │
│  │    - outcome: allow / deny / error                    │ │
│  │    - reason: 拒绝原因                                 │ │
│  │    - approver: hitl / policy / none / mention         │ │
│  │    - durationMs: 执行耗时                             │ │
│  │                                                      │ │
│  │  隐私脱敏:                                            │ │
│  │  · Bearer *** (替换 Bearer token)                    │ │
│  │  · "token|key|password|secret": "***"                │ │
│  │                                                      │ │
│  │  设计意图:                                            │ │
│  │  · Agent 的实际副作用变成可回放的事实流               │ │
│  │  · 行为评估、差错复盘、监控告警的统一数据源          │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 7.2 安全特性汇总

| 特性 | 实现类 | 说明 |
|------|--------|------|
| 路径穿越防护 | `PathGuard.resolveSafe()` | 符号链接感知，不存在路径也能校验 |
| 命令注入防护 | `CommandGuard.check()` | 正则黑名单 9 条规则 |
| SSRF 防护 | `NetworkPolicy` | scheme/host 白名单+黑名单 + 限流 |
| 写文件上限 | `write_file` 5MB | 防止磁盘灌满与误覆盖 |
| 命令输出截断 | `execute_command` 8000 字符 | 防止 LLM 上下文污染 |
| HITL 审批 | `HitlToolRegistry` + `TerminalHitlHandler` | 危险操作人工确认，支持修改参数 |
| 操作审计 | `AuditLog` (JSONL) | 按天分文件，全链可追溯 |
| 隐私脱敏 | `AuditLog.sanitize()` | Bearer token、key/password 等自动替换 |
| 取消机制 | `CancellationContext` (ThreadLocal) | Ctrl+C 安全取消，超时自动取消 |
| 审计故障隔离 | 写入失败只 stderr 警告 | 不影响主流程 |

---

## 八、Skill 系统（规划中）

根据 ROADMAP.md，Skill 系统规划在第 15 期实现，目前尚未交付。当前项目通过以下机制提供类似能力：

- **系统提示词注入**（`SYSTEM_PROMPT` 常量）— 提供行为约束和工具优先级
- **Planner 模板**（`EXECUTION_PROMPT`）— 提供任务执行专家角色
- **SubAgent 角色**（`AgentRole` 枚举）— 提供规划者/执行者/检查者角色分工

未来 Skill 系统预期提供：用户可自定义的技能模块、动态加载、领域专用提示词模板。

---

## 九、Agent 范式架构总结

### 9.1 三种 Agent 模式对比

```
┌────────────────┬──────────────────┬──────────────────────┬──────────────────────┐
│     特性       │  ReAct Agent     │  PlanExecuteAgent    │  AgentOrchestrator   │
│               │  (单 Agent)      │  (规划+执行)          │  (Multi-Agent)       │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ 模式           │ Think→Act→Obs   │ 规划→执行→汇总        │ 规划→并行执行→检查   │
│               │ 循环往复         │                      │                      │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ Agent 数量     │ 1                │ 1 (不同阶段复用)      │ 4 (1 Planner +      │
│               │                  │                      │    2 Workers +       │
│               │                  │                      │    1 Reviewer)       │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ 任务分解       │ 无               │ LLM 规划→Task DAG    │ Planner 拆解→       │
│               │                  │                      │ ExecutionStep DAG    │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ 并行执行       │ 同轮多个工具调用  │ 批次内 Task 并行     │ 同依赖批次 Step 并行 │
│               │ 并行 (最多4个)    │                      │ (Worker 池化)       │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ 质量保证       │ LLM 自身判断     │ 计划审查 (HITL可选)  │ Reviewer 逐步骤检查  │
│               │                  │                      │ 失败重试 (最多2次)   │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ 适用场景       │ 简单对话/编辑    │ 多步编程任务          │ 复杂跨模块重构       │
│               │ 单文件操作       │ 需要先规划再执行      │ 需要多角色协作       │
├────────────────┼──────────────────┼──────────────────────┼──────────────────────┤
│ CLI 入口       │ 默认模式         │ /plan 命令            │ /multi 命令          │
└────────────────┴──────────────────┴──────────────────────┴──────────────────────┘
```

### 9.2 核心设计原则

1. **Immutable 数据流**：所有 record 类型（`ExecutionStep`, `ApprovalRequest`, `AuditEntry` 等）都用 `withXxx()` 返回新实例，遵循不可变原则
2. **优雅降级**：MCP server 启动失败不阻塞主流程；审计写入失败只 stderr 警告
3. **流式渲染隔离**：多 Worker 并行时各用独立 `ByteArrayOutputStream`，批次结束按序 flush
4. **符号链接感知**：`PathGuard` 对不存在路径通过向上找最近存在祖先 + `toRealPath()` 做越界检测
5. **配置兼容性**：MCP 配置文件直接兼容 Claude Code 的 `claude_desktop_config.json` 格式
6. **防御性预算**：Agent 层 Token 预算 + Memory 层 Token 预算 + 停滞检测 + 硬最大轮数，四道防线防止失控
7. **审计故障保护**：审计日志写入失败不影响主流程，避免安全机制成为单点故障

---

## 十、数据流全景图

```
                           ┌──────────────────┐
                           │    ~/YuCLI/     │
                           │                  │
                           │  memory/         │
                           │  └ long_term_    │
                           │    memory.json   │
                           │                  │
                           │  rag/            │
                           │  └ codebase.db   │
                           │                  │
                           │  audit/          │
                           │  └ audit-YYYY-   │
                           │    MM-DD.jsonl   │
                           │                  │
                           │  logs/           │
                           │                  │
                           │  mcp.json        │
                           └──────────────────┘
                                  ↕ (读写)
┌─────────────────────────────────────────────────────────────────────────┐
│                            YuCLI Agent                                 │
│                                                                         │
│  用户输入 ──→ [Agent模式选择] ──→ [Memory注入] ──→ [LLM推理]            │
│                                                 │                       │
│                                            返回文本或工具调用            │
│                                                 │                       │
│                              ┌──────────────────┼──────────────┐       │
│                              ▼                  ▼              ▼       │
│                         安全策略层          HITL审批        MCP转发     │
│                         (PathGuard         (Terminal       (外部        │
│                         CommandGuard       交互确认)       Server)     │
│                         NetworkPolicy)                                  │
│                              │                  │              │       │
│                              └──────────────────┼──────────────┘       │
│                                                 ▼                       │
│                                           工具实际执行                   │
│                                                 │                       │
│                                     ┌──────────┼──────────┐           │
│                                     ▼          ▼          ▼           │
│                                 AuditLog   Memory     LLM结果          │
│                                 (审计)     (记忆)     (回写)           │
│                                                 │                       │
│                                            继续循环                      │
│                                                 │                       │
│                                            返回用户                      │
└─────────────────────────────────────────────────────────────────────────┘
```
