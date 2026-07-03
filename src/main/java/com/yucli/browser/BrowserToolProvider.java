package com.yucli.browser;

import java.util.Base64;
import java.util.Map;

/**
 * 浏览器工具提供者。
 *
 * 封装浏览器操控工具，管理 Chrome 进程和 CDP 会话生命周期。
 * 提供浏览器导航、截图、点击、输入、执行脚本、读取 DOM、标签页管理和关闭能力。
 */
public class BrowserToolProvider {

    private static final int DEFAULT_DOM_SUMMARY_MAX_LENGTH = 8000;
    private static final int MAX_DOM_SUMMARY_MAX_LENGTH = 20000;

    private final ChromeLauncher launcher;
    private ChromeDiscovery discovery;
    private CdpWebSocketClient wsClient;
    private CdpSession session;

    public BrowserToolProvider() {
        this.launcher = new ChromeLauncher();
    }

    // ---- 生命周期管理 ----

    private synchronized CdpSession ensureSession() throws Exception {
        if (session != null && wsClient != null && wsClient.isConnected()) {
            return session;
        }

        // 先尝试连接已有实例
        try {
            discovery = new ChromeDiscovery(9222);
            discovery.getVersion();
            // 已有实例可用
        } catch (Exception e) {
            // 启动新实例
            int port = launcher.launch(true, null);
            discovery = new ChromeDiscovery(port);
        }

        return connectToWebSocket(discovery.getWebSocketDebuggerUrl());
    }

    private synchronized CdpSession connectToWebSocket(String wsUrl) throws Exception {
        CdpWebSocketClient newClient = new CdpWebSocketClient();
        newClient.connect(wsUrl).get();
        try {
            enableCoreDomains(newClient);
        } catch (Exception e) {
            try {
                newClient.close().get();
            } catch (Exception ignored) {
            }
            throw e;
        }

        CdpWebSocketClient oldClient = wsClient;
        wsClient = newClient;
        session = new CdpSession(newClient);
        if (oldClient != null && oldClient != newClient) {
            try {
                oldClient.close().get();
            } catch (Exception ignored) {
            }
        }
        return session;
    }

    private void enableCoreDomains(CdpWebSocketClient client) throws Exception {
        client.sendSync("Page.enable", null);
        client.sendSync("Runtime.enable", null);
        client.sendSync("DOM.enable", null);
    }

    void reconnectToTarget(String targetId) throws Exception {
        if (discovery == null) {
            throw new IllegalStateException("Chrome discovery 未初始化");
        }
        connectToWebSocket(discovery.getWebSocketDebuggerUrl(targetId));
    }

    public synchronized void close() {
        if (wsClient != null) {
            try {
                wsClient.close().get();
            } catch (Exception ignored) {
            }
            wsClient = null;
        }
        session = null;
        if (launcher != null) {
            launcher.kill();
        }
    }

    // Visible for testing
    void setSession(CdpSession session) {
        this.session = session;
        if (session != null) {
            this.wsClient = session.getClient();
        }
    }

    // ---- 工具实现 ----

    /**
     * browser_navigate: 导航到指定 URL。
     */
    public String navigate(Map<String, String> args) {
        try {
            String url = args.get("url");
            if (url == null || url.isBlank()) {
                return "错误：url 参数不能为空";
            }
            boolean waitForLoad = !"false".equalsIgnoreCase(args.getOrDefault("wait_for_load", "true"));

            CdpSession s = ensureSession();
            s.navigate(url, waitForLoad);

            String currentUrl = s.getCurrentUrl();
            return withDomSummary("✅ 已导航到: " + currentUrl, s, args);
        } catch (Exception e) {
            return "❌ 导航失败: " + e.getMessage();
        }
    }

    /**
     * browser_screenshot: 截取页面截图。
     */
    public String screenshot(Map<String, String> args) {
        try {
            String selector = args.get("selector");
            boolean fullPage = "true".equalsIgnoreCase(args.getOrDefault("full_page", "false"));

            CdpSession s = ensureSession();
            String base64 = s.captureScreenshot(selector, fullPage);

            // 保存到临时文件
            String filename = "browser-screenshot-" + System.currentTimeMillis() + ".png";
            java.nio.file.Path path = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), filename);
            java.nio.file.Files.write(path, Base64.getDecoder().decode(base64));

