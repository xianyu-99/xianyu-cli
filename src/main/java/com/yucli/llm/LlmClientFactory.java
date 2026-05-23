package com.yucli.llm;

import com.yucli.config.YuCLIConfig;

public class LlmClientFactory {

    private LlmClientFactory() {}

    public static LlmClient create(String provider, YuCLIConfig config) {
        if (provider == null) return null;

        String normalized = provider.toLowerCase();
        String apiKey = config.getApiKey(normalized);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String model = config.getModel(normalized);

        String baseUrl = config.getBaseUrl(normalized);

        return switch (normalized) {
            case "glm" -> new GLMClient(apiKey, model);
            case "deepseek" -> new DeepSeekClient(apiKey, model);
            case "anthropic" -> new AnthropicClient(baseUrl, apiKey, model);
            default -> null;
        };
    }

    public static LlmClient createFromConfig(YuCLIConfig config) {
        LlmClient client = create(config.getDefaultProvider(), config);
        if (client != null) {
            return client;
        }

        for (String provider : new String[]{"anthropic", "glm", "deepseek"}) {
            client = create(provider, config);
            if (client != null) {
                return client;
            }
        }

        return null;
    }
}
