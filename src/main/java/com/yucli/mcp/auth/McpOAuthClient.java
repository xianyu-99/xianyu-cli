package com.yucli.mcp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.mcp.config.McpServerConfig;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class McpOAuthClient implements TokenProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final String serverName;
    private final McpServerConfig config;
    private final TokenStore tokenStore;
    private final TokenStore.TokenEntry[] cachedToken = new TokenStore.TokenEntry[1];

    public McpOAuthClient(String serverName, McpServerConfig config, TokenStore tokenStore) {
        this.serverName = serverName;
        this.config = config;
        this.tokenStore = tokenStore;
        this.cachedToken[0] = tokenStore.getToken(serverName);
    }

    @Override
    public String getAccessToken() {
        TokenStore.TokenEntry entry = cachedToken[0];
        if (entry == null) return null;
        return entry.accessToken();
    }

    @Override
    public boolean isTokenValid() {
        return tokenStore.hasValidToken(serverName);
    }

    @Override
    public void refreshToken() {
        TokenStore.TokenEntry entry = cachedToken[0];
        if (entry == null || entry.refreshToken() == null) {
            throw new IllegalStateException("没有可用的 refresh_token，请重新授权: " + serverName);
        }
        try {
            TokenStore.TokenEntry refreshed = refreshAccessToken(entry.refreshToken());
            cachedToken[0] = refreshed;
            tokenStore.saveToken(serverName, refreshed);
        } catch (IOException e) {
            throw new IllegalStateException("刷新 token 失败: " + e.getMessage(), e);
        }
    }

    public String authorize() throws IOException, InterruptedException {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = UUID.randomUUID().toString();

        String authUrl = buildAuthorizationUrl(codeChallenge, state);

        System.out.println("正在打开浏览器进行 OAuth 授权...");
        System.out.println("授权 URL: " + authUrl);

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(authUrl));
            } catch (Exception e) {
                System.out.println("无法自动打开浏览器，请手动访问上述 URL");
            }
        } else {
            System.out.println("当前环境不支持自动打开浏览器，请手动访问上述 URL");
        }

        try (OAuthCallbackServer callbackServer = new OAuthCallbackServer(state)) {
            int port = callbackServer.start();
            System.out.println("本地回调服务已启动，端口: " + port);
            System.out.println("等待授权回调...");

            OAuthCallbackServer.CallbackResult result = callbackServer.waitForCode();
            if (result.error() != null) {
                throw new IOException("OAuth 授权失败: " + result.error());
            }

            TokenStore.TokenEntry token = exchangeCode(result.code(), codeVerifier);
            cachedToken[0] = token;
            tokenStore.saveToken(serverName, token);
            System.out.println("OAuth 授权成功，token 已保存");
            return token.accessToken();
        }
    }

    TokenStore.TokenEntry exchangeCode(String code, String codeVerifier) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", buildRedirectUri())
                .add("client_id", config.getClientId())
                .add("code_verifier", codeVerifier)
                .build();

        Request request = new Request.Builder()
                .url(config.getTokenEndpoint())
                .header("Accept", "application/json")
                .post(body)
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new IOException("token 端点返回 " + response.code() + ": " + respBody);
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            return parseTokenResponse(json);
        }
    }

    TokenStore.TokenEntry refreshAccessToken(String refreshToken) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", config.getClientId())
                .build();

        Request request = new Request.Builder()
                .url(config.getTokenEndpoint())
                .header("Accept", "application/json")
                .post(body)
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new IOException("refresh token 失败 " + response.code() + ": " + respBody);
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            return parseTokenResponse(json);
        }
    }

    private String buildAuthorizationUrl(String codeChallenge, String state) {
        StringBuilder sb = new StringBuilder(config.getAuthorizationEndpoint());
        sb.append(config.getAuthorizationEndpoint().contains("?") ? "&" : "?");
        sb.append("response_type=code");
        sb.append("&client_id=").append(urlEncode(config.getClientId()));
        sb.append("&redirect_uri=").append(urlEncode(buildRedirectUri()));
        sb.append("&code_challenge=").append(urlEncode(codeChallenge));
        sb.append("&code_challenge_method=S256");
        sb.append("&state=").append(urlEncode(state));
        if (config.getScopes() != null && !config.getScopes().isEmpty()) {
            sb.append("&scope=").append(urlEncode(String.join(" ", config.getScopes())));
        }
        return sb.toString();
    }

    private String buildRedirectUri() {
        return "http://127.0.0.1/callback";
    }

    private TokenStore.TokenEntry parseTokenResponse(JsonNode json) {
        String accessToken = json.path("access_token").asText(null);
        String refreshToken = json.path("refresh_token").asText(null);
        long expiresIn = json.path("expires_in").asLong(3600);
        long expiresAtEpoch = Instant.now().getEpochSecond() + expiresIn;
        return new TokenStore.TokenEntry(accessToken, refreshToken, expiresAtEpoch);
    }

    static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
