package com.yucli.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GLMClientTest {

    @Test
    void defaultModel_whenModelIsNull() {
        GLMClient client = new GLMClient("key", null);
        assertEquals("glm-5.1", client.getModelName());
    }

    @Test
    void defaultModel_whenModelIsBlank() {
        GLMClient client = new GLMClient("key", "   ");
        assertEquals("glm-5.1", client.getModelName());
    }

    @Test
    void defaultModel_whenModelIsEmpty() {
        GLMClient client = new GLMClient("key", "");
        assertEquals("glm-5.1", client.getModelName());
    }

    @Test
    void singleArgConstructor_usesDefaultModel() {
        GLMClient client = new GLMClient("key");
        assertEquals("glm-5.1", client.getModelName());
    }

    @Test
    void customModel_isUsed() {
        GLMClient client = new GLMClient("key", "glm-4");
        assertEquals("glm-4", client.getModelName());
    }

    @Test
    void getApiUrl() {
        GLMClient client = new GLMClient("key", "model");
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4/chat/completions", client.getApiUrl());
    }

    @Test
    void getProviderName() {
        GLMClient client = new GLMClient("key");
        assertEquals("glm", client.getProviderName());
    }

    @Test
    void maxContextWindow() {
        GLMClient client = new GLMClient("key");
        assertEquals(200_000, client.maxContextWindow());
    }

    @Test
    void supportsPromptCaching() {
        GLMClient client = new GLMClient("key");
        assertTrue(client.supportsPromptCaching());
    }
}
