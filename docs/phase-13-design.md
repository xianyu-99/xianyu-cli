# 第13期设计：Chrome DevTools MCP

## 目标
让Agent能操控浏览器，处理需要JS渲染或UI交互的页面。

## 技术方案
手写 WebSocket + CDP（零额外依赖，复用Jackson + OkHttp）

## 模块结构

```
src/main/java/com/paicli/browser/
├── CdpWebSocketClient.java      # WebSocket连接 + JSON-RPC请求/响应配对
├── ChromeLauncher.java          # 启动/停止Chrome进程
├── ChromeDiscovery.java         # HTTP /json/list /json/version 查询
├── CdpSession.java              # 单页面会话封装
├── BrowserToolProvider.java     # 工具注册与实现
└── BrowserMcpServer.java        # 可选：作为独立stdio MCP Server
```

## 工具设计（6个核心工具）

| 工具名 | 参数 | 功能 |
|--------|------|------|
| browser_navigate | url (string, required), wait_for_load (boolean, default true) | 导航到URL，等待加载完成 |
| browser_screenshot | selector (string, opt), full_page (boolean, default false) | 截图，支持元素级和全页 |
| browser_click | selector (string, required) | CSS选择器点击元素 |
| browser_type | selector (string, required), text (string, required), submit (boolean, default false) | 输入文本，可选提交 |
| browser_evaluate | script (string, required) | 执行JavaScript，返回结果 |
| browser_get_dom | selector (string, opt), max_length (int, default 8000) | 获取DOM文本内容 |

## 核心流程

1. **首次使用浏览器工具时**：
   - ChromeLauncher启动Chrome（--remote-debugging-port=9222 --headless）
   - ChromeDiscovery查询WebSocket URL
   - CdpWebSocketClient建立WebSocket连接
   - CdpSession封装页面会话

2. **工具执行**：
   - 通过CdpSession发送CDP命令
   - 等待响应或事件
   - 格式化结果返回Agent

3. **进程管理**：
   - JVM退出hook清理Chrome进程
   - 支持显式关闭browser_close工具

## 与现有系统集成

- **ToolRegistry**：BrowserToolProvider将6个工具注册为内置工具（非MCP工具）
- **Agent系统提示词**：告知Agent浏览器工具的使用场景（JS渲染、交互、登录态页面）
- **HITL**：browser_navigate/browser_click/browser_type等操作走审批（中危）
- **AuditLog**：浏览器操作记录审计
- **与web_fetch分工**：静态页面用web_fetch，JS渲染/交互用浏览器工具

## 实现步骤

1. CdpWebSocketClient（JSON-RPC配对，事件监听）
2. ChromeLauncher + ChromeDiscovery
3. CdpSession（封装常用CDP命令）
4. BrowserToolProvider（6个工具实现）
5. ToolRegistry注册 + Agent提示词更新
6. HITL + AuditLog集成
7. 测试
