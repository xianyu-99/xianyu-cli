# 第 13 期技术调研报告：Chrome DevTools MCP

> 调研日期：2026-05-22
> 调研目标：为 PaiCLI 第 13 期"Chrome DevTools MCP"选择最优技术方案
> 项目背景：Java 17 项目，已有手写 MCP 框架（stdio + Streamable HTTP），依赖 Jackson + OkHttp

---

## 1. CDP 协议基础

### 1.1 什么是 Chrome DevTools Protocol (CDP)

CDP 是 Chrome/Chromium 浏览器暴露的一套远程调试协议，基于 **WebSocket + JSON-RPC 2.0**。它允许外部程序通过发送 JSON 消息来控制浏览器的几乎所有行为——导航页面、执行 JavaScript、截取屏幕、操作 DOM、拦截网络请求等。

Chrome 启动时加上 `--remote-debugging-port=9222` 参数后，会在该端口暴露 HTTP 端点用于发现，以及 WebSocket 端点用于实际通信。

### 1.2 核心 Domain

| Domain | 用途 | 本期相关度 |
|--------|------|-----------|
| **Page** | 页面导航、加载事件、截图 | 高 |
| **Runtime** | JavaScript 执行、对象引用 | 高 |
| **DOM** | DOM 树查询、节点选择器 | 高 |
| **Network** | 网络请求拦截、监控 | 中 |
| **Target** | 页面/标签管理、会话创建 | 高 |
| **Browser** | 浏览器级操作、权限管理 | 中 |
| **Input** | 鼠标/键盘事件分发 | 高 |
| **Emulation** | 设备模拟、视口设置 | 低 |
| **Fetch** | 请求拦截与修改 | 低 |

### 1.3 常用命令（本期需要覆盖）

```json
// 导航到 URL
{"id": 1, "method": "Page.navigate", "params": {"url": "https://example.com"}}

// 等待页面加载完成（监听 Page.loadEventFired 事件）
{"id": 2, "method": "Page.captureScreenshot", "params": {"format": "png"}}

// 执行 JavaScript
{"id": 3, "method": "Runtime.evaluate", "params": {"expression": "document.title"}}

// 获取完整 DOM 树
{"id": 4, "method": "DOM.getDocument", "params": {}}

// CSS 选择器查询
{"id": 5, "method": "DOM.querySelector", "params": {"nodeId": 1, "selector": "#content"}}

// 分发鼠标点击事件
{"id": 6, "method": "Input.dispatchMouseEvent", "params": {"type": "mousePressed", "x": 100, "y": 200, "button": "left", "clickCount": 1}}
{"id": 7, "method": "Input.dispatchMouseEvent", "params": {"type": "mouseReleased", "x": 100, "y": 200, "button": "left", "clickCount": 1}}

// 分发键盘事件
{"id": 8, "method": "Input.dispatchKeyEvent", "params": {"type": "keyDown", "key": "Enter"}}
```

### 1.4 通信方式

**HTTP 发现端点**（用于获取 WebSocket URL）：

- `GET http://localhost:9222/json/version` — 返回浏览器版本和 `webSocketDebuggerUrl`
- `GET http://localhost:9222/json/list` — 返回所有可用页面列表（含 `webSocketDebuggerUrl`）
- `GET http://localhost:9222/json/new?url=about:blank` — 创建新页面

**WebSocket 通信**（实际命令通道）：

- 连接 `ws://localhost:9222/devtools/page/{targetId}` 进行单页面通信
- 或连接 `ws://localhost:9222/devtools/browser` 进行浏览器级通信（可创建新页面）
- 消息格式：JSON-RPC 2.0，每条消息必须有 `id`（请求/响应配对）
- 事件通知：`method` 字段存在但无 `id`，如 `Page.loadEventFired`

### 1.5 Chrome 启动参数

