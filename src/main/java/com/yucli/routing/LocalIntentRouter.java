package com.yucli.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LocalIntentRouter implements IntentRouter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT = """
            You are YuCLI's local intent router. Classify the user's request for tool routing only.
            Return one compact JSON object and no prose:
            {
              "needs_write": boolean,
              "needs_command": boolean,
              "needs_web": boolean,
              "needs_browser": boolean,
              "needs_mcp": boolean,
              "needs_plugin": boolean,
              "risk": "low" | "medium" | "high" | "deny",
              "confidence": 0.0-1.0,
              "reason": "short reason"
            }
            Use needs_write for file edits, generation, fixes, or project creation.
            Use needs_command for tests, builds, git, docker, shell commands, or running code.
            Use needs_web for URLs, latest/current info, docs lookup, GitHub, or web search.
            Use needs_browser for screenshots, clicking, login, page inspection, or DOM actions.
            Do not answer the user's task.
            """;

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final double confidenceThreshold;
    private final long failureCooldownMillis;
    private final OkHttpClient httpClient;
    private volatile long disabledUntilMillis;

    public LocalIntentRouter(String baseUrl, String apiKey, String model,
                             Duration timeout, double confidenceThreshold,
                             Duration failureCooldown) {
        this.apiUrl = buildChatCompletionsUrl(baseUrl);
        this.apiKey = apiKey == null || apiKey.isBlank() ? "ollama" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "qwen2.5:7b" : model.trim();
        this.confidenceThreshold = Math.max(0, Math.min(1, confidenceThreshold));
        this.failureCooldownMillis = Math.max(0, failureCooldown.toMillis());
        long timeoutSeconds = Math.max(1, timeout.toSeconds());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public static Optional<IntentRouter> fromEnvironment() {
        if (!readBoolean("YuCLI.router.enabled", "YUCLI_ROUTER_ENABLED", false)) {
            return Optional.empty();
        }
        String baseUrl = readConfig("YuCLI.router.base.url", "YUCLI_ROUTER_BASE_URL",
                "http://localhost:11434/v1");
        String apiKey = readConfig("YuCLI.router.api.key", "YUCLI_ROUTER_API_KEY", "ollama");
        String model = readConfig("YuCLI.router.model", "YUCLI_ROUTER_MODEL", "qwen2.5:7b");
        int timeoutSeconds = readInt("YuCLI.router.timeout.seconds", "YUCLI_ROUTER_TIMEOUT_SECONDS", 8);
        double confidence = readDouble("YuCLI.router.confidence.threshold",
                "YUCLI_ROUTER_CONFIDENCE_THRESHOLD", 0.55);
        int cooldownSeconds = readInt("YuCLI.router.failure.cooldown.seconds",
                "YUCLI_ROUTER_FAILURE_COOLDOWN_SECONDS", 60);
        return Optional.of(new LocalIntentRouter(baseUrl, apiKey, model,
                Duration.ofSeconds(timeoutSeconds), confidence, Duration.ofSeconds(cooldownSeconds)));
    }

    @Override
    public Optional<IntentDecision> route(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        if (now < disabledUntilMillis) {
            return Optional.empty();
        }
        try {
            IntentDecision decision = callRouter(prompt);
            if (decision.confidence() < confidenceThreshold) {
                return Optional.empty();
            }
            return Optional.of(decision);
        } catch (Exception e) {
            disabledUntilMillis = now + failureCooldownMillis;
            return Optional.empty();
        }
    }

    private IntentDecision callRouter(String prompt) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", 0);
        body.put("max_tokens", 256);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", prompt);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("router request failed: " + response.code());
            }
            JsonNode root = MAPPER.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                content = raw;
            }
            return parseDecision(content);
        }
    }

    static IntentDecision parseDecision(String content) throws IOException {
        JsonNode json = MAPPER.readTree(extractJsonObject(content));
        return new IntentDecision(
                suggestedTools(json.path("tools")),
                bool(json, "needs_write", "write", "edit", "file_write"),
                bool(json, "needs_command", "command", "shell", "execute"),
                bool(json, "needs_web", "web", "search", "fetch"),
                bool(json, "needs_browser", "browser", "ui", "page"),
                bool(json, "needs_mcp", "mcp"),
                bool(json, "needs_plugin", "plugin"),
                json.path("risk").asText("low"),
                number(json.path("confidence"), 0.75),
                json.path("reason").asText("")
        );
    }

    private static Set<String> suggestedTools(JsonNode toolsNode) {
        Set<String> tools = new LinkedHashSet<>();
        if (toolsNode == null || !toolsNode.isArray()) {
            return tools;
        }
        for (JsonNode tool : toolsNode) {
            String value = tool.asText("");
            if (!value.isBlank()) {
                tools.add(value.trim());
            }
        }
        return tools;
    }

    private static boolean bool(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.path(key);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual()) {
                String text = value.asText("").trim().toLowerCase();
                if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
                    return true;
                }
                if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
                    return false;
                }
            }
        }
        return false;
    }

    private static double number(JsonNode node, double defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        if (node.isNumber()) {
            return node.asDouble(defaultValue);
        }
        try {
            return Double.parseDouble(node.asText(""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String extractJsonObject(String raw) throws IOException {
        if (raw == null) {
            throw new IOException("empty router response");
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("router response did not contain JSON object");
        }
        return trimmed.substring(start, end + 1);
    }

    private static String buildChatCompletionsUrl(String baseUrl) {
        String raw = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434/v1" : baseUrl.trim();
        String normalized = raw.replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.matches(".*/v\\d+(?:\\.\\d+)?$")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private static boolean readBoolean(String propertyKey, String envKey, boolean defaultValue) {
        String value = readConfig(propertyKey, envKey, Boolean.toString(defaultValue));
        return "true".equalsIgnoreCase(value) || "1".equals(value)
                || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }

    private static int readInt(String propertyKey, String envKey, int defaultValue) {
        try {
            int parsed = Integer.parseInt(readConfig(propertyKey, envKey, Integer.toString(defaultValue)).trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double readDouble(String propertyKey, String envKey, double defaultValue) {
        try {
            double parsed = Double.parseDouble(readConfig(propertyKey, envKey, Double.toString(defaultValue)).trim());
            return parsed >= 0 && parsed <= 1 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String readConfig(String propertyKey, String envKey, String defaultValue) {
        String value = System.getProperty(propertyKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = readFromDotEnv(envKey);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String readFromDotEnv(String key) {
        File[] envFiles = { new File(".env"), new File(System.getProperty("user.home"), ".env") };
        for (File envFile : envFiles) {
            if (!envFile.exists()) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith(key + "=")) {
                        return line.substring((key + "=").length()).trim();
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}
