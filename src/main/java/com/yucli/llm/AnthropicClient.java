package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Anthropic Messages API 客户端 — 用于 DeepSeek Anthropic 兼容端点.
 *
 * 与 OpenAI 格式的关键差异:
 * - Auth: x-api-key header (非 Authorization: Bearer)
 * - System prompt: 顶层 system 字段 (不在 messages 数组里)
 * - Messages: role + content, content 可以是字符串或 content block 数组
 * - Tool call: content block type=tool_use (非 tool_calls 数组)
 * - Tool result: user message 内含 type=tool_result 的 content block
 * - SSE: event: 行 + data: 行, 多种事件类型
 */
public class AnthropicClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 8192;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(readTimeout("YuCLI.llm.connect.timeout.seconds", 60), TimeUnit.SECONDS)
            .readTimeout(readTimeout("YuCLI.llm.read.timeout.seconds", 300), TimeUnit.SECONDS)
            .writeTimeout(readTimeout("YuCLI.llm.write.timeout.seconds", 60), TimeUnit.SECONDS)
            .callTimeout(readTimeout("YuCLI.llm.call.timeout.seconds", 600), TimeUnit.SECONDS)
            .build();

    private static long readTimeout(String key, long defaultVal) {
        try {
            String raw = System.getProperty(key);
            if (raw != null && !raw.isBlank()) {
                long v = Long.parseLong(raw.trim());
                return v > 0 ? v : defaultVal;
            }
        } catch (NumberFormatException ignored) {}
        return defaultVal;
    }

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public AnthropicClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.deepseek.com/anthropic";
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : "deepseek-v4-pro";
    }

    // ---- LlmClient impl ----

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
        return chat(messages, tools, StreamListener.NO_OP);
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
        StreamListener l = listener != null ? listener : StreamListener.NO_OP;
        String url = buildUrl();
        String body = buildRequestBody(messages, tools);

        Request request = new Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Anthropic API 请求失败: " + response.code() + " - " + errorBody);
            }
            ResponseBody rb = response.body();
            if (rb == null) throw new IOException("Anthropic API 返回空响应体");

            return parseSseStream(rb.source(), l);
        }
    }

    @Override
    public String getModelName() { return model; }

    @Override
    public String getProviderName() { return "anthropic"; }

    @Override
    public int maxContextWindow() {
        return 1_000_000;
    }

    @Override
    public boolean supportsPromptCaching() {
        return true;
    }

    // ---- request builder ----

    private String buildUrl() {
        String u = baseUrl.replaceAll("/+$", "");
        if (u.endsWith("/v1/messages")) return u;
        if (u.endsWith("/v1")) return u + "/messages";
        return u + "/v1/messages";
    }

    private String buildRequestBody(List<Message> messages, List<Tool> tools) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", DEFAULT_MAX_TOKENS);
        root.put("stream", true);

        // 分离 system prompt
        StringBuilder systemText = new StringBuilder();
        ArrayNode msgs = root.putArray("messages");

        for (Message msg : messages) {
            if ("system".equals(msg.role())) {
                if (!systemText.isEmpty()) systemText.append("\n");
                systemText.append(msg.content());
                continue;
            }

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                // assistant 消息带 tool_use → content block 数组
                ObjectNode assistNode = msgs.addObject();
                assistNode.put("role", "assistant");
                ArrayNode blocks = assistNode.putArray("content");
                if (msg.content() != null && !msg.content().isBlank()) {
                    ObjectNode textBlock = blocks.addObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", msg.content());
                }
                for (ToolCall tc : msg.toolCalls()) {
                    ObjectNode tuBlock = blocks.addObject();
                    tuBlock.put("type", "tool_use");
                    tuBlock.put("id", tc.id());
                    tuBlock.put("name", tc.function().name());
                    try {
                        tuBlock.set("input", MAPPER.readTree(tc.function().arguments()));
                    } catch (Exception e) {
                        tuBlock.putObject("input");
                    }
                }
            } else if (msg.toolCallId() != null) {
                // tool 结果 → user message 内含 tool_result content block
                ObjectNode userNode = msgs.addObject();
                userNode.put("role", "user");
                ArrayNode blocks = userNode.putArray("content");
                ObjectNode trBlock = blocks.addObject();
                trBlock.put("type", "tool_result");
                trBlock.put("tool_use_id", msg.toolCallId());
                trBlock.put("content", msg.content() != null ? msg.content() : "");
            } else {
                // 普通 user / assistant 消息
                ObjectNode msgNode = msgs.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content() != null ? msg.content() : "");
            }
        }

        if (!systemText.isEmpty()) {
            if (supportsPromptCaching()) {
                // Anthropic prompt caching: system as content block array with cache_control
                ArrayNode systemBlocks = root.putArray("system");
                ObjectNode textBlock = systemBlocks.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", systemText.toString());
                ObjectNode cacheControl = textBlock.putObject("cache_control");
                cacheControl.put("type", "ephemeral");
            } else {
                root.put("system", systemText.toString());
            }
        }

        // tools
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = root.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArr.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                toolNode.set("input_schema", tool.parameters());
            }
        }

        return root.toString();
    }

    // ---- SSE parser ----

    private ChatResponse parseSseStream(BufferedSource source, StreamListener listener) throws IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolUseAccumulator> toolAccums = new ArrayList<>();
        int inputTokens = 0;
        int outputTokens = 0;
        int cachedTokens = 0;
        String role = "assistant";

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) break;

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 跳过 event: 行
            if (trimmed.startsWith("event:")) continue;
            if (!trimmed.startsWith("data:")) continue;

            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isEmpty()) continue;

            JsonNode root;
            try {
                root = MAPPER.readTree(payload);
            } catch (Exception e) {
                continue;
            }

            // 检查是否错误响应
            if ("error".equals(root.path("type").asText())) {
                String errMsg = root.path("error").path("message").asText("未知错误");
                throw new IOException("Anthropic API 错误: " + errMsg);
            }

            String eventType = root.path("type").asText("");

            switch (eventType) {
                case "message_start" -> {
                    JsonNode usage = root.path("message").path("usage");
                    inputTokens = usage.path("input_tokens").asInt(0);
                    cachedTokens = usage.path("cache_read_input_tokens").asInt(0);
                }
                case "content_block_start" -> {
                    JsonNode block = root.path("content_block");
                    String blockType = block.path("type").asText("");
                    int idx = root.path("index").asInt(-1);
                    if ("tool_use".equals(blockType)) {
                        while (toolAccums.size() <= idx) toolAccums.add(new ToolUseAccumulator());
                        ToolUseAccumulator acc = toolAccums.get(idx);
                        acc.id = block.path("id").asText("");
                        acc.name = block.path("name").asText("");
                    }
                }
                case "content_block_delta" -> {
                    JsonNode delta = root.path("delta");
                    String deltaType = delta.path("type").asText("");
                    int idx = root.path("index").asInt(-1);
                    switch (deltaType) {
                        case "text_delta" -> {
                            String text = delta.path("text").asText("");
                            content.append(text);
                            listener.onContentDelta(text);
                        }
                        case "input_json_delta" -> {
                            String partial = delta.path("partial_json").asText("");
                            while (toolAccums.size() <= idx) toolAccums.add(new ToolUseAccumulator());
                            toolAccums.get(idx).inputJson.append(partial);
                        }
                        case "thinking_delta" -> {
                            String t = delta.path("thinking").asText("");
                            reasoning.append(t);
                            listener.onReasoningDelta(t);
                        }
                    }
                }
                case "message_delta" -> {
                    JsonNode usage = root.path("usage");
                    outputTokens = usage.path("output_tokens").asInt(0);
                }
                case "message_stop" -> {
                    // 流正常结束
                }
            }
        }

        // 构建 ToolCall 列表
        List<ToolCall> toolCalls = null;
        if (!toolAccums.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (ToolUseAccumulator acc : toolAccums) {
                if (acc.id == null || acc.id.isBlank()) continue;
                toolCalls.add(new ToolCall(
                        acc.id,
                        new ToolCall.Function(acc.name, acc.inputJson.toString())
                ));
            }
        }

        return new ChatResponse(
                role,
                content.toString(),
                reasoning.length() > 0 ? reasoning.toString() : null,
                toolCalls,
                inputTokens,
                outputTokens,
                cachedTokens
        );
    }

    private static class ToolUseAccumulator {
        String id = "";
        String name = "";
        StringBuilder inputJson = new StringBuilder();
    }
}