```bash
# 基础远程调试
chrome --remote-debugging-port=9222

# 无头模式（服务器环境）
chrome --remote-debugging-port=9222 --headless --no-sandbox

# 常用附加参数
--disable-gpu              # 禁用 GPU 加速（服务器环境推荐）
--disable-dev-shm-usage    # 避免 /dev/shm 空间不足
--window-size=1920,1080    # 设置视口大小
--user-data-dir=/path      # 指定用户数据目录（复用登录态，第 14 期）
```

---

## 2. 连接到 Chrome 的方式

### 2.1 方式一：启动新 Chrome 实例

通过 `ProcessBuilder` 启动 Chrome/Chromium 进程，指定 `--remote-debugging-port`。

```java
ProcessBuilder pb = new ProcessBuilder(
    "chrome",
    "--remote-debugging-port=9222",
    "--headless",
    "--no-sandbox",
    "about:blank"
);
Process process = pb.start();
// 等待 Chrome 就绪（轮询 http://localhost:9222/json/version）
```

**优点**：完全控制浏览器生命周期，适合自动化场景
**缺点**：需要本地安装 Chrome/Chromium；启动有延迟（1-3 秒）；需要管理进程生命周期

### 2.2 方式二：连接已有实例

用户已启动 Chrome（如 `chrome --remote-debugging-port=9222`），PaiCLI 通过 HTTP 发现端点获取 WebSocket URL 后连接。

```java
// 1. 获取可用页面列表
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("http://localhost:9222/json/list")
    .build();
Response response = client.newCall(request).execute();
JsonNode pages = mapper.readTree(response.body().string());

// 2. 提取第一个页面的 webSocketDebuggerUrl
String wsUrl = pages.get(0).get("webSocketDebuggerUrl").asText();
// ws://localhost:9222/devtools/page/ABC123...
```

**优点**：复用用户已有浏览器（含登录态），启动零延迟
**缺点**：依赖用户预先配置；端口冲突风险；多客户端竞争（Chrome 63+ 支持多客户端）

### 2.3 方式三：Puppeteer/Playwright 的 CDP 封装

Puppeteer（Node.js）和 Playwright（多语言）都是高层封装库，它们内部做的事情：

1. 自动下载/查找 Chrome 可执行文件
2. 用 `ProcessBuilder`（或平台等价物）启动 Chrome 并指定 `--remote-debugging-port=0`（自动分配端口）
3. 通过 HTTP 发现端点获取实际端口和 WebSocket URL
4. 建立 WebSocket 连接，发送 CDP 命令
5. 提供高层 API（如 `page.goto()`、`page.click()`）

**Puppeteer 的核心封装逻辑**（简化）：

```javascript
// 1. 启动 Chrome 并获取端口
const { port } = await launchChrome({ args: ['--remote-debugging-port=0'] });
// 2. 获取 WebSocket URL
const response = await fetch(`http://localhost:${port}/json/version`);
const { webSocketDebuggerUrl } = await response.json();
// 3. 连接 WebSocket
const ws = new WebSocket(webSocketDebuggerUrl);
// 4. 发送 CDP 命令
ws.send(JSON.stringify({ id: 1, method: 'Target.createTarget', params: { url: 'about:blank' } }));
```

---

## 3. 现有开源 Browser MCP Server 调研

### 3.1 @modelcontextprotocol/server-puppeteer（官方，已归档）

**状态**：已从主仓库归档到 `modelcontextprotocol/servers-archived`，但仍是官方参考实现。

**架构模式**：stdio MCP Server（Node.js/TypeScript）+ Puppeteer 库 + CDP

**工具设计**（7 个核心工具）：

| 工具名 | 参数 | 功能 |
|--------|------|------|
| `puppeteer_navigate` | `url` (string, required), `launchOptions` (object, opt), `allowDangerous` (boolean, opt) | 导航到 URL，支持重启浏览器 |
| `puppeteer_screenshot` | `name` (string, req), `selector` (string, opt), `width` (number, default 800), `height` (number, default 600), `encoded` (boolean, opt) | 截图，支持元素级截图 |
| `puppeteer_click` | `selector` (string) | CSS 选择器点击 |
| `puppeteer_hover` | `selector` (string) | CSS 选择器悬停 |
| `puppeteer_fill` | `selector` (string), `value` (string) | 填充表单 |
| `puppeteer_select` | `selector` (string), `value` (string) | 下拉选择 |
| `puppeteer_evaluate` | `script` (string) | 执行 JS |

**资源暴露**：
- `console://logs` — 浏览器控制台输出
- `screenshot://<name>` — 已截图的 PNG 图像

