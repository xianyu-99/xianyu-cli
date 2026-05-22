package com.paicli.browser;

import java.util.Base64;
import java.util.Map;

/**
 * 浏览器工具提供者。
 *
 * 封装浏览器操控工具，管理 Chrome 进程和 CDP 会话生命周期。
 * 提供 6 个核心工具：navigate、screenshot、click、type、evaluate、get_dom。
 */
public class BrowserToolProvider {

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

        String wsUrl = discovery.getWebSocketDebuggerUrl();
        wsClient = new CdpWebSocketClient();
        wsClient.connect(wsUrl).get();

        // 启用必要域
        wsClient.sendSync("Page.enable", null);
        wsClient.sendSync("Runtime.enable", null);
        wsClient.sendSync("DOM.enable", null);

        session = new CdpSession(wsClient);
        return session;
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
        launcher.kill();
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
            return "✅ 已导航到: " + currentUrl;
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

            return "✅ 截图已保存: " + path.toAbsolutePath() +
                    "\n尺寸: " + (fullPage ? "全页" : "视口") +
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
            return "✅ 已点击元素: " + selector;
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
            return "✅ 已在 " + selector + " 中输入文本" + (submit ? " 并提交" : "");
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
     * browser_close: 关闭浏览器。
     */
    public String closeBrowser(Map<String, String> args) {
        close();
        return "✅ 浏览器已关闭";
    }

    public boolean isBrowserOpen() {
        return session != null && wsClient != null && wsClient.isConnected();
    }
}
