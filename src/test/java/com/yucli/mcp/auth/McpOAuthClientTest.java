package com.yucli.mcp.auth;

import org.junit.jupiter.api.Test;

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
}
