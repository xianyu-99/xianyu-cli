package com.yucli.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekClientTest {

    @Test
    void defaultModel_whenModelIsNull() {
        DeepSeekClient client = new DeepSeekClient("key", null);
        assertEquals("deepseek-v4-flash", client.getModelName());
    }

    @Test
    void defaultModel_whenModelIsBlank() {
        DeepSeekClient client = new DeepSeekClient("key", "   ");
        assertEquals("deepseek-v4-flash", client.getModelName());
    }

    @Test
    void defaultModel_whenModelIsEmpty() {
        DeepSeekClient client = new DeepSeekClient("key", "");
        assertEquals("deepseek-v4-flash", client.getModelName());
    }

    @Test
    void singleArgConstructor_usesDefaultModel() {
        DeepSeekClient client = new DeepSeekClient("key");
        assertEquals("deepseek-v4-flash", client.getModelName());
    }

    @Test
    void customModel_isUsed() {
        DeepSeekClient client = new DeepSeekClient("key", "deepseek-coder");
        assertEquals("deepseek-coder", client.getModelName());
    }

    @Test
    void getApiUrl() {
        DeepSeekClient client = new DeepSeekClient("key", "model");
        assertEquals("https://api.deepseek.com/chat/completions", client.getApiUrl());
    }

    @Test
    void getProviderName() {
        DeepSeekClient client = new DeepSeekClient("key");
        assertEquals("deepseek", client.getProviderName());
    }

    @Test
    void maxContextWindow() {
        DeepSeekClient client = new DeepSeekClient("key");
        assertEquals(1_000_000, client.maxContextWindow());
    }

    @Test
    void supportsPromptCaching() {
        DeepSeekClient client = new DeepSeekClient("key");
        assertTrue(client.supportsPromptCaching());
    }
}
