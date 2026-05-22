package com.paicli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CDP 单页面会话封装。
 *
 * 封装常用 CDP 命令的便捷方法，处理命令发送、事件等待和结果解析。
 */
public class CdpSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final CdpWebSocketClient client;
    private final ObjectMapper mapper;

    public CdpSession(CdpWebSocketClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    // ---- Page Domain ----

    /**
     * 导航到指定 URL，等待页面加载完成。
     */
    public void navigate(String url) throws Exception {
        navigate(url, true);
    }

    /**
     * 导航到指定 URL。
     *
     * @param waitForLoad 是否等待 Page.loadEventFired 事件
     */
    public void navigate(String url, boolean waitForLoad) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("url", url);

        if (waitForLoad) {
            // 注册一次性加载完成监听器
            CompletableFuture<Void> loadFuture = new CompletableFuture<>();
            client.onEvent("Page.loadEventFired", p -> loadFuture.complete(null));

            try {
                client.sendSync("Page.navigate", params);
                loadFuture.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("页面加载超时: " + url);
            } finally {
                client.offEvent("Page.loadEventFired");
            }
        } else {
            client.sendSync("Page.navigate", params);
        }
    }

    /**
     * 截取当前页面截图，返回 base64 编码的 PNG 数据。
     */
    public String captureScreenshot() throws Exception {
        return captureScreenshot(null, false);
    }

    /**
     * 截取截图。
     *
     * @param selector CSS 选择器（null 表示整页截图）
     * @param fullPage 是否截取完整页面（含滚动区域）
     */
    public String captureScreenshot(String selector, boolean fullPage) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("format", "png");
        params.put("fromSurface", true);

        if (fullPage) {
            // 获取完整页面尺寸
            JsonNode metrics = client.sendSync("Page.getLayoutMetrics", null);
            JsonNode contentSize = metrics.path("contentSize");
            params.put("clip", mapper.createObjectNode()
                    .put("x", 0).put("y", 0)
                    .put("width", contentSize.path("width").asDouble(1920))
                    .put("height", contentSize.path("height").asDouble(1080))
                    .put("scale", 1));
        }

        if (selector != null && !selector.isBlank()) {
            // 先滚动到元素位置
            scrollIntoView(selector);
        }

        JsonNode result = client.sendSync("Page.captureScreenshot", params);
        return result.path("data").asText();
    }

    /**
     * 获取当前页面 URL。
     */
    public String getCurrentUrl() throws Exception {
        JsonNode result = evaluate("window.location.href");
        return result.path("result").path("value").asText();
    }

    // ---- DOM Domain ----

    /**
     * 获取页面 DOM 文本内容。
     */
    public String getDomText() throws Exception {
        return getDomText(null, 8000);
    }

    /**
     * 获取指定选择器匹配的元素的文本内容。
     */
    public String getDomText(String selector, int maxLength) throws Exception {
        String script;
        if (selector == null || selector.isBlank()) {
            script = "document.body.innerText";
        } else {
            script = String.format(
                "(function() { " +
                "  var el = document.querySelector('%s'); " +
                "  return el ? el.innerText : 'Element not found: %s'; " +
                "})()",
                selector.replace("'", "\\'"), selector.replace("'", "\\'")
            );
        }

        JsonNode result = evaluate(script);
        String text = result.path("result").path("value").asText("");
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength) + "\n... (truncated, total " + text.length() + " chars)";
        }
        return text;
    }

    /**
     * 获取页面 HTML。
     */
    public String getHtml() throws Exception {
        JsonNode result = evaluate("document.documentElement.outerHTML");
        return result.path("result").path("value").asText("");
    }

    // ---- Input Domain ----

    /**
     * 点击指定 CSS 选择器匹配的元素。
     */
    public void click(String selector) throws Exception {
        // 先获取元素位置
        String script = String.format(
            "(function() { " +
            "  var el = document.querySelector('%s'); " +
            "  if (!el) return null; " +
            "  var rect = el.getBoundingClientRect(); " +
            "  return { x: rect.left + rect.width/2, y: rect.top + rect.height/2 }; " +
            "})()",
            selector.replace("'", "\\'")
        );

        JsonNode result = evaluate(script);
        JsonNode coords = result.path("result").path("value");
        if (coords.isNull()) {
            throw new RuntimeException("未找到元素: " + selector);
        }

        double x = coords.path("x").asDouble();
        double y = coords.path("y").asDouble();

        // 分发鼠标事件
        dispatchMouseEvent("mousePressed", x, y);
        dispatchMouseEvent("mouseReleased", x, y);
    }

    /**
     * 在指定元素中输入文本。
     */
    public void type(String selector, String text, boolean submit) throws Exception {
        // 先点击元素获取焦点
        click(selector);

        // 清空现有内容
        String clearScript = String.format(
            "document.querySelector('%s').value = ''",
            selector.replace("'", "\\'")
        );
        evaluate(clearScript);

        // 逐字符输入
        for (char c : text.toCharArray()) {
            dispatchKeyEvent("char", String.valueOf(c));
        }

        if (submit) {
            dispatchKeyEvent("keyDown", "Enter");
            dispatchKeyEvent("keyUp", "Enter");
        }
    }

    // ---- Runtime Domain ----

    /**
     * 执行 JavaScript 代码。
     */
    public JsonNode evaluate(String expression) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("expression", expression);
        params.put("returnByValue", true);
        params.put("awaitPromise", true);
        return client.sendSync("Runtime.evaluate", params);
    }

    // ---- Helper methods ----

    private void scrollIntoView(String selector) throws Exception {
        String script = String.format(
            "document.querySelector('%s').scrollIntoView({behavior: 'instant', block: 'center'})",
            selector.replace("'", "\\'")
        );
        evaluate(script);
        // 等待滚动完成
        Thread.sleep(200);
    }

    private void dispatchMouseEvent(String type, double x, double y) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("type", type);
        params.put("x", x);
        params.put("y", y);
        params.put("button", "left");
        params.put("clickCount", 1);
        client.sendSync("Input.dispatchMouseEvent", params);
    }

    private void dispatchKeyEvent(String type, String key) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("type", type);
        params.put("text", key);
        client.sendSync("Input.dispatchKeyEvent", params);
    }

    // ---- Target Domain (Tab Management) ----

    /**
     * 获取所有标签页列表。
     */
    public java.util.List<TabInfo> getTabs() throws Exception {
        JsonNode result = client.sendSync("Target.getTargets", null);
        JsonNode targets = result.path("targetInfos");
        java.util.List<TabInfo> tabs = new java.util.ArrayList<>();
        for (JsonNode t : targets) {
            if ("page".equals(t.path("type").asText())) {
                tabs.add(new TabInfo(
                    t.path("targetId").asText(),
                    t.path("title").asText(""),
                    t.path("url").asText(""),
                    t.path("attached").asBoolean(false)
                ));
            }
        }
        return tabs;
    }

    /**
     * 切换到指定标签页。
     */
    public void switchToTab(String targetId) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("targetId", targetId);
        client.sendSync("Target.activateTarget", params);
    }

    /**
     * 创建新标签页。
     */
    public String createTab(String url) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("url", url != null ? url : "about:blank");
        JsonNode result = client.sendSync("Target.createTarget", params);
        return result.path("targetId").asText();
    }

    /**
     * 关闭指定标签页。
     */
    public void closeTab(String targetId) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("targetId", targetId);
        client.sendSync("Target.closeTarget", params);
    }

    public CdpWebSocketClient getClient() {
        return client;
    }

    public record TabInfo(String targetId, String title, String url, boolean attached) {}
}
