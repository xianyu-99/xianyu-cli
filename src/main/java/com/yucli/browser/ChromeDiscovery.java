package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Chrome DevTools HTTP 发现端点查询。
 *
 * 通过 /json/version 和 /json/list 获取浏览器信息和可用页面的 WebSocket URL。
 */
public class ChromeDiscovery {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int port;

    public ChromeDiscovery(int port) {
        this.port = port;
    }

    /**
     * 获取浏览器的 WebSocket 调试 URL（连接已有实例时使用）。
     */
    public String getWebSocketDebuggerUrl() throws Exception {
        return getWebSocketDebuggerUrl(null);
    }

    /**
     * 获取指定页面的 WebSocket 调试 URL。
     *
     * @param targetId 目标页面 ID，null 表示获取第一个可用页面
     */
    public String getWebSocketDebuggerUrl(String targetId) throws Exception {
        URL url = new URL("http://localhost:" + port + "/json/list");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (InputStream is = conn.getInputStream()) {
            JsonNode pages = MAPPER.readTree(is);
            if (!pages.isArray() || pages.isEmpty()) {
                throw new RuntimeException("Chrome 没有可用页面");
            }

            for (JsonNode page : pages) {
                if (targetId == null || targetId.equals(page.path("id").asText())) {
                    String wsUrl = page.path("webSocketDebuggerUrl").asText();
                    if (wsUrl != null && !wsUrl.isBlank()) {
                        return wsUrl;
                    }
                }
            }
        }

        throw new RuntimeException("未找到页面 " + targetId + " 的 WebSocket 调试 URL");
    }

    /**
     * 获取浏览器版本信息。
     */
    public JsonNode getVersion() throws Exception {
        URL url = new URL("http://localhost:" + port + "/json/version");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (InputStream is = conn.getInputStream()) {
            return MAPPER.readTree(is);
        }
    }

    /**
     * 创建新页面并返回其 WebSocket URL。
     */
    public String createNewPage(String initialUrl) throws Exception {
        String urlStr = "http://localhost:" + port + "/json/new?" +
                (initialUrl != null ? initialUrl : "about:blank");
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (InputStream is = conn.getInputStream()) {
            JsonNode page = MAPPER.readTree(is);
            String wsUrl = page.path("webSocketDebuggerUrl").asText();
            if (wsUrl != null && !wsUrl.isBlank()) {
                return wsUrl;
            }
            throw new RuntimeException("创建新页面失败：响应中无 WebSocket URL");
        }
    }
}