**架构特点**：
- 单文件实现（`index.ts`），约 300 行
- 维护一个全局 Puppeteer `browser` 实例，跨工具调用复用
- `puppeteer_navigate` 首次调用时懒启动浏览器
- 通过 `launchOptions` 参数支持动态重启
- stdio transport，newline-delimited JSON-RPC

### 3.2 microsoft/playwright-mcp（官方，活跃维护）

**状态**：Microsoft 官方维护，2025 年发布，当前最活跃的 browser MCP server。

**架构模式**：stdio MCP Server（TypeScript）+ Playwright 库

**工具设计**（20+ 工具，按 capability 分组）：

**核心自动化工具**：

| 工具名 | 参数 | 功能 |
|--------|------|------|
| `browser_navigate` | `url` (string) | 导航 |
| `browser_click` | `element` (string, opt), `target` (string), `doubleClick` (boolean, opt), `button` (string, opt) | 点击 |
| `browser_type` | `target` (string), `text` (string), `submit` (boolean, opt), `slowly` (boolean, opt) | 输入文本 |
| `browser_take_screenshot` | `element` (string, opt), `target` (string, opt), `type` (string), `filename` (string, opt), `fullPage` (boolean, opt) | 截图 |
| `browser_snapshot` | `target` (string, opt), `filename` (string, opt), `depth` (number, opt), `boxes` (boolean, opt) | 可访问性快照 |
| `browser_evaluate` | `function` (string), `target` (string, opt) | 执行 JS |
| `browser_hover` | `target` (string) | 悬停 |
| `browser_drag` | `startTarget` (string), `endTarget` (string) | 拖拽 |
| `browser_press_key` | `key` (string) | 按键 |
| `browser_wait_for` | `time` (number, opt), `text` (string, opt), `textGone` (string, opt) | 等待 |
| `browser_tabs` | `action` (string), `index` (number, opt), `url` (string, opt) | Tab 管理 |
| `browser_close` | 无 | 关闭页面 |

**Capability 可选工具**（通过 `--caps` 参数启用）：
- `vision`：坐标级鼠标操作（`browser_mouse_click_xy` 等）
- `pdf`：`browser_pdf_save`
- `network`：路由拦截（`browser_route` / `browser_unroute`）
- `storage`：Cookie/LocalStorage 操作
- `devtools`：高亮、追踪、视频录制

**架构特点**：
- 使用 Playwright 的**可访问性树（accessibility tree）**而非像素坐标定位元素
- LLM 友好：不需要 vision model，纯结构化数据交互
- 确定性工具应用：避免截图+坐标方式的歧义
- 支持 `--headless` / `--browser` / `--viewport-size` / `--user-data-dir` 等启动参数
- 支持 CDP endpoint 连接（`--cdp-endpoint`）

### 3.3 其他社区实现

| 项目 | 技术栈 | 特点 |
|------|--------|------|
| `qckfx-browser-ai` | TypeScript + Playwright | 18+ 工具，支持 AI 辅助元素识别 |
| `claude_browser_mcp_server` | Python + Selenium | Python 实现，Selenium 驱动 |
| `browser-use-mcp-server` | TypeScript | 专注于 browser-use 模式 |

### 3.4 架构模式总结

所有现有 browser MCP server 都采用**"包装高层浏览器库"**的模式：

```
MCP Client (PaiCLI) --stdio/HTTP--> MCP Server --Puppeteer/Playwright--> CDP --WebSocket--> Chrome
```

