package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

public interface LlmClient {

    ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException;

    ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException;

    String getModelName();

    String getProviderName();

    /**
     * 该模型的最大上下文窗口（token 数）。
     * 例如 GLM-5.1 返回 200_000，DeepSeek V4 / Claude Sonnet 返回 1_000_000。
     */
    default int maxContextWindow() {
        return 128_000;
    }

    /**
     * 是否支持 prompt caching。
     * Anthropic Claude (cache_control)、GLM-5.1 (prompt_cache)、DeepSeek V4 (prefix_cache) 均返回 true。
     */
    default boolean supportsPromptCaching() {
        return false;
    }

    record Message(String role, String content, String reasoningContent, List<ToolCall> toolCalls,
                   String toolCallId) {
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }

        public static Message assistant(String reasoningContent, String content) {
            return new Message("assistant", content, reasoningContent, null, null);
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, null, toolCalls, null);
        }

        public static Message assistant(String reasoningContent, String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, reasoningContent, toolCalls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, null, null, toolCallId);
        }
    }

    record ToolCall(String id, Function function) {
        public record Function(String name, String arguments) {}
    }

    record Tool(String name, String description, JsonNode parameters) {}

    interface StreamListener {
        StreamListener NO_OP = new StreamListener() {};

        default void onReasoningDelta(String delta) {}

        default void onContentDelta(String delta) {}
    }

    record ChatResponse(String role, String content, String reasoningContent, List<ToolCall> toolCalls,
                        int inputTokens, int outputTokens, int cachedTokens) {
        public ChatResponse(String role, String content, List<ToolCall> toolCalls,
                            int inputTokens, int outputTokens) {
            this(role, content, null, toolCalls, inputTokens, outputTokens, 0);
        }

        public ChatResponse(String role, String content, String reasoningContent, List<ToolCall> toolCalls,
                            int inputTokens, int outputTokens) {
            this(role, content, reasoningContent, toolCalls, inputTokens, outputTokens, 0);
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
