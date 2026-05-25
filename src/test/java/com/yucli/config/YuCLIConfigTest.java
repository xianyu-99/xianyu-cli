package com.yucli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YuCLIConfigTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Default constructor ──────────────────────────────────────────

    @Test
    @DisplayName("Default constructor: defaultProvider is 'anthropic'")
    void defaultConstructor_defaultProvider() {
        YuCLIConfig config = new YuCLIConfig();
        assertEquals("anthropic", config.getDefaultProvider());
    }

    @Test
    @DisplayName("Default constructor: providers map is empty")
    void defaultConstructor_providersEmpty() {
        YuCLIConfig config = new YuCLIConfig();
        assertNotNull(config.getProviders());
        assertTrue(config.getProviders().isEmpty());
    }

    // ── Top-level setters ────────────────────────────────────────────

    @Test
    @DisplayName("setDefaultProvider updates the value")
    void setDefaultProvider() {
        YuCLIConfig config = new YuCLIConfig();
        config.setDefaultProvider("deepseek");
        assertEquals("deepseek", config.getDefaultProvider());
    }

    @Test
    @DisplayName("setProviders replaces the map")
    void setProviders() {
        YuCLIConfig config = new YuCLIConfig();
        Map<String, YuCLIConfig.ProviderConfig> map = new LinkedHashMap<>();
        map.put("a", new YuCLIConfig.ProviderConfig());
        config.setProviders(map);
        assertEquals(1, config.getProviders().size());
        assertTrue(config.getProviders().containsKey("a"));
    }

    // ── ProviderConfig POJO ──────────────────────────────────────────

    @Nested
    @DisplayName("ProviderConfig")
    class ProviderConfigTest {

        @Test
        @DisplayName("3-arg constructor sets all fields")
        void threeArgConstructor() {
            YuCLIConfig.ProviderConfig pc =
                    new YuCLIConfig.ProviderConfig("sk-xxx", "https://api.example.com", "gpt-4");
            assertEquals("sk-xxx", pc.getApiKey());
            assertEquals("https://api.example.com", pc.getBaseUrl());
            assertEquals("gpt-4", pc.getModel());
        }

        @Test
        @DisplayName("No-arg constructor leaves fields null")
        void noArgConstructor() {
            YuCLIConfig.ProviderConfig pc = new YuCLIConfig.ProviderConfig();
            assertNull(pc.getApiKey());
            assertNull(pc.getBaseUrl());
            assertNull(pc.getModel());
        }

        @Test
        @DisplayName("Setters overwrite values from constructor")
        void setters() {
            YuCLIConfig.ProviderConfig pc =
                    new YuCLIConfig.ProviderConfig("old-key", "https://old.example.com", "old-model");
            pc.setApiKey("new-key");
            pc.setBaseUrl("https://new.example.com");
            pc.setModel("new-model");
            assertEquals("new-key", pc.getApiKey());
            assertEquals("https://new.example.com", pc.getBaseUrl());
            assertEquals("new-model", pc.getModel());
        }
    }

    // ── getApiKey / getModel / getBaseUrl with provider in map ───────

    @Test
    @DisplayName("getApiKey returns map value when provider is present")
    void getApiKey_providerInMap() {
        YuCLIConfig config = new YuCLIConfig();
        config.getProviders().put("anthropic",
                new YuCLIConfig.ProviderConfig("my-key", "https://api.anthropic.com", "claude-3"));
        assertEquals("my-key", config.getApiKey("anthropic"));
    }

    @Test
    @DisplayName("getModel returns map value when provider is present")
    void getModel_providerInMap() {
        YuCLIConfig config = new YuCLIConfig();
        config.getProviders().put("deepseek",
                new YuCLIConfig.ProviderConfig("dk-key", "https://api.deepseek.com", "deepseek-chat"));
        assertEquals("deepseek-chat", config.getModel("deepseek"));
    }

    @Test
    @DisplayName("getBaseUrl returns map value when provider is present")
    void getBaseUrl_providerInMap() {
        YuCLIConfig config = new YuCLIConfig();
        config.getProviders().put("glm",
                new YuCLIConfig.ProviderConfig("glm-key", "https://open.bigmodel.cn", "glm-4"));
        assertEquals("https://open.bigmodel.cn", config.getBaseUrl("glm"));
    }

    // ── getApiKey / getModel / getBaseUrl with provider NOT in map ───
    // (depends on no matching env var being set in the test environment)

    @Test
    @DisplayName("getApiKey returns null for unknown provider when no env var set")
    void getApiKey_providerNotInMap() {
        YuCLIConfig config = new YuCLIConfig();
        // Use a provider name unlikely to have a matching env var
        assertNull(config.getApiKey("nonexistent_provider_xyz"));
    }

    @Test
    @DisplayName("getModel returns null for unknown provider when no env var set")
    void getModel_providerNotInMap() {
        YuCLIConfig config = new YuCLIConfig();
        assertNull(config.getModel("nonexistent_provider_xyz"));
    }

    @Test
    @DisplayName("getBaseUrl returns null for unknown provider when no env var set")
    void getBaseUrl_providerNotInMap() {
        YuCLIConfig config = new YuCLIConfig();
        assertNull(config.getBaseUrl("nonexistent_provider_xyz"));
    }

    // ── getApiKey / getModel / getBaseUrl: blank values fall through ─

    @Test
    @DisplayName("getApiKey returns null when apiKey in map is blank and no env var")
    void getApiKey_blankValueInMap() {
        YuCLIConfig config = new YuCLIConfig();
        // Use a provider name unlikely to have a matching env var
        config.getProviders().put("nonexistent_provider_xyz",
                new YuCLIConfig.ProviderConfig("", null, null));
        // blank apiKey should NOT be returned; falls through to env var (null)
        assertNull(config.getApiKey("nonexistent_provider_xyz"));
    }

    @Test
    @DisplayName("getModel returns null when model in map is blank and no env var")
    void getModel_blankValueInMap() {
        YuCLIConfig config = new YuCLIConfig();
        config.getProviders().put("nonexistent_provider_xyz",
                new YuCLIConfig.ProviderConfig(null, null, "  "));
        assertNull(config.getModel("nonexistent_provider_xyz"));
    }

    // ── Multiple providers ───────────────────────────────────────────

    @Test
    @DisplayName("Multiple providers in map: both accessible independently")
    void multipleProviders() {
        YuCLIConfig config = new YuCLIConfig();
        config.getProviders().put("anthropic",
                new YuCLIConfig.ProviderConfig("ant-key", "https://api.anthropic.com", "claude-3"));
        config.getProviders().put("deepseek",
                new YuCLIConfig.ProviderConfig("dk-key", "https://api.deepseek.com", "deepseek-chat"));

        assertEquals("ant-key", config.getApiKey("anthropic"));
        assertEquals("claude-3", config.getModel("anthropic"));
        assertEquals("https://api.anthropic.com", config.getBaseUrl("anthropic"));

        assertEquals("dk-key", config.getApiKey("deepseek"));
        assertEquals("deepseek-chat", config.getModel("deepseek"));
        assertEquals("https://api.deepseek.com", config.getBaseUrl("deepseek"));
    }

    // ── Jackson deserialization ──────────────────────────────────────

    @Nested
    @DisplayName("Jackson deserialization")
    class JacksonDeserializationTest {

        @Test
        @DisplayName("Deserialize JSON into YuCLIConfig")
        void deserializeBasic() throws Exception {
            String json = """
                    {
                      "defaultProvider": "deepseek",
                      "providers": {
                        "anthropic": {
                          "apiKey": "sk-ant-123",
                          "baseUrl": "https://api.anthropic.com",
                          "model": "claude-3-opus"
                        }
                      }
                    }
                    """;
            YuCLIConfig config = mapper.readValue(json, YuCLIConfig.class);

            assertEquals("deepseek", config.getDefaultProvider());
            assertEquals(1, config.getProviders().size());

            YuCLIConfig.ProviderConfig pc = config.getProviders().get("anthropic");
            assertNotNull(pc);
            assertEquals("sk-ant-123", pc.getApiKey());
            assertEquals("https://api.anthropic.com", pc.getBaseUrl());
            assertEquals("claude-3-opus", pc.getModel());
        }

        @Test
        @DisplayName("Deserialize JSON with multiple providers")
        void deserializeMultipleProviders() throws Exception {
            String json = """
                    {
                      "defaultProvider": "anthropic",
                      "providers": {
                        "anthropic": {
                          "apiKey": "key-a",
                          "model": "claude-3"
                        },
                        "glm": {
                          "apiKey": "key-g",
                          "baseUrl": "https://open.bigmodel.cn"
                        }
                      }
                    }
                    """;
            YuCLIConfig config = mapper.readValue(json, YuCLIConfig.class);

            assertEquals(2, config.getProviders().size());
            assertEquals("key-a", config.getProviders().get("anthropic").getApiKey());
            assertEquals("claude-3", config.getProviders().get("anthropic").getModel());
            assertEquals("key-g", config.getProviders().get("glm").getApiKey());
            assertEquals("https://open.bigmodel.cn", config.getProviders().get("glm").getBaseUrl());
        }
    }

    // ── @JsonIgnoreProperties(ignoreUnknown = true) ──────────────────

    @Test
    @DisplayName("Deserialization ignores unknown top-level and nested fields")
    void ignoreUnknownFields() throws Exception {
        String json = """
                {
                  "defaultProvider": "anthropic",
                  "unknownTopLevel": "should be ignored",
                  "anotherExtra": 42,
                  "providers": {
                    "anthropic": {
                      "apiKey": "sk-xxx",
                      "unknownNested": true,
                      "model": "claude-3"
                    }
                  }
                }
                """;
        // Should NOT throw
        YuCLIConfig config = mapper.readValue(json, YuCLIConfig.class);

        assertEquals("anthropic", config.getDefaultProvider());
        YuCLIConfig.ProviderConfig pc = config.getProviders().get("anthropic");
        assertNotNull(pc);
        assertEquals("sk-xxx", pc.getApiKey());
        assertEquals("claude-3", pc.getModel());
    }
}
