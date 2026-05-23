# Phase 13 Implementation Plan: Chrome DevTools MCP

> Status: COMPLETED (code delivered, document retroactive)
> Target Version: v13.0.0
> Estimated Effort: 6 days
> Actual Effort: 6 days

---

## 1. Goals and Scope

### 1.1 Primary Goal
Enable the Agent to control a browser for handling pages that require JavaScript rendering or UI interaction.

### 1.2 Functional Boundaries

| In Scope | Out of Scope (Phase 14) |
|----------|------------------------|
| Launch new Chrome instance with --remote-debugging-port | Connect to user's existing Chrome instance |
| Single page/tab operations | Multi-tab / multi-context management |
| Headless mode as default | Reusing existing login sessions |
| 7 core browser tools | Network request interception |
| Lazy browser startup on first tool use | Browser pool / session reuse |
| Manual browser_close for cleanup | Automatic resource cleanup heuristics |

### 1.3 Technical Decision: Hand-Written CDP (Not MCP Server Wrapper)

After evaluating three approaches (jvppeteer, Selenium 4 + CDP, hand-written WebSocket + CDP), we chose **Option C: hand-written WebSocket + CDP** with the following rationale:

1. **Zero additional dependencies**: Reuses existing Jackson 2.16 + Java 17 built-in java.net.http.WebSocket
2. **Aligns with YuCLI educational positioning**: Hand-written protocol stacks are a core project theme (already done for MCP stdio + Streamable HTTP in Phases 10-11)
3. **Natural Phase 14衔接**: Reusing existing Chrome instances only requires changing ChromeDiscovery connection logic
4. **Controllable complexity**: Only 5-6 stable CDP commands needed (Page.navigate, Page.captureScreenshot, Runtime.evaluate, Input.dispatchMouseEvent, DOM.getDocument)
5. **No dependency risks**: Avoids Jackson version conflicts (jvppeteer uses 2.18) and Selenium's strict Chrome version binding

---

## 2. Module Structure

```
src/main/java/com/YuCLI/browser/
├── CdpWebSocketClient.java      # WebSocket connection + JSON-RPC request/response pairing
├── ChromeLauncher.java          # Chrome process lifecycle (start/stop/discovery)
├── ChromeDiscovery.java         # HTTP /json/list /json/version /json/new queries
├── CdpSession.java              # Single-page session wrapper (high-level CDP commands)
└── BrowserToolProvider.java     # Tool implementations + lifecycle management
```

### 2.1 Package Design Rationale

- **New package com.yucli.browser**: Browser automation is a distinct capability domain, separate from web fetching (com.yucli.web) and MCP infrastructure (com.yucli.mcp)
- **No BrowserMcpServer.java**: Unlike the research recommendation, we chose **built-in tools** (registered directly in ToolRegistry) rather than a separate stdio MCP Server. This avoids an extra process and aligns with how other built-in tools (web_search, web_fetch) are structured
- **No domain subpackage**: With only 7 tools, splitting into domains/PageDomain.java etc. would be premature abstraction. CdpSession encapsulates all domain operations

---

## 3. Component Designs

### 3.1 ChromeLauncher

**Responsibility**: Find, launch, and kill Chrome/Chromium processes.

**Key Behaviors**:
- Cross-platform Chrome discovery (Windows: Program Files + LOCALAPPDATA; macOS: /Applications; Linux: /usr/bin + PATH)
- Falls back to Microsoft Edge if Chrome not found
- Supports CHROME_PATH environment variable override
- Headless mode default (--headless=new)
- JVM shutdown hook for automatic cleanup
- Startup polling via /json/version HTTP endpoint (15s timeout, 500ms interval)

**Chrome Launch Arguments**:
```
--remote-debugging-port={port}
--no-first-run
--no-default-browser-check
--headless=new        (if headless)
--no-sandbox          (if headless)
--disable-gpu         (if headless)
--disable-dev-shm-usage (if headless)
--user-data-dir={dir} (if specified)
about:blank
```

### 3.2 ChromeDiscovery

**Responsibility**: HTTP-based discovery of Chrome DevTools endpoints.

**Endpoints**:
- GET /json/version — Browser version info
- GET /json/list — Available pages with WebSocket URLs
- GET /json/new?{url} — Create new page

**Usage Flow**:
1. ChromeLauncher.launch() returns debug port
2. ChromeDiscovery(port) queries /json/list
3. Extract webSocketDebuggerUrl from first available page
4. Pass to CdpWebSocketClient.connect(wsUrl)

### 3.3 CdpWebSocketClient

