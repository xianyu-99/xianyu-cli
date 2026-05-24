package com.yucli.mcp.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yucli.mcp.transport.McpTransport;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JsonRpcClient implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final McpTransport transport;
    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "YuCLI-mcp-jsonrpc-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Consumer<JsonNode>> notificationListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, java.util.function.Function<JsonNode, JsonNode>> requestHandlers = new ConcurrentHashMap<>();

    public JsonRpcClient(McpTransport transport) {
        this.transport = transport;
        this.transport.onReceive(this::handleMessage);
    }

    public JsonNode request(String method, JsonNode params) throws IOException {
        return request(method, params, DEFAULT_TIMEOUT_SECONDS);
    }

    public JsonNode request(String method, JsonNode params, long timeoutSeconds) throws IOException {
        long id = ids.getAndIncrement();
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        scheduler.schedule(() -> {
            CompletableFuture<JsonNode> removed = pending.remove(id);
            if (removed != null) {
                removed.completeExceptionally(new TimeoutException("JSON-RPC request timed out: " + method));
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        try {
            transport.send(request);
            return future.get(timeoutSeconds + 1, TimeUnit.SECONDS);
        } catch (JsonRpcException e) {
            throw e;
        } catch (Exception e) {
            pending.remove(id);
            if (e.getCause() instanceof JsonRpcException jsonRpcException) {
                throw jsonRpcException;
            }
            throw new IOException(e.getMessage(), e);
        }
    }

    public void sendNotification(String method, JsonNode params) throws IOException {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", params);
        }
        transport.send(notification);
    }

    public void onNotification(Consumer<JsonNode> listener) {
        if (listener != null) {
            notificationListeners.add(listener);
        }
    }

    public void onRequest(String method, java.util.function.Function<JsonNode, JsonNode> handler) {
        if (method != null && handler != null) {
            requestHandlers.put(method, handler);
        }
    }

    private void handleMessage(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode == null || idNode.isNull()) {
            for (Consumer<JsonNode> listener : notificationListeners) {
                listener.accept(message);
            }
            return;
        }

        // Server-initiated request: has id AND method
        JsonNode methodNode = message.get("method");
        if (methodNode != null && !methodNode.isNull()) {
            String method = methodNode.asText("");
            JsonNode params = message.get("params");
            java.util.function.Function<JsonNode, JsonNode> handler = requestHandlers.get(method);
            if (handler == null) {
                sendResponse(idNode, null, new JsonRpcException(-32601, "Method not found: " + method));
                return;
            }
            try {
                JsonNode result = handler.apply(params);
                sendResponse(idNode, result, null);
            } catch (Exception e) {
                sendResponse(idNode, null, new JsonRpcException(-32603, e.getMessage()));
            }
            return;
        }

        // Response to our request
        long id = idNode.asLong();
        CompletableFuture<JsonNode> future = pending.remove(id);
        if (future == null) {
            return;
        }
        JsonNode error = message.get("error");
        if (error != null && !error.isNull()) {
            future.completeExceptionally(new JsonRpcException(
                    error.path("code").asInt(-32603),
                    error.path("message").asText("JSON-RPC error")));
            return;
        }
        future.complete(message.get("result"));
    }

    private void sendResponse(JsonNode idNode, JsonNode result, JsonRpcException error) {
        try {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", idNode.deepCopy());
            if (error != null) {
                ObjectNode errorObj = response.putObject("error");
                errorObj.put("code", error.code());
                errorObj.put("message", error.getMessage());
            } else {
                response.set("result", result != null ? result : JsonNodeFactory.instance.objectNode());
            }
            transport.send(response);
        } catch (IOException e) {
            // Log but don't throw - we're in a callback
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        transport.close();
    }
}
