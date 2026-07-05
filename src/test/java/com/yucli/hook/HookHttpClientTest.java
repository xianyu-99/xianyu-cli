package com.yucli.hook;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookHttpClientTest {

    @Test
    void postsHeadersSignatureAndRetriesRetryableFailures() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("try again"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"decision\":\"allow\"}"));
            server.start();

            String payload = "{\"event\":\"PreToolUse\"}";
            HookHttpClient client = new HookHttpClient();

            HookHttpClient.HookHttpResult result = client.post(
                    server.url("/hook").toString(),
                    payload,
                    5,
                    Map.of("X-YuCLI-Test", "yes"),
                    "shared-secret",
                    1,
                    1L);

            assertTrue(result.success());
            assertEquals(2, result.attempt());
            assertEquals(2, server.getRequestCount());

            RecordedRequest first = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest second = server.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(first);
            assertNotNull(second);
            assertEquals("POST", first.getMethod());
            assertEquals("yes", first.getHeader("X-YuCLI-Test"));
            assertEquals("YuCLI-Hook", first.getHeader("User-Agent"));
            assertEquals("sha256=" + hmacSha256("shared-secret", payload),
                    first.getHeader("X-YuCLI-Signature"));
            assertEquals(payload, first.getBody().readUtf8());
            assertEquals("/hook", second.getPath());
        }
    }

    @Test
    void failureMessageRedactsSecretBodyValues() {
        HookHttpClient.HookHttpResult result = new HookHttpClient.HookHttpResult(
                500,
                "{\"token\":\"plain-secret\",\"ok\":false}",
                false,
                null,
                "https://hooks.example/pre");

        String message = result.failureMessage();

        assertTrue(message.contains("***"));
        assertFalse(message.contains("plain-secret"));
    }

    private static String hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
