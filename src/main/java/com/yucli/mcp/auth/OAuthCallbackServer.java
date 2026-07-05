package com.yucli.mcp.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OAuthCallbackServer implements AutoCloseable {
    private static final long TIMEOUT_MINUTES = 5;

    private final HttpServer server;
    private final String expectedState;
    private final CompletableFuture<CallbackResult> future = new CompletableFuture<>();

    public OAuthCallbackServer(String expectedState) throws IOException {
        this.expectedState = expectedState;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/callback", this::handleCallback);
        server.setExecutor(null);
    }

    public int start() {
        server.start();
        return server.getAddress().getPort();
    }

    public CallbackResult waitForCode() throws InterruptedException {
        try {
            return future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            future.complete(new CallbackResult(null, "OAuth 回调超时（" + TIMEOUT_MINUTES + " 分钟）"));
            return future.getNow(null);
        } catch (java.util.concurrent.ExecutionException e) {
            return new CallbackResult(null, "OAuth 回调异常: " + e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

        String state = params.get("state");
        if (state == null || !state.equals(expectedState)) {
            sendHtml(exchange, 400, ERROR_HTML.replace("{{message}}", "CSRF 校验失败：state 参数不匹配"));
            future.complete(new CallbackResult(null, "CSRF 校验失败"));
            return;
        }

        String error = params.get("error");
        if (error != null) {
            String desc = params.getOrDefault("error_description", error);
            sendHtml(exchange, 400, ERROR_HTML.replace("{{message}}", "授权失败: " + desc));
            future.complete(new CallbackResult(null, "授权失败: " + desc));
            return;
        }

        String code = params.get("code");
        if (code == null || code.isBlank()) {
            sendHtml(exchange, 400, ERROR_HTML.replace("{{message}}", "缺少 code 参数"));
            future.complete(new CallbackResult(null, "缺少 code 参数"));
            return;
        }

        sendHtml(exchange, 200, SUCCESS_HTML);
        future.complete(new CallbackResult(code, null));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
            }
        }
        return params;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static void sendHtml(HttpExchange exchange, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record CallbackResult(String code, String error) {}

    private static final String SUCCESS_HTML = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>YuCLI - 授权成功</title>
            <style>body{font-family:system-ui;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f5f5}
            .card{background:#fff;padding:2rem;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.1);text-align:center;max-width:400px}
            h2{color:#16a34a}p{color:#555}</style></head>
            <body><div class="card"><h2>&#10003; 授权成功</h2><p>YuCLI 已获得访问权限，可以关闭此页面。</p></div></body></html>
            """;

    private static final String ERROR_HTML = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>YuCLI - 授权失败</title>
            <style>body{font-family:system-ui;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f5f5}
            .card{background:#fff;padding:2rem;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.1);text-align:center;max-width:400px}
            h2{color:#dc2626}p{color:#555}</style></head>
            <body><div class="card"><h2>&#10007; 授权失败</h2><p>{{message}}</p></div></body></html>
            """;
}
