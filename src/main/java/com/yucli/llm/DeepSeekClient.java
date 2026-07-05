package com.yucli.llm;

public class DeepSeekClient extends AbstractOpenAiCompatibleClient {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String reasoningEffort;

    public DeepSeekClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public DeepSeekClient(String apiKey, String model) {
        this(apiKey, null, model);
    }

    public DeepSeekClient(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, null);
    }

    public DeepSeekClient(String apiKey, String baseUrl, String model, String reasoningEffort) {
        this.apiKey = apiKey;
        this.apiUrl = OpenAiCompatibleClient.buildChatCompletionsUrl(baseUrl, API_URL);
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.reasoningEffort = reasoningEffort;
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
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProviderName() {
        return "deepseek";
    }

    @Override
    public int maxContextWindow() {
        return 1_000_000;
    }

    @Override
    public boolean supportsPromptCaching() {
        return true;
    }
}
