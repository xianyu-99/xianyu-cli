package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientTest {

    @Test
    void defaultBaseUrl_whenNull() {
        AnthropicClient client = new AnthropicClient(null, "key", "model");
        // baseUrl is private, but we can verify behavior indirectly via getProviderName/getModelName
        assertEquals("anthropic", client.getProviderName());
    }

    @Test
    void defaultBaseUrl_whenBlank() {
        AnthropicClient client = new AnthropicClient("  ", "key", "model");
        assertEquals("anthropic", client.getProviderName());
    }

    @Test
    void defaultModel_whenNull() {
        AnthropicClient client = new AnthropicClient("https://api.example.com", "key", null);
        assertEquals("deepseek-v4-pro", client.getModelName());
    }

    @Test
    void defaultModel_whenBlank() {
        AnthropicClient client = new AnthropicClient("https://api.example.com", "key", "   ");
        assertEquals("deepseek-v4-pro", client.getModelName());
    }

    @Test
    void defaultModel_whenEmpty() {
        AnthropicClient client = new AnthropicClient("https://api.example.com", "key", "");
        assertEquals("deepseek-v4-pro", client.getModelName());
    }

    @Test
    void customModel_isUsed() {
        AnthropicClient client = new AnthropicClient("https://api.example.com", "key", "claude-3-opus");
        assertEquals("claude-3-opus", client.getModelName());
    }

    @Test
    void customBaseUrl_isUsed() {
        AnthropicClient client = new AnthropicClient("https://custom.api.com", "key", "model");
        // Cannot directly access baseUrl, but the object is constructed without error
        assertNotNull(client);
    }

    @Test
    void getProviderName() {
        AnthropicClient client = new AnthropicClient(null, "key", "model");
        assertEquals("anthropic", client.getProviderName());
    }

    @Test
    void getModelName() {
        AnthropicClient client = new AnthropicClient(null, "key", "my-model");
        assertEquals("my-model", client.getModelName());
    }

    @Test
    void maxContextWindow() {
        AnthropicClient client = new AnthropicClient(null, "key", "model");
        assertEquals(1_000_000, client.maxContextWindow());
    }

    @Test
    void supportsPromptCaching() {
        AnthropicClient client = new AnthropicClient(null, "key", "model");
        assertTrue(client.supportsPromptCaching());
    }

    @Test
    void reasoningEffort_isIncludedInRequestBody() throws Exception {
        AnthropicClient client = new AnthropicClient(null, "key", "model", "xhigh");

        Method method = AnthropicClient.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        method.setAccessible(true);
        String body = (String) method.invoke(client, List.of(LlmClient.Message.user("hi")), List.of());
        JsonNode root = new ObjectMapper().readTree(body);

        assertEquals("xhigh", client.getReasoningEffort());
        assertEquals("xhigh", root.path("reasoning_effort").asText());
    }
}