**Responsibility**: WebSocket connection lifecycle + JSON-RPC 2.0 message handling.

**Key Design Patterns**:
- **Request/Response Pairing**: ConcurrentHashMap<Long, CompletableFuture<JsonNode>> maps request IDs to futures
- **Event Subscription**: ConcurrentHashMap<String, Consumer<JsonNode>> for CDP events (e.g., Page.loadEventFired)
- **Auto Timeout**: 30s default timeout via CompletableFuture.orTimeout()
- **Connection State**: volatile boolean connected for fast checks

**Message Types**:
```json
// Request
{"id": 1, "method": "Page.navigate", "params": {"url": "https://example.com"}}

// Response
{"id": 1, "result": {"frameId": "ABC123", "loaderId": "DEF456"}}

// Error Response
{"id": 1, "error": {"code": -32000, "message": "Cannot navigate to invalid URL"}}

// Event (no id field)
{"method": "Page.loadEventFired", "params": {"timestamp": 1234567890}}
```

### 3.4 CdpSession

**Responsibility**: High-level CDP command wrappers for single-page operations.

**Implemented Operations**:

| Method | CDP Commands | Description |
|--------|-------------|-------------|
| navigate(url, waitForLoad) | Page.navigate + Page.loadEventFired event | Navigate and optionally wait for load |
| captureScreenshot(selector, fullPage) | Page.getLayoutMetrics + Page.captureScreenshot | Screenshot with optional full-page clip |
| getCurrentUrl() | Runtime.evaluate(window.location.href) | Get current page URL |
| getDomText(selector, maxLength) | Runtime.evaluate(document.body.innerText) or document.querySelector | Extract text content |
| getHtml() | Runtime.evaluate(document.documentElement.outerHTML) | Get full HTML |
| click(selector) | Runtime.evaluate(getBoundingClientRect) + Input.dispatchMouseEvent x2 | CSS selector click |
| type(selector, text, submit) | click() + Runtime.evaluate(clear) + Input.dispatchKeyEvent loop | Type text into input |
| evaluate(expression) | Runtime.evaluate with returnByValue=true, awaitPromise=true | Execute JavaScript |