            String scope = selector != null && !selector.isBlank() ? "元素" : (fullPage ? "全页" : "视口");
            return "✅ 截图已保存: " + path.toAbsolutePath() +
                    "\n尺寸: " + scope +
                    (selector != null ? " (元素: " + selector + ")" : "");
        } catch (Exception e) {
            return "❌ 截图失败: " + e.getMessage();
        }
    }

    /**
     * browser_click: 点击指定元素。
     */
    public String click(Map<String, String> args) {
        try {
            String selector = args.get("selector");
            if (selector == null || selector.isBlank()) {
                return "错误：selector 参数不能为空";
            }

            CdpSession s = ensureSession();
            s.click(selector);
            return withDomSummary("✅ 已点击元素: " + selector, s, args);
        } catch (Exception e) {
            return "❌ 点击失败: " + e.getMessage();
        }
    }

    /**
     * browser_type: 在指定元素中输入文本。
     */
    public String type(Map<String, String> args) {
        try {
            String selector = args.get("selector");
            String text = args.get("text");
            if (selector == null || selector.isBlank()) {
                return "错误：selector 参数不能为空";
            }
            if (text == null) {
                text = "";
            }
            boolean submit = "true".equalsIgnoreCase(args.getOrDefault("submit", "false"));

            CdpSession s = ensureSession();
            s.type(selector, text, submit);
            return withDomSummary("✅ 已在 " + selector + " 中输入文本" + (submit ? " 并提交" : ""), s, args);
        } catch (Exception e) {
            return "❌ 输入失败: " + e.getMessage();
        }
    }

    /**
     * browser_evaluate: 执行 JavaScript。
     */
    public String evaluate(Map<String, String> args) {
        try {
            String script = args.get("script");
            if (script == null || script.isBlank()) {
                return "错误：script 参数不能为空";
            }

            CdpSession s = ensureSession();
            var result = s.evaluate(script);
            String value = result.path("result").path("value").asText(
                    result.path("result").toString()
            );
            return "✅ 执行结果:\n" + value;
        } catch (Exception e) {
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * browser_get_dom: 获取 DOM 文本内容。
     */
    public String getDom(Map<String, String> args) {
        try {
            String selector = args.get("selector");
            int maxLength = 8000;
            try {
                String maxStr = args.get("max_length");
                if (maxStr != null && !maxStr.isBlank()) {
                    maxLength = Integer.parseInt(maxStr);
                }
            } catch (NumberFormatException ignored) {
            }

            CdpSession s = ensureSession();
            String text = s.getDomText(selector, maxLength);
            return text;
        } catch (Exception e) {
            return "❌ 获取 DOM 失败: " + e.getMessage();
        }
    }

    /**
     * browser_tab: 管理标签页。
     */
    public String tab(Map<String, String> args) {
        try {
            String action = args.getOrDefault("action", "list");
            CdpSession s = ensureSession();

            return switch (action) {
                case "list" -> {
                    var tabs = s.getTabs();
                    if (tabs.isEmpty()) {
                        yield "当前没有打开的标签页";
                    }
                    StringBuilder sb = new StringBuilder("📑 标签页列表:\n");
                    for (int i = 0; i < tabs.size(); i++) {
                        CdpSession.TabInfo t = tabs.get(i);
                        sb.append(String.format("%d. %s%s\n   %s\n",
                                i + 1,
                                t.attached() ? "● " : "○ ",
                                t.title(),
                                t.url()));
                    }
                    yield sb.toString().trim();
                }
                case "switch" -> {
                    String targetId = args.get("target_id");
                    if (targetId == null || targetId.isBlank()) {
                        yield "错误：switch 操作需要 target_id 参数";
                    }
                    s.switchToTab(targetId);
                    reconnectToTarget(targetId);
                    yield "✅ 已切换到标签页: " + targetId;
                }
                case "new" -> {
                    String url = args.getOrDefault("url", "about:blank");
                    String newId = s.createTab(url);
                    reconnectToTarget(newId);
                    yield "✅ 已创建新标签页: " + newId + " (" + url + ")";
                }
                case "close" -> {
                    String targetId = args.get("target_id");
                    if (targetId == null || targetId.isBlank()) {
                        yield "错误：close 操作需要 target_id 参数";
                    }
                    s.closeTab(targetId);
                    yield "✅ 已关闭标签页: " + targetId;
                }
                default -> "错误：未知 action '" + action + "'，支持 list/switch/new/close";
            };
        } catch (Exception e) {
            return "❌ 标签页操作失败: " + e.getMessage();
        }
    }

    /**
     * browser_close: 关闭浏览器。
     */
    public String closeBrowser(Map<String, String> args) {
        close();
        return "✅ 浏览器已关闭";
    }

    public boolean isBrowserOpen() {
        return session != null && wsClient != null && wsClient.isConnected();
    }

    public String getBrowserStatus() {
        if (!isBrowserOpen()) {
            return "浏览器未连接";
        }
        try {
            var tabs = session.getTabs();
            StringBuilder sb = new StringBuilder("🌐 浏览器状态: 已连接\n");
            sb.append("📑 标签页数: ").append(tabs.size()).append("\n");
            for (int i = 0; i < Math.min(tabs.size(), 5); i++) {
                CdpSession.TabInfo t = tabs.get(i);
                sb.append(String.format("   %d. %s%s\n", i + 1, t.attached() ? "● " : "○ ", t.title()));
            }
            if (tabs.size() > 5) {
                sb.append("   ... 共 ").append(tabs.size()).append(" 个标签页\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "浏览器已连接，但获取状态失败: " + e.getMessage();
        }
    }

    private String withDomSummary(String successMessage, CdpSession session, Map<String, String> args) {
        if (!shouldIncludeDomSummary(args)) {
            return successMessage;
        }
        try {
            return successMessage + "\n\nDOM 摘要:\n" + session.getCleanDom(parseDomSummaryMaxLength(args));
        } catch (Exception e) {
            return successMessage + "\n\nDOM 摘要获取失败: " + e.getMessage();
        }
    }

    private boolean shouldIncludeDomSummary(Map<String, String> args) {
        String value = args.getOrDefault("include_dom_summary", args.getOrDefault("dom_summary", "true")).trim();
        return !("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value));
    }

    private int parseDomSummaryMaxLength(Map<String, String> args) {
        String value = args.get("dom_summary_max_length");
        if (value == null || value.isBlank()) {
            return DEFAULT_DOM_SUMMARY_MAX_LENGTH;
        }
        try {
            int maxLength = Integer.parseInt(value.trim());
            return Math.max(0, Math.min(maxLength, MAX_DOM_SUMMARY_MAX_LENGTH));
        } catch (NumberFormatException e) {
            return DEFAULT_DOM_SUMMARY_MAX_LENGTH;
        }
    }
}