**没有**任何现有实现采用"直接 WebSocket + 手写 CDP"的方式。原因：
1. CDP 命令繁多且版本变化快，手写维护成本高
2. 高层库（Puppeteer/Playwright）已处理进程管理、等待逻辑、错误恢复
3. 元素定位（选择器引擎、可访问性树）需要大量封装代码

---

## 4. Java 生态中的 CDP 客户端选项

### 4.1 选项 A：jvppeteer（Java 版 Puppeteer）

**项目**：`io.github.fanyong920:jvppeteer:3.6.4`

**本质**：Puppeteer 的 Java 移植版，底层通过 WebSocket + CDP 控制 Chrome。

**核心能力**：
- 启动/关闭 Chrome 进程
- 页面导航（`page.goTo()`）
- 截图（`page.screenshot()`）
- PDF 生成（`page.pdf()`）
- 元素操作（点击、输入、选择）
- JavaScript 执行（`page.evaluate()`）
- 网络拦截
- Cookie 管理
- 多 Tab 支持

**Maven 依赖**：

```xml
<dependency>
    <groupId>io.github.fanyong920</groupId>
    <artifactId>jvppeteer</artifactId>
    <version>3.6.4</version>
</dependency>
```

**内部依赖**：Jackson 2.18.0、Java-WebSocket 1.5.7、SLF4J 2.0.16

**优点**：
- API 与 Puppeteer Node.js 基本一致，文档丰富
- 活跃维护（最新 3.6.4，2026-05-20 发布）
- 自动下载 Chrome（`BrowserFetcher`）
- 同时支持 Chrome（CDP）和 Firefox（WebDriver-bidi）
- Apache-2.0 许可证

**缺点**：
- 引入额外依赖（Jackson 2.18 可能与 PaiCLI 的 Jackson 2.16 冲突，需验证）
- 包体积大（jvppeteer 本身 + 内部依赖）
- 学习曲线（需理解 Puppeteer 的 Page/Browser 模型）
- 对 CDP 底层细节封装较深，如需精细控制（如第 14 期复用已有 Chrome 实例）可能受限

### 4.2 选项 B：Selenium 4 + CDP

**项目**：`org.seleniumhq.selenium:selenium-java` + `selenium-devtools-v{version}`

**本质**：Selenium WebDriver 的 CDP 扩展，通过 `HasCdp` 接口发送原始 CDP 命令。

**使用方式**：

```java
ChromeDriver driver = new ChromeDriver();
// 方式 1：原始 CDP 命令
Map<String, Object> result = ((HasCdp) driver).executeCdpCommand(
    "Page.captureScreenshot",
    Map.of("format", "png")
);
String base64 = (String) result.get("data");

// 方式 2：类型安全封装（需版本匹配）
DevTools devTools = driver.getDevTools();
devTools.createSession();
devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
```

**优点**：
- Selenium 生态成熟，社区支持好
- `executeCdpCommand` 可发送任意 CDP 命令，灵活性高
- 类型安全封装（`DevTools.send()`）提供编译期检查

**缺点**：
- 需要 ChromeDriver 作为中间层（额外进程）
- 版本绑定严格：Chrome 版本必须与 `selenium-devtools-v{version}` 匹配（Selenium 只支持最近 3 个 Chrome 版本）
- 包体积极大（Selenium 全家桶）
- 对"复用已有 Chrome 实例"（第 14 期目标）支持不佳——Selenium 通常自己启动 Chrome
- 不是为 MCP server 场景设计的，需要大量适配代码

### 4.3 选项 C：手写 WebSocket + Jackson（直接 CDP）

**本质**：复用 PaiCLI 已有的 Jackson + OkHttp，手写 WebSocket 客户端直接与 Chrome CDP 通信。

**需要实现的组件**：

