package com.yucli.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.mcp.protocol.McpInitializeRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import com.yucli.mcp.auth.TokenProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class StreamableHttpTransport implements McpTransport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build();
    private final String url;
    private final Map<String, String> headers;
    private final List<Consumer<JsonNode>> listeners = new CopyOnWriteArrayList<>();
    private volatile String sessionId;
    private volatile TokenProvider tokenProvider;

    public StreamableHttpTransport(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public void setTokenProvider(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void send(JsonNode message) throws IOException {
        sendWithRetry(message, false);
    }

    private void sendWithRetry(JsonNode message, boolean retried) throws IOException {
        RequestBody body = RequestBody.create(MAPPER.writeValueAsString(message), JSON);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", McpInitializeRequest.PROTOCOL_VERSION)
                .post(body);
        headers.forEach(builder::header);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        addOAuthAuthorizationHeader(builder);

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 401 && !retried && tokenProvider != null) {
                response.close();
                try {
                    tokenProvider.refreshToken();
                } catch (Exception refreshEx) {
                    throw new IOException("OAuth token 刷新失败: " + refreshEx.getMessage(), refreshEx);
                }
                sendWithRetry(message, true);
                return;
            }

            String newSession = response.header("Mcp-Session-Id");
            if (newSession != null && !newSession.isBlank()) {
                sessionId = newSession;
            }
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return;
            }
            String contentType = response.header("Content-Type", "");
            if (contentType.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream")) {
                streamSse(responseBody, message.path("id").asText(null));
            } else {
                String raw = responseBody.string();
                if (raw == null || raw.isBlank()) {
                    return;
                }
                dispatch(MAPPER.readTree(raw));
            }
        }
    }

    @Override
    public void onReceive(Consumer<JsonNode> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public String transportName() {
        return "http";
    }

    @Override
    public void close() {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("MCP-Protocol-Version", McpInitializeRequest.PROTOCOL_VERSION)
                .header("Mcp-Session-Id", sessionId)
                .delete();
        headers.forEach(builder::header);
        addOAuthAuthorizationHeader(builder);
        // close 是 best-effort：server 已经关停 / 网络不通时不应该让 YuCLI 退出卡住。
        // 主 client 的 callTimeout 是 60s，这里用 5s 短超时单独发请求。
        OkHttpClient closeClient = client.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .connectTimeout(2, TimeUnit.SECONDS)
                .build();
        try (Response ignored = closeClient.newCall(builder.build()).execute()) {
            // best effort
        } catch (IOException ignored) {
        }
    }

    private void addOAuthAuthorizationHeader(Request.Builder builder) {
        TokenProvider provider = tokenProvider;
        if (provider != null && provider.isTokenValid()) {
            builder.header("Authorization", "Bearer " + provider.getAccessToken());
        }
    }

    private void streamSse(ResponseBody responseBody, String requestId) throws IOException {
        BufferedSource source = responseBody.source();
        StringBuilder data = new StringBuilder();
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                if (!data.isEmpty()) {
                    dispatch(MAPPER.readTree(data.toString()));
                }
                return;
            }
            if (line.isBlank()) {
                if (!data.isEmpty()) {
                    JsonNode node = MAPPER.readTree(data.toString());
                    data.setLength(0);
                    dispatch(node);
                    if (requestId != null && requestId.equals(node.path("id").asText(null))) {
                        return;
                    }
                }
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring("data:".length()).trim());
            }
        }
    }

    private void dispatch(JsonNode node) {
        for (Consumer<JsonNode> listener : listeners) {
            listener.accept(node);
        }
    }

}
