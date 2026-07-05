package com.yucli.hook;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.InterruptedIOException;
import java.time.Duration;

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
        try {
            OkHttpClient callClient = client.newBuilder()
                    .callTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "YuCLI-Hook")
                    .post(RequestBody.create(inputJson == null ? "{}" : inputJson, JSON))
                    .build();

            try (Response response = callClient.newCall(request).execute()) {
                String body = readBody(response.body());
                return new HookHttpResult(response.code(), body, false, null, url);
            }
        } catch (InterruptedIOException e) {
            return new HookHttpResult(-1, "", true,
                    "hook http POST timed out after " + timeoutSeconds + "s", url);
        } catch (Exception e) {
            return new HookHttpResult(-1, "", false, e.getMessage(), url);
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

    record HookHttpResult(int statusCode, String body, boolean timedOut, String error, String url) {
        boolean success() {
            return statusCode >= 200 && statusCode < 300 && !timedOut && error == null;
        }

        String failureMessage() {
            if (timedOut || error != null) {
                return error == null ? "hook http POST failed" : error;
            }
            String trimmed = body == null ? "" : body.trim();
            return trimmed.isBlank()
                    ? "hook http POST returned status " + statusCode + " for " + url
                    : "hook http POST returned status " + statusCode + " for " + url + ": " + trimmed;
        }
    }
}