```
com.paicli.mcp.browser/
├── CdpWebSocketClient.java      # WebSocket 连接 + JSON-RPC 请求/响应配对
├── ChromeLauncher.java          # ProcessBuilder 启动 Chrome
├── ChromeDiscovery.java         # HTTP /json/list /json/version 查询
├── CdpSession.java              # 单页面会话（发送命令、监听事件）
├── CdpDomains.java              # 各 Domain 的便捷方法封装
│   ├── PageDomain.java          # Page.navigate, Page.captureScreenshot
│   ├── RuntimeDomain.java       # Runtime.evaluate
│   ├── DomDomain.java           # DOM.getDocument, DOM.querySelector
│   └── InputDomain.java         # Input.dispatchMouseEvent, Input.dispatchKeyEvent
└── BrowserMcpServer.java        # MCP Server 主类（stdio transport + 工具注册）
```

**核心代码示意**：

```java
public class CdpWebSocketClient implements AutoCloseable {
    private final WebSocket ws;
    private final AtomicLong id = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    public JsonNode send(String method, JsonNode params) {
        long reqId = id.getAndIncrement();
        ObjectNode request = mapper.createObjectNode();
        request.put("id", reqId);
        request.put("method", method);
        request.set("params", params);
        // 发送 WebSocket 消息...
        // 等待响应配对...
    }
}
```

**优点**：
- 零额外依赖（复用 Jackson + OkHttp，Java 17 内置 `java.net.http.WebSocket`）
- 完全掌控 CDP 通信细节，第 14 期"复用已有 Chrome 实例"天然支持
- 与 PaiCLI 现有 MCP 框架风格一致（手写 JSON-RPC）
- 包体积零增加
- 教育价值高（契合 PaiCLI"从零手写"的定位）

**缺点**：
- 开发工作量大（需实现 WebSocket 客户端、请求响应配对、事件监听、Chrome 进程管理）
- 需自行处理 CDP 版本兼容性（Chrome 升级可能改变命令参数）
- 元素定位逻辑需自行实现（无 Puppeteer 的选择器引擎）
- 等待/重试逻辑（如等待页面加载完成）需手写

### 4.4 选项对比表

| 维度 | jvppeteer | Selenium 4 + CDP | 手写 WebSocket + CDP |
|------|-----------|-------------------|----------------------|
| **额外依赖** | 1 个（jvppeteer，含 Jackson/WebSocket/SLF4J） | 多个（selenium-java + devtools） | 0（复用现有） |
| **包体积影响** | 中等 | 大 | 无 |
| **开发工作量** | 低（API 现成） | 中（需适配层） | 高（需手写底层） |
| **Chrome 版本兼容性** | 好（jvppeteer 跟随 Chrome 更新） | 差（需匹配 devtools 版本） | 需自行处理 |
| **复用已有实例** | 支持（`connect` 方法） | 弱 | 天然支持 |
| **进程管理** | 内置 | ChromeDriver 管理 | 需手写 |
| **元素定位** | 完整（Puppeteer API） | 通过 WebDriver | 需手写（或简化） |
| **教育价值** | 低（封装太深） | 低 | 高（契合项目定位） |
| **维护成本** | 低（跟随上游） | 中（版本绑定） | 高（自维护） |
| **许可证** | Apache-2.0 | Apache-2.0 | N/A |

---

## 5. 推荐方案

### 5.1 推荐：选项 C（手写 WebSocket + CDP）+ 适度封装

**核心理由**：

1. **契合 PaiCLI 项目定位**：PaiCLI 是一个"从零手写"的教育型 Agent CLI 产品，第 10-11 期已经手写了完整的 MCP 协议栈（JSON-RPC、stdio transport、Streamable HTTP）。直接手写 CDP 与这一理念完全一致，能让用户理解浏览器自动化的底层原理。

2. **零额外依赖**：PaiCLI 已有 Jackson 2.16 + OkHttp 4.12。Java 11+ 内置 `java.net.http.WebSocket`（PaiCLI 用 Java 17），不需要引入任何新库。

3. **第 14 期天然衔接**：第 14 期目标是"复用用户已登录的 Chrome 实例"，手写 CDP 方案只需改 `ChromeDiscovery` 的连接逻辑，无需改动封装层。

