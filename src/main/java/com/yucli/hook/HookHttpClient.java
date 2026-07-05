package com.yucli.hook;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class HookHttpClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_BODY_CHARS = 8_000;

    private final OkHttpClient client;

    HookHttpClient() {
        this(new OkHttpClient());
    }

    HookHttpClient(OkHttpClient client) {
        this.client = client == null ? new OkHttpClient() : client;
    }

    HookHttpResult post(String url, String inputJson, int timeoutSeconds) {
        return post(url, inputJson, timeoutSeconds, Map.of(), null, 0, 200L);
    }

    HookHttpResult post(String url, String inputJson, int timeoutSeconds,
                        Map<String, String> headers, String signatureSecret,
                        int retryCount, long retryBackoffMillis) {
        int attempts = Math.max(1, retryCount + 1);
        HookHttpResult last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            last = postOnce(url, inputJson, timeoutSeconds, headers, signatureSecret, attempt);
            if (last.success() || attempt == attempts || !last.retryable()) {
                return last;
            }
            sleepBeforeRetry(retryBackoffMillis, attempt);
        }
        return last == null ? new HookHttpResult(-1, "", false, "hook http POST failed", url, 0) : last;
    }

    private HookHttpResult postOnce(String url, String inputJson, int timeoutSeconds,
                                    Map<String, String> headers, String signatureSecret, int attempt) {
        String payload = inputJson == null ? "{}" : inputJson;
        try {
            OkHttpClient callClient = client.newBuilder()
                    .callTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .build();
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "YuCLI-Hook")
                    .post(RequestBody.create(payload, JSON));
            normalizedHeaders(headers).forEach(requestBuilder::header);
            if (signatureSecret != null && !signatureSecret.isBlank()) {
                requestBuilder.header("X-YuCLI-Signature", "sha256=" + hmacSha256(signatureSecret, payload));
            }
            Request request = requestBuilder.build();

            try (Response response = callClient.newCall(request).execute()) {
                String body = readBody(response.body());
                return new HookHttpResult(response.code(), body, false, null, url, attempt);
            }
        } catch (InterruptedIOException e) {
            return new HookHttpResult(-1, "", true,
                    "hook http POST timed out after " + timeoutSeconds + "s", url, attempt);
        } catch (Exception e) {
            return new HookHttpResult(-1, "", false, HookRedactor.redact(e.getMessage()), url, attempt);
        }
    }

    private static String readBody(ResponseBody body) throws Exception {
        if (body == null) {
            return "";
        }
        String value = body.string();
        if (value.length() <= MAX_BODY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_BODY_CHARS) + System.lineSeparator() + "...(hook http body truncated)";
    }

    private static Map<String, String> normalizedHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> normalized = new java.util.LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                normalized.put(key.trim(), value.trim());
            }
        });
        return normalized;
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

    private static void sleepBeforeRetry(long retryBackoffMillis, int attempt) {
        long sleepMillis = Math.max(0, retryBackoffMillis) * attempt;
        if (sleepMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record HookHttpResult(int statusCode, String body, boolean timedOut, String error, String url, int attempt) {
        HookHttpResult(int statusCode, String body, boolean timedOut, String error, String url) {
            this(statusCode, body, timedOut, error, url, 1);
        }

        boolean success() {
            return statusCode >= 200 && statusCode < 300 && !timedOut && error == null;
        }

        boolean retryable() {
            return timedOut || error != null || statusCode == 429 || statusCode >= 500;
        }

        String failureMessage() {
            if (timedOut || error != null) {
                return error == null ? "hook http POST failed" : error;
            }
            String trimmed = HookRedactor.redact(body == null ? "" : body.trim());
            return trimmed.isBlank()
                    ? "hook http POST returned status " + statusCode + " for " + url
                    : "hook http POST returned status " + statusCode + " for " + url + ": " + trimmed;
        }
    }
}
