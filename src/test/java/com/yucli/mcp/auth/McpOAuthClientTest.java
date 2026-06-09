package com.yucli.mcp.auth;

import com.yucli.mcp.config.McpServerConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class McpOAuthClientTest {

    @Test
    void codeVerifierIs43Characters() {
        String verifier = McpOAuthClient.generateCodeVerifier();
        assertEquals(43, verifier.length(), "code_verifier 长度应为 43 字符");
    }

    @Test
    void codeVerifierIsUrlSafe() {
        String verifier = McpOAuthClient.generateCodeVerifier();
        assertTrue(verifier.matches("[A-Za-z0-9_-]+"), "code_verifier 应仅包含 URL-safe 字符");
    }

    @Test
    void codeVerifierGeneratesUniqueValues() {
        String v1 = McpOAuthClient.generateCodeVerifier();
        String v2 = McpOAuthClient.generateCodeVerifier();
        assertNotEquals(v1, v2, "每次生成的 code_verifier 应不同");
    }

    @Test
    void codeChallengeIs43Characters() {
        String verifier = McpOAuthClient.generateCodeVerifier();
        String challenge = McpOAuthClient.generateCodeChallenge(verifier);
        assertEquals(43, challenge.length(), "code_challenge 长度应为 43 字符");
    }

    @Test
    void codeChallengeIsUrlSafe() {
        String verifier = McpOAuthClient.generateCodeVerifier();
        String challenge = McpOAuthClient.generateCodeChallenge(verifier);
        assertTrue(challenge.matches("[A-Za-z0-9_-]+"), "code_challenge 应仅包含 URL-safe 字符");
    }

    @Test
    void codeChallengeIsDeterministic() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String c1 = McpOAuthClient.generateCodeChallenge(verifier);
        String c2 = McpOAuthClient.generateCodeChallenge(verifier);
        assertEquals(c1, c2, "相同 verifier 应产生相同 challenge");
    }

    @Test
    void codeChallengeDiffersFromVerifier() {
        String verifier = McpOAuthClient.generateCodeVerifier();
        String challenge = McpOAuthClient.generateCodeChallenge(verifier);
        assertNotEquals(verifier, challenge, "challenge 不应等于 verifier");
    }

    @Test
    void refreshTokenPreservesExistingRefreshTokenWhenResponseOmitsIt(@TempDir Path tempDir) throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"new-access\",\"expires_in\":3600}"));

            McpServerConfig config = new McpServerConfig();
            config.setClientId("client-id");
            config.setTokenEndpoint(server.url("/token").toString());

            TokenStore store = new TokenStore(tempDir.toFile());
            store.saveToken("demo", new TokenStore.TokenEntry("old-access", "old-refresh", 1));

            McpOAuthClient client = new McpOAuthClient("demo", config, store);
            client.refreshToken();

            RecordedRequest request = server.takeRequest();
            assertEquals("/token", request.getPath());
            assertTrue(request.getBody().readUtf8().contains("refresh_token=old-refresh"));

            TokenStore.TokenEntry saved = store.getToken("demo");
            assertEquals("new-access", saved.accessToken());
            assertEquals("old-refresh", saved.refreshToken());
        } finally {
            server.shutdown();
        }
    }
}