4. **可控的复杂度**：本期只需要 5-6 个基础工具（导航、截图、执行 JS、点击、获取 DOM），对应的 CDP 命令非常稳定（`Page.navigate`、`Page.captureScreenshot`、`Runtime.evaluate`、`Input.dispatchMouseEvent`、`DOM.getDocument`），不需要覆盖全部 CDP。

5. **避免依赖风险**：jvppeteer 虽活跃但社区规模有限；Selenium 版本绑定问题在 CLI 工具场景下是硬伤。

### 5.2 具体实现思路

**架构设计**：

```
PaiCLI (MCP Client)
    |
    | stdio / Streamable HTTP (第 10 期已有)
    v
Browser MCP Server (stdio 子进程)
    |
    |-- CdpBrowserTool.java (工具注册到 ToolRegistry)
    |-- ChromeProcessManager.java (Chrome 进程生命周期)
    |-- CdpClient.java (WebSocket + JSON-RPC)
    |-- CdpSession.java (单页面会话)
    |-- domains/
    |   |-- PageDomain.java
    |   |-- RuntimeDomain.java
    |   |-- DomDomain.java
    |   |-- InputDomain.java
    |   `-- TargetDomain.java
```

**两种部署模式**：

**模式 A：作为外部 stdio MCP Server（推荐）**

与现有 MCP 框架完全兼容，在 `mcp.json` 中配置：

```json
{
  "mcpServers": {
    "browser": {
      "command": "java",
      "args": ["-jar", "paicli-browser-mcp.jar"]
    }
  }
}
```

Browser MCP Server 是一个独立的 Java 进程，通过 stdio 与 PaiCLI 通信。这种方式：
- 复用第 10 期全部基础设施（`McpServerManager`、`StdioTransport`、`JsonRpcClient`）
- 浏览器进程隔离在 MCP Server 子进程中，崩溃不影响 PaiCLI 主进程
- 符合 MCP 生态标准，可被其他 MCP Client 复用

**模式 B：作为 PaiCLI 内置模块（备选）**

直接在 PaiCLI 内部启动 Chrome 进程，工具注册到 `ToolRegistry`。这种方式：
- 无需额外进程，启动更快
- 但浏览器崩溃会影响 PaiCLI 主进程
- 与 MCP 生态解耦

**推荐模式 A**，与 PaiCLI 现有 MCP 架构保持一致。

**工具设计（对齐官方 Puppeteer MCP）**：

| 工具名 | 参数 | CDP 命令 | 说明 |
|--------|------|----------|------|
| `browser_navigate` | `url` (string, req) | `Page.navigate` + 等待 `Page.loadEventFired` | 导航并等待加载完成 |
| `browser_screenshot` | `name` (string, req), `selector` (string, opt), `fullPage` (boolean, opt, default false) | `Page.captureScreenshot` | 截图保存到临时目录，返回 base64 |
| `browser_click` | `selector` (string, req) | `DOM.querySelector` 获取坐标 + `Input.dispatchMouseEvent` x2 | CSS 选择器点击 |
| `browser_type` | `selector` (string, req), `text` (string, req), `submit` (boolean, opt) | `DOM.querySelector` + `Input.dispatchKeyEvent` 逐字符 | 输入文本 |
| `browser_evaluate` | `script` (string, req) | `Runtime.evaluate` | 执行 JS，返回结果 |
| `browser_get_dom` | `selector` (string, opt) | `DOM.getDocument` + `DOM.querySelector` | 获取 DOM 文本内容 |

**关键实现细节**：

1. **WebSocket 客户端**：复用 PaiCLI 第 10 期的 `JsonRpcClient` 设计模式，但 transport 改为 WebSocket 而非 stdio/HTTP。`CdpWebSocketTransport` 实现 `McpTransport` 接口（或独立设计）。

2. **Chrome 进程管理**：
   - 首次调用 `browser_navigate` 时懒启动 Chrome
   - `ProcessBuilder` 启动，指定 `--remote-debugging-port=0`（自动分配端口）
   - 通过 `http://localhost:{port}/json/version` 获取 WebSocket URL
   - JVM shutdown hook 兜底销毁 Chrome 进程

