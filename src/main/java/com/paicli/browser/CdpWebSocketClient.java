package com.paicli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Chrome DevTools Protocol WebSocket 客户端。
 *
 * 基于 Java 17 内置 WebSocket + Jackson，零额外依赖。
 * 负责：
 * 1. WebSocket 连接生命周期管理
 * 2. JSON-RPC 2.0 请求/响应配对（id → CompletableFuture）
 * 3. CDP 事件监听（method 存在但无 id 的消息）
 */
public class CdpWebSocketClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final AtomicLong requestId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Consumer<JsonNode>> eventListeners = new ConcurrentHashMap<>();
    private volatile boolean connected = false;

    public CdpWebSocketClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 连接到 CDP WebSocket 端点。
     */
    public CompletableFuture<Void> connect(String wsUrl) {
        CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        connected = true;
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        handleMessage(data.toString());
                        return WebSocket.Listener.super.onText(ws, data, last);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        connected = false;
                        // 清理所有挂起的请求
                        pendingRequests.forEach((id, future) ->
                            future.completeExceptionally(error));
                        pendingRequests.clear();
                        WebSocket.Listener.super.onError(ws, error);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        connected = false;
                        pendingRequests.forEach((id, future) ->
                            future.completeExceptionally(new RuntimeException("WebSocket closed: " + reason)));
                        pendingRequests.clear();
                        return WebSocket.Listener.super.onClose(ws, statusCode, reason);
                    }
                });

        return wsFuture.thenAccept(ws -> this.webSocket = ws);
    }

    /**
     * 发送 CDP 命令，等待响应。
     */
    public CompletableFuture<JsonNode> send(String method, ObjectNode params) {
        if (!connected || webSocket == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket not connected"));
        }

        long id = requestId.getAndIncrement();
        ObjectNode request = MAPPER.createObjectNode();
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        // 设置超时
        future.orTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> pendingRequests.remove(id));

        webSocket.sendText(request.toString(), true);
        return future;
    }

    /**
     * 发送命令并同步等待结果。
     */
    public JsonNode sendSync(String method, ObjectNode params) throws Exception {
        return send(method, params).get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册 CDP 事件监听器。
     */
    public void onEvent(String method, Consumer<JsonNode> handler) {
        eventListeners.put(method, handler);
    }

    /**
     * 移除事件监听器。
     */
    public void offEvent(String method) {
        eventListeners.remove(method);
    }

    /**
     * 关闭 WebSocket 连接。
     */
    public CompletableFuture<Void> close() {
        connected = false;
        if (webSocket != null) {
            return webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                    .thenRun(() -> {});
        }
        return CompletableFuture.completedFuture(null);
    }

    public boolean isConnected() {
        return connected;
    }

    private void handleMessage(String text) {
        try {
            JsonNode msg = MAPPER.readTree(text);

            // 响应消息：有 id 字段
            if (msg.has("id")) {
                long id = msg.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (msg.has("error")) {
                        String errorMsg = msg.get("error").toString();
                        future.completeExceptionally(new RuntimeException("CDP error: " + errorMsg));
                    } else {
                        future.complete(msg.get("result"));
                    }
                }
                return;
            }

            // 事件消息：有 method 字段但无 id
            if (msg.has("method")) {
                String method = msg.get("method").asText();
                Consumer<JsonNode> handler = eventListeners.get(method);
                if (handler != null) {
                    handler.accept(msg.get("params"));
                }
            }
        } catch (Exception e) {
            // 忽略无法解析的消息
        }
    }
}
