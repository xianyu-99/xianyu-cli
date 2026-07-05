package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleClientTest {

    @Test
    void defaultModel_whenModelIsBlank() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openai",
                "https://api.example.com/v1",
                "key",
                " ",
                "https://api.openai.com/v1",
                "gpt-default",
                128_000,
                false
        );

        assertEquals("gpt-default", client.getModelName());
    }

    @Test
    void buildChatCompletionsUrl_appendsToVersionedBaseUrl() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiCompatibleClient.buildChatCompletionsUrl("https://api.example.com/v1", "ignored")
        );
    }

    @Test
    void buildChatCompletionsUrl_appendsToNonV1VersionedBaseUrl() {
        assertEquals(
                "https://api.example.com/api/paas/v4/chat/completions",
                OpenAiCompatibleClient.buildChatCompletionsUrl("https://api.example.com/api/paas/v4", "ignored")
        );
    }

    @Test
    void buildChatCompletionsUrl_keepsFullEndpoint() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiCompatibleClient.buildChatCompletionsUrl("https://api.example.com/v1/chat/completions", "ignored")
        );
    }

    @Test
    void buildChatCompletionsUrl_appendsVersionWhenBaseUrlHasNoVersion() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiCompatibleClient.buildChatCompletionsUrl("https://api.example.com", "ignored")
        );
    }

    @Test
    void providerNameAndContextWindow() {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "qwen",
                null,
                "key",
                null,
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen3-coder-plus",
                1_000_000,
                false
        );

        assertEquals("qwen", client.getProviderName());
        assertEquals(1_000_000, client.maxContextWindow());
        assertFalse(client.supportsPromptCaching());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", client.getApiUrl());
    }

    @Test
    void reasoningEffort_isIncludedInOpenAiCompatibleRequestBody() throws Exception {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                "openai",
                "https://api.example.com/v1",
                "key",
                "gpt-test",
                "https://api.openai.com/v1",
                "gpt-default",
                128_000,
                false,
                "max"
        );

        Method method = AbstractOpenAiCompatibleClient.class
                .getDeclaredMethod("buildRequestBody", List.class, List.class);
        method.setAccessible(true);
        JsonNode root = (JsonNode) method.invoke(client, List.of(LlmClient.Message.user("hi")), List.of());

        assertEquals("max", client.getReasoningEffort());
        assertEquals("max", root.path("reasoning_effort").asText());
    }
}