**Element Location Strategy**:
- Uses Runtime.evaluate with document.querySelector() to get element coordinates
- Calculates center point from getBoundingClientRect()
- Dispatches mousePressed + mouseReleased at calculated coordinates
- No full CSS selector engine needed (delegated to browser's native implementation)

### 3.5 BrowserToolProvider

**Responsibility**: Bridge between ToolRegistry and CDP infrastructure.

**Lifecycle Management**:
- Lazy initialization: Chrome starts on first tool call, not at construction
- Connection reuse: Checks session != null && wsClient.isConnected() before creating new connection
- Prefers connecting to existing Chrome instance on port 9222 before launching new one
- close() method for explicit cleanup (called by browser_close tool)

**Error Handling**:
- All tools return structured error messages ("❌ {operation} failed: {message}")
- Null/blank parameter validation before CDP calls
- Graceful degradation if Chrome not installed (friendly error message)

---

## 4. Browser Tool Set (7 Tools)

### 4.1 Tool Registry

| Tool Name | Parameters | Risk Level | Audit |
|-----------|-----------|------------|-------|
| browser_navigate | url (string, required), wait_for_load (boolean, default true) | Medium | Yes |
| browser_screenshot | selector (string, opt), full_page (boolean, default false) | Low | No |
| browser_click | selector (string, required) | Medium | Yes |
| browser_type | selector (string, required), text (string, required), submit (boolean, default false) | Medium | Yes |
| browser_evaluate | script (string, required) | Low | No |
| browser_get_dom | selector (string, opt), max_length (int, default 8000) | Low | No |
| browser_close | none | Low | No |

### 4.2 Tool Descriptions (for LLM)

```
browser_navigate: Open a webpage (supports JS rendering). Parameters: {"url": "https://...", "wait_for_load": true}
browser_screenshot: Capture page screenshot as PNG. Parameters: {"selector": "css-selector", "full_page": false}
browser_click: Click element matching CSS selector. Parameters: {"selector": "css-selector"}
browser_type: Type text into input field. Parameters: {"selector": "css-selector", "text": "content", "submit": false}
browser_evaluate: Execute JavaScript in page context. Parameters: {"script": "document.title"}
browser_get_dom: Get page or element text content. Parameters: {"selector": "css-selector", "max_length": 8000}
browser_close: Close browser and release resources. Parameters: {}
```

### 4.3 web_fetch vs Browser Decision Matrix

Agent system prompt includes this guidance:

| Scenario | Tool to Use | Reason |
|----------|-------------|--------|
| Static page, no JS needed | web_fetch | Faster, lighter, no browser overhead |
| SPA (React/Vue/Angular), JS rendering required | browser_navigate + browser_get_dom | Needs real browser to execute JS |
| Interaction needed (click, form fill) | browser_click / browser_type | Only browser can simulate user interaction |
| Screenshot verification needed | browser_screenshot | Browser-rendered screenshot |
| Execute page JavaScript | browser_evaluate | Runs in page context |
| web_fetch returns empty body (SPA/anti-crawl) | browser_navigate + browser_get_dom | Fallback when web_fetch fails |

---

## 5. Integration with Existing Systems

### 5.1 ToolRegistry Integration

**Changes to ToolRegistry.java**:
- Added private BrowserToolProvider browserToolProvider field
- Added registerBrowserTools() method called in constructor
- Registered all 7 browser tools with proper parameter schemas
- Added browser tools to AUDIT_TOOLS set: "browser_navigate", "browser_click", "browser_type"

**No changes needed for**:
- registerMcpTool() / replaceMcpToolsForServer() — browser tools are built-in, not MCP
- executeTools() parallel batch execution — browser tools participate normally
- Tool definition format (Tool record) — same structure as existing tools

### 5.2 HITL Integration

**Changes to ApprovalPolicy.java**:
- Added "browser_navigate", "browser_click", "browser_type" to DANGEROUS_TOOLS
- Added risk descriptions for browser tools in getRiskDescription()
- Added danger level "🟡 中危" for browser tools in getDangerLevel()

**Behavior**:
- browser_navigate: Requires approval (opens external URLs, triggers network requests)
- browser_click: Requires approval (may trigger navigation, form submission)
- browser_type: Requires approval (modifies form content)
- browser_screenshot, browser_evaluate, browser_get_dom, browser_close: No approval needed (read-only / safe)

**HitlToolRegistry**: No changes needed. Inherits approval behavior through ApprovalPolicy.requiresApproval().

### 5.3 AuditLog Integration

**Changes to ToolRegistry.java**:
- AUDIT_TOOLS expanded to include "browser_navigate", "browser_click", "browser_type"

**Audit entries include**:
- allow: Browser operation executed successfully
- deny: HITL rejected the browser operation
- error: Browser operation threw exception

**No changes needed to AuditLog.java** — existing structured logging handles all cases.

### 5.4 Agent System Prompt Updates

**Files Modified**:
1. Agent.java (buildSystemPrompt())
2. PlanExecuteAgent.java (system prompt for planning mode)
3. SubAgent.java (system prompt for sub-agents)

**Prompt Additions**:
- Browser tools added to numbered tool list (items 9-15)
- Tool selection priority guidance:
  - "web_fetch 拿到空正文（提示 SPA / 防爬墙）→ 换用 browser_navigate + browser_get_dom"
  - "需要点击、输入、截图等交互操作 → browser_navigate 打开页面，再用 browser_click / browser_type / browser_screenshot"
  - "浏览器工具使用完后，调用 browser_close 释放资源"
- Safety policy hard rules remain unchanged (path guards, command guards still apply)

### 5.5 NetworkPolicy Integration

**No direct integration** — browser tools bypass NetworkPolicy because:
- Browser operates through Chrome process, not direct HTTP from Java
- Chrome's own security model (CORS, CSP) applies
- Phase 14 may add URL validation before browser_navigate

**Future consideration**: Add URL scheme validation in browser_navigate to prevent file://, javascript: protocols.

---

## 6. Security Policy

### 6.1 URL Validation (Recommended Enhancement)

Add to browser_navigate before CDP call:
```java
// Reject non-http(s) schemes
if (!url.startsWith("http://") && !url.startsWith("https://")) {
    return "❌ 仅支持 http 和 https 协议";
}
```

### 6.2 Sensitive Page Recognition (Phase 14)

Future enhancement: Pattern matching for sensitive domains:
- Banking sites (*.bank, *.alipay.com)
- Internal systems (intranet domains)
- Admin panels (*/admin*, */manage*)

These should trigger enhanced HITL warnings or automatic refusal.

### 6.3 Browser Isolation

- Each YuCLI session gets its own Chrome instance (or connection)
- --user-data-dir uses temporary directory by default
- No cookie/session sharing between sessions
- Phase 14 will support intentional login state reuse

---

## 7. Implementation Steps (6 Days)

### Day 1: CDP WebSocket Infrastructure
- [ ] Implement CdpWebSocketClient.java
  - WebSocket connection with java.net.http.WebSocket
  - JSON-RPC request/response pairing via ConcurrentHashMap
  - Event listener registration (onEvent/offEvent)
  - Sync and async send methods
  - Connection state management
- [ ] Implement ChromeDiscovery.java
  - HTTP queries to /json/version, /json/list, /json/new
  - WebSocket URL extraction
  - Error handling for unreachable Chrome

### Day 2: Chrome Process Management + Session
- [ ] Implement ChromeLauncher.java
  - Cross-platform Chrome executable discovery
  - ProcessBuilder launch with proper arguments
  - Startup polling (wait for /json/version to respond)
  - JVM shutdown hook for cleanup
  - Kill with graceful fallback to forcible
- [ ] Implement CdpSession.java
  - navigate() with Page.loadEventFired event waiting
  - captureScreenshot() with full-page support
  - evaluate() with returnByValue=true
  - getDomText() with truncation

### Day 3: DOM Interaction + Tool Provider
- [ ] Implement CdpSession continued
  - click() via coordinate calculation + mouse events
  - type() with focus, clear, key-by-key input, optional submit
  - scrollIntoView() helper
- [ ] Implement BrowserToolProvider.java
  - Lazy session initialization
  - All 7 tool methods with parameter validation
  - Error message formatting
  - Screenshot file saving to temp directory

### Day 4: Integration
- [ ] Update ToolRegistry.java
  - Add browserToolProvider field
  - Implement registerBrowserTools()
  - Add browser tools to AUDIT_TOOLS
- [ ] Update ApprovalPolicy.java
  - Add browser tools to DANGEROUS_TOOLS
  - Add risk descriptions and danger levels
- [ ] Update Agent.java system prompt
  - Add browser tools to numbered list
  - Add tool selection priority guidance
- [ ] Update PlanExecuteAgent.java and SubAgent.java prompts

### Day 5: Testing
- [ ] Unit tests for CdpWebSocketClient (mock WebSocket server)
- [ ] Unit tests for ChromeDiscovery (mock HTTP endpoints)
- [ ] Unit tests for CdpSession (mock CDP responses)
- [ ] Integration test: Full flow navigate → getDom → screenshot → close
- [ ] Cross-platform Chrome discovery test

### Day 6: Polish + Documentation
- [ ] Error message review (ensure all user-facing messages are Chinese)
- [ ] Screenshot file path reporting
- [ ] Browser not installed error message
- [ ] Update ROADMAP.md Phase 13 status
- [ ] Write tutorial draft

---

## 8. Acceptance Criteria

### 8.1 Functional Criteria

- [ ] browser_navigate successfully loads https://example.com and returns current URL
- [ ] browser_get_dom returns page text content after JS rendering
- [ ] browser_screenshot saves PNG file to temp directory and returns path
- [ ] browser_click successfully clicks a button on a test page
- [ ] browser_type enters text into an input field and optionally submits
- [ ] browser_evaluate executes document.title and returns result
- [ ] browser_close terminates Chrome process and releases resources
- [ ] Lazy startup: No Chrome process before first browser tool call
- [ ] Connection reuse: Second tool call uses same Chrome instance

### 8.2 Integration Criteria

- [ ] browser_navigate triggers HITL approval prompt
- [ ] browser_click triggers HITL approval prompt
- [ ] browser_type triggers HITL approval prompt
- [ ] browser_screenshot does NOT trigger HITL
- [ ] All browser operations appear in audit log
- [ ] Agent system prompt includes browser tool descriptions
- [ ] Agent correctly chooses web_fetch for static pages vs browser for JS pages

### 8.3 Edge Cases

- [ ] Chrome not installed: Returns friendly error message with installation hint
- [ ] Invalid URL: Returns error before attempting navigation
- [ ] Element not found for click/type: Returns "Element not found" error
- [ ] Page load timeout: Returns timeout error after 30s
- [ ] Chrome crash during operation: Returns connection error
- [ ] Multiple rapid browser tool calls: Connection reused, not multiple Chrome instances

---

## 9. Test Strategy

### 9.1 Unit Tests

| Component | Test Cases |
|-----------|-----------|
| CdpWebSocketClient | Connection success/failure, request/response pairing, event delivery, timeout handling, close cleanup |
| ChromeDiscovery | Version query, page list parsing, WebSocket URL extraction, new page creation, connection error |
| ChromeLauncher | Mock platform detection, argument construction, startup polling logic, kill behavior |
| CdpSession | Mock CDP responses for each command, error propagation, coordinate calculation |
| BrowserToolProvider | Parameter validation, lazy initialization, error message formatting |

### 9.2 Integration Tests

- **Happy Path**: navigate → getDom → screenshot → evaluate → close
- **SPA Test**: Navigate to React/Vue demo site, verify getDom returns rendered content (web_fetch would return empty)
- **Form Interaction**: Navigate to login form, type credentials, click submit
- **Error Recovery**: Kill Chrome mid-session, verify next tool call launches new instance

### 9.3 Manual Tests

- Cross-platform: Windows (Chrome + Edge fallback), macOS, Linux
- Headless vs headed mode
- Various websites: static blog, SPA dashboard, form-heavy site

---

## 10. Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Chrome not installed on target machine | Medium | High | Clear error message; Edge fallback; document prerequisites |
| Windows path issues with spaces | Medium | Medium | Quote paths in ProcessBuilder; test on Windows |
| CDP command version incompatibility | Low | Medium | Use only stable commands; test with multiple Chrome versions |
| WebSocket connection instability | Low | Medium | Implement reconnection; auto-restart Chrome on failure |
| Element coordinate calculation wrong | Medium | Medium | Fallback to JS-based clicking; test on various page layouts |
| Memory leak from Chrome processes | Low | High | JVM shutdown hook; browser_close tool; document cleanup need |
| Screenshot file accumulation | Low | Low | Save to temp dir (OS cleans); document manual cleanup |

---

## 11. Performance Considerations

- **Chrome startup time**: 1-3 seconds cold start. Mitigated by lazy initialization and connection reuse.
- **Screenshot size**: Full-page screenshots of long pages can be large (10MB+). Base64 encoding adds 33% overhead. Consider size limits.
- **DOM text truncation**: Default 8000 chars matches web_fetch default. Consistent with existing behavior.
- **Parallel tool execution**: Browser tools participate in ToolRegistry.executeTools() parallel batch. Single session is synchronized internally.

---

## 12. Future Work (Phase 14+)

### Phase 14: CDP Session Reuse + Login State
- Connect to existing Chrome instance (--remote-debugging-port already set)
- Reuse user login state for authenticated pages
- Multi-tab management (Target.createTarget, Target.attachToTarget)
- Sensitive page recognition and enhanced security

### Phase 15: Skill System
- Package browser + web tools into web-access Skill
- Site-specific heuristics ("when on GitHub, use this pattern")
- Jina Reader fallback integration

### Potential Enhancements
- Browser console log capture (Runtime.consoleAPICalled event)
- Network request monitoring (Network.requestWillBeSent)
- PDF generation (Page.printToPDF)
- Mobile viewport emulation (Emulation.setDeviceMetricsOverride)
- Cookie/LocalStorage access (Storage domain)

---

## 13. References

- Chrome DevTools Protocol Docs: https://chromedevtools.github.io/devtools-protocol/
- Puppeteer MCP Server (Archived): https://github.com/modelcontextprotocol/servers-archived/tree/main/src/puppeteer
- Microsoft Playwright MCP: https://github.com/microsoft/playwright-mcp
- Phase 10 Design: docs/phase-10-mcp-core.md
- Phase 11 Design: docs/phase-11-mcp-advanced.md
- Phase 13 Research: docs/phase-13-research.md

---

## Appendix A: CDP Message Flow Example

```
1. ChromeLauncher.launch()
   -> ProcessBuilder starts chrome --remote-debugging-port=9222
   -> Polls http://localhost:9222/json/version until 200 OK

2. ChromeDiscovery.getWebSocketDebuggerUrl()
   -> GET http://localhost:9222/json/list
   -> Extracts ws://localhost:9222/devtools/page/ABC123

3. CdpWebSocketClient.connect(wsUrl)
   -> java.net.http.WebSocket connection established

4. BrowserToolProvider.navigate("https://example.com")
   -> CdpSession.navigate()
      -> sendSync("Page.enable", null)
      -> sendSync("Runtime.enable", null)
      -> sendSync("DOM.enable", null)
      -> onEvent("Page.loadEventFired", handler)
      -> sendSync("Page.navigate", {"url": "https://example.com"})
      -> wait for loadEventFired (or timeout)
      -> offEvent("Page.loadEventFired")
   -> Returns "✅ 已导航到: https://example.com"

5. BrowserToolProvider.getDom(null, 8000)
   -> CdpSession.getDomText(null, 8000)
      -> evaluate("document.body.innerText")
      -> Truncate to 8000 chars if needed
   -> Returns text content

6. BrowserToolProvider.closeBrowser()
   -> BrowserToolProvider.close()
      -> CdpWebSocketClient.close()
      -> ChromeLauncher.kill()
   -> Returns "✅ 浏览器已关闭"
```
