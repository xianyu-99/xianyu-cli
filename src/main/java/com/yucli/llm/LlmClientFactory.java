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
        String reasoningEffort = config.getReasoningEffort(normalized);
        String wireApi = config.getWireApi(normalized);

        return switch (normalized) {
            case "glm" -> new GLMClient(apiKey, baseUrl, model, reasoningEffort);
            case "deepseek" -> new DeepSeekClient(apiKey, baseUrl, model, reasoningEffort);
            case "anthropic" -> new AnthropicClient(baseUrl, apiKey, model, reasoningEffort);
            case "openai" -> "responses".equals(wireApi)
                    ? new OpenAiResponsesClient(
                            "openai",
                            baseUrl,
                            apiKey,
                            model,
                            "https://api.openai.com/v1",
                            "gpt-4o",
                            128_000,
                            false,
                            reasoningEffort
                    )
                    : new OpenAiCompatibleClient(
                    "openai",
                    baseUrl,
                    apiKey,
                    model,
                    "https://api.openai.com/v1",
                    "gpt-4o",
                    128_000,
                    false,
                    reasoningEffort
            );
            case "qwen" -> new OpenAiCompatibleClient(
                    "qwen",
                    baseUrl,
                    apiKey,
                    model,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "qwen3-coder-plus",
                    1_000_000,
                    false,
                    reasoningEffort
            );
            default -> null;
        };
    }

    public static LlmClient createFromConfig(YuCLIConfig config) {
        LlmClient client = create(config.getEffectiveDefaultProvider(), config);
        if (client != null) {
            return client;
        }

        for (String provider : new String[]{"anthropic", "glm", "deepseek", "qwen", "openai"}) {
            client = create(provider, config);
            if (client != null) {
                return client;
            }
        }

        return null;
    }
}