3. **请求/响应配对**：CDP 是异步的，需要 `id` 字段配对。复用 `ConcurrentHashMap<Long, CompletableFuture<JsonNode>>` 模式。

4. **事件监听**：页面加载完成通过监听 `Page.loadEventFired` 事件实现，而非轮询。

5. **元素定位简化**：本期不实现完整的 CSS 选择器引擎。方案：
   - `browser_click` / `browser_type` 通过 `Runtime.evaluate` 执行 `document.querySelector()` 获取元素坐标，再分发鼠标/键盘事件
   - 或先通过 `DOM.getDocument` + `DOM.querySelector` 获取 `nodeId`，再用 `DOM.getBoxModel` 获取坐标

6. **截图返回**：`Page.captureScreenshot` 返回 base64 字符串，直接作为 MCP tool result 的 text content 返回（或保存为临时文件后返回路径）。

### 5.3 工作量估算

| 组件 | 代码量 | 天数 |
|------|--------|------|
| `CdpWebSocketClient`（WebSocket + JSON-RPC） | ~150 行 | Day 1 |
| `ChromeLauncher` + `ChromeDiscovery` | ~100 行 | Day 1 |
| `CdpSession` + `PageDomain` + `RuntimeDomain` | ~150 行 | Day 2 |
| `DomDomain` + `InputDomain` + 元素定位 | ~150 行 | Day 2 |
| Browser MCP Server 主类（stdio transport + 工具注册） | ~200 行 | Day 3 |
| 测试（MockWebSocket、集成测试） | ~200 行 | Day 4 |
| 联调 + 文档 | — | Day 5 |
| **总计** | **~950 行** | **5 天** |

### 5.4 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Chrome 未安装 | 启动时检测，返回友好错误提示；文档说明安装方式 |
| Windows 路径问题 | `ProcessBuilder` 使用绝对路径或从 `PATH` 查找；测试覆盖 |
| CDP 命令版本变化 | 本期只使用最稳定的命令（Page.navigate、Runtime.evaluate 等），这些命令多年未变 |
| WebSocket 连接不稳定 | 实现重连逻辑；Chrome 崩溃时自动重启 |
| 元素定位不准确 | 通过 `Runtime.evaluate` 回退到 JS 方式获取坐标 |

---

## 6. 与现有 `web_fetch` 的分工

| 场景 | 使用工具 | 原因 |
|------|----------|------|
| 静态页面、无需 JS 渲染 | `web_fetch` | 更快、更轻量、无浏览器开销 |
| SPA（React/Vue/Angular）、需 JS 渲染 | `browser_navigate` + `browser_get_dom` | 需要真实浏览器执行 JS |
| 需要交互（点击、表单填写） | `browser_click` / `browser_type` | 只有浏览器能模拟用户交互 |
| 需要截图验证 | `browser_screenshot` | 浏览器渲染后截图 |
| 需要执行页面内 JS | `browser_evaluate` | 在页面上下文中执行 |

Agent 提示词中需明确告知这一分工，避免 LLM 对所有 URL 都使用浏览器。

---

## 7. 参考资源

- [Chrome DevTools Protocol 官方文档](https://chromedevtools.github.io/devtools-protocol/)
- [Puppeteer MCP Server (Archived)](https://github.com/modelcontextprotocol/servers-archived/tree/main/src/puppeteer)
- [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp)
- [jvppeteer (Java Puppeteer)](https://github.com/fanyong920/jvppeteer)
- [Selenium 4 CDP Documentation](https://www.selenium.dev/documentation/webdriver/bidi/cdp/)
- [PaiCLI 第 10 期 MCP 协议核心](phase-10-mcp-core.md)
- [PaiCLI 第 11 期 MCP 高级能力](phase-11-mcp-advanced.md)
