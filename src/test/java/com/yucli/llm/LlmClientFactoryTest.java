package com.yucli.llm;

import com.yucli.config.YuCLIConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientFactoryTest {

    private YuCLIConfig configWith(String provider, String apiKey, String model) {
        YuCLIConfig config = new YuCLIConfig();
        YuCLIConfig.ProviderConfig pc = new YuCLIConfig.ProviderConfig(apiKey, null, model);
        config.getProviders().put(provider, pc);
        return config;
    }

    private YuCLIConfig configWith(String provider, String apiKey, String baseUrl, String model) {
        YuCLIConfig config = new YuCLIConfig();
        YuCLIConfig.ProviderConfig pc = new YuCLIConfig.ProviderConfig(apiKey, baseUrl, model);
        config.getProviders().put(provider, pc);
        return config;
    }

    @Test
    void create_nullProvider_returnsNull() {
        YuCLIConfig config = new YuCLIConfig();
        assertNull(LlmClientFactory.create(null, config));
    }

    @Test
    void create_blankApiKey_returnsNull() {
        YuCLIConfig config = configWith("deepseek", "  ", null);
        assertNull(LlmClientFactory.create("deepseek", config));
    }

    @Test
    void create_nullApiKey_returnsNull() {
        YuCLIConfig config = configWith("deepseek", null, null);
        assertNull(LlmClientFactory.create("deepseek", config));
    }

    @Test
    void create_deepseek_returnsDeepSeekClient() {
        YuCLIConfig config = configWith("deepseek", "sk-test", "deepseek-v4");
        LlmClient client = LlmClientFactory.create("deepseek", config);
        assertNotNull(client);
        assertInstanceOf(DeepSeekClient.class, client);
    }

    @Test
    void create_glm_returnsGLMClient() {
        YuCLIConfig config = configWith("glm", "glm-key", "glm-5.1");
        LlmClient client = LlmClientFactory.create("glm", config);
        assertNotNull(client);
        assertInstanceOf(GLMClient.class, client);
    }

    @Test
    void create_anthropic_returnsAnthropicClient() {
        YuCLIConfig config = configWith("anthropic", "ant-key", "https://api.example.com", "claude-3");
        LlmClient client = LlmClientFactory.create("anthropic", config);
        assertNotNull(client);
        assertInstanceOf(AnthropicClient.class, client);
    }

    @Test
    void create_unknownProvider_returnsNull() {
        YuCLIConfig config = configWith("unknown", "key", "model");
        assertNull(LlmClientFactory.create("unknown", config));
    }

    @Test
    void create_caseInsensitive() {
        YuCLIConfig config = configWith("deepseek", "sk-test", "model");
        LlmClient client = LlmClientFactory.create("DeepSeek", config);
        assertNotNull(client);
        assertInstanceOf(DeepSeekClient.class, client);
    }
}
