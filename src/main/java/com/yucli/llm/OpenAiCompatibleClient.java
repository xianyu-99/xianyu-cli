package com.yucli.llm;

public class OpenAiCompatibleClient extends AbstractOpenAiCompatibleClient {

    private final String providerName;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxContextWindow;
    private final boolean supportsPromptCaching;

    public OpenAiCompatibleClient(
            String providerName,
            String baseUrl,
            String apiKey,
            String model,
            String defaultBaseUrl,
            String defaultModel,
            int maxContextWindow,
            boolean supportsPromptCaching
    ) {
        this.providerName = providerName;
        this.apiUrl = buildChatCompletionsUrl(baseUrl, defaultBaseUrl);
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : defaultModel;
        this.maxContextWindow = maxContextWindow;
        this.supportsPromptCaching = supportsPromptCaching;
    }

    static String buildChatCompletionsUrl(String baseUrl, String defaultBaseUrl) {
        String raw = baseUrl != null && !baseUrl.isBlank() ? baseUrl : defaultBaseUrl;
        String normalized = raw.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.matches(".*/v\\d+(?:\\.\\d+)?$")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public int maxContextWindow() {
        return maxContextWindow;
    }

    @Override
    public boolean supportsPromptCaching() {
        return supportsPromptCaching;
    }
}
