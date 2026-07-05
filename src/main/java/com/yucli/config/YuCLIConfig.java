package com.yucli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YuCLIConfig {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".YuCLI");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private String defaultProvider = "anthropic";
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private String reasoningEffort;
        private String wireApi;

        public ProviderConfig() {}

        public ProviderConfig(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
        }

        public ProviderConfig(String apiKey, String baseUrl, String model, String reasoningEffort) {
            this(apiKey, baseUrl, model);
            this.reasoningEffort = reasoningEffort;
        }

        public ProviderConfig(String apiKey, String baseUrl, String model, String reasoningEffort, String wireApi) {
            this(apiKey, baseUrl, model, reasoningEffort);
            this.wireApi = wireApi;
        }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getReasoningEffort() { return reasoningEffort; }
        public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
        public String getWireApi() { return wireApi; }
        public void setWireApi(String wireApi) { this.wireApi = wireApi; }
    }

    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    public String getEffectiveDefaultProvider() {
        String provider = loadDefaultProviderFromEnv();
        if (provider != null && !provider.isBlank()) {
            return provider.toLowerCase();
        }
        return defaultProvider;
    }

    public String getApiKey(String provider) {
        ProviderConfig providerConfig = providers.get(provider);
        if (providerConfig != null && providerConfig.getApiKey() != null && !providerConfig.getApiKey().isBlank()) {
            return providerConfig.getApiKey();
        }
        return loadApiKeyFromEnv(provider);
    }

    public String getModel(String provider) {
        ProviderConfig providerConfig = providers.get(provider);
        if (providerConfig != null && providerConfig.getModel() != null && !providerConfig.getModel().isBlank()) {
            return providerConfig.getModel();
        }
        return loadModelFromEnv(provider);
    }

    public String getBaseUrl(String provider) {
        ProviderConfig providerConfig = providers.get(provider);
        if (providerConfig != null && providerConfig.getBaseUrl() != null && !providerConfig.getBaseUrl().isBlank()) {
            return providerConfig.getBaseUrl();
        }
        return loadBaseUrlFromEnv(provider);
    }

    public String getReasoningEffort(String provider) {
        ProviderConfig providerConfig = providers.get(provider);
        if (providerConfig != null && providerConfig.getReasoningEffort() != null
                && !providerConfig.getReasoningEffort().isBlank()) {
            return normalizeReasoningEffort(providerConfig.getReasoningEffort());
        }
        return loadReasoningEffortFromEnv(provider);
    }

    public String getWireApi(String provider) {
        ProviderConfig providerConfig = providers.get(provider);
        if (providerConfig != null && providerConfig.getWireApi() != null && !providerConfig.getWireApi().isBlank()) {
            return normalizeWireApi(providerConfig.getWireApi());
        }
        return loadWireApiFromEnv(provider);
    }

    public static YuCLIConfig load() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                return mapper.readValue(CONFIG_FILE.toFile(), YuCLIConfig.class);
            } catch (IOException e) {
                System.err.println("⚠️ 配置文件读取失败，使用默认配置: " + e.getMessage());
            }
        }
        return new YuCLIConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            mapper.writeValue(CONFIG_FILE.toFile(), this);
        } catch (IOException e) {
            System.err.println("⚠️ 配置保存失败: " + e.getMessage());
        }
    }

    private static String loadModelFromEnv(String provider) {
        String envKey = switch (provider.toLowerCase()) {
            case "glm" -> "GLM_MODEL";
            case "deepseek" -> "DEEPSEEK_MODEL";
            case "anthropic" -> "ANTHROPIC_MODEL";
            default -> provider.toUpperCase() + "_MODEL";
        };

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String dotEnvValue = readFromDotEnv(envKey);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) {
            return dotEnvValue.trim();
        }

        return null;
    }

    private static String loadApiKeyFromEnv(String provider) {
        List<String> envKeys = apiKeyEnvKeys(provider);

        for (String envKey : envKeys) {
            String envValue = System.getenv(envKey);
            if (isConfiguredSecret(envValue)) {
                return envValue.trim();
            }
        }

        for (String envKey : envKeys) {
            String dotEnvValue = readFromDotEnv(envKey);
            if (isConfiguredSecret(dotEnvValue)) {
                return dotEnvValue.trim();
            }
        }

        return null;
    }

    private static String loadReasoningEffortFromEnv(String provider) {
        List<String> envKeys = reasoningEffortEnvKeys(provider);

        for (String envKey : envKeys) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                return normalizeReasoningEffort(envValue);
            }
        }

        for (String envKey : envKeys) {
            String dotEnvValue = readFromDotEnv(envKey);
            if (dotEnvValue != null && !dotEnvValue.isBlank()) {
                return normalizeReasoningEffort(dotEnvValue);
            }
        }

        return null;
    }

    private static String loadWireApiFromEnv(String provider) {
        List<String> envKeys = wireApiEnvKeys(provider);

        for (String envKey : envKeys) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                return normalizeWireApi(envValue);
            }
        }

        for (String envKey : envKeys) {
            String dotEnvValue = readFromDotEnv(envKey);
            if (dotEnvValue != null && !dotEnvValue.isBlank()) {
                return normalizeWireApi(dotEnvValue);
            }
        }

        return null;
    }

    private static String loadDefaultProviderFromEnv() {
        for (String envKey : defaultProviderEnvKeys()) {
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                return envValue.trim();
            }
        }

        for (String envKey : defaultProviderEnvKeys()) {
            String dotEnvValue = readFromDotEnv(envKey);
            if (dotEnvValue != null && !dotEnvValue.isBlank()) {
                return dotEnvValue.trim();
            }
        }

        return null;
    }

    static List<String> reasoningEffortEnvKeys(String provider) {
        String prefix = provider.toUpperCase();
        return switch (provider.toLowerCase()) {
            case "anthropic" -> List.of("ANTHROPIC_REASONING_EFFORT", "ANTHROPIC_MODEL_REASONING_EFFORT",
                    "MODEL_REASONING_EFFORT", "YUCLI_REASONING_EFFORT");
            default -> List.of(prefix + "_REASONING_EFFORT", prefix + "_MODEL_REASONING_EFFORT",
                    "MODEL_REASONING_EFFORT", "YUCLI_REASONING_EFFORT");
        };
    }

    static List<String> wireApiEnvKeys(String provider) {
        String prefix = provider.toUpperCase();
        return List.of(prefix + "_WIRE_API", prefix + "_WIRE", "MODEL_WIRE_API", "YUCLI_WIRE_API");
    }

    static List<String> defaultProviderEnvKeys() {
        return List.of("YUCLI_DEFAULT_PROVIDER", "MODEL_PROVIDER");
    }

    private static String normalizeReasoningEffort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeWireApi(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    static List<String> apiKeyEnvKeys(String provider) {
        return switch (provider.toLowerCase()) {
            case "glm" -> List.of("GLM_API_KEY");
            case "deepseek" -> List.of("DEEPSEEK_API_KEY");
            case "anthropic" -> List.of("ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN");
            default -> List.of(provider.toUpperCase() + "_API_KEY");
        };
    }

    static boolean isConfiguredSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.equals("your_api_key_here")
                && !normalized.equals("your-key")
                && !normalized.equals("your_key")
                && !normalized.equals("your_openai_compatible_key_here")
                && !normalized.equals("your_qwen_api_key_here")
                && !normalized.equals("your_deepseek_api_key_here")
                && !normalized.startsWith("your_")
                && !normalized.endsWith("_here");
    }

    private static String loadBaseUrlFromEnv(String provider) {
        String envKey = switch (provider.toLowerCase()) {
            case "anthropic" -> "ANTHROPIC_BASE_URL";
            case "glm" -> "GLM_BASE_URL";
            case "deepseek" -> "DEEPSEEK_BASE_URL";
            default -> provider.toUpperCase() + "_BASE_URL";
        };

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) return envValue.trim();
        String dotEnvValue = readFromDotEnv(envKey);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) return dotEnvValue.trim();
        return null;
    }

    private static String readFromDotEnv(String key) {
        File[] envFiles = { new File(".env"), new File(System.getProperty("user.home"), ".env") };
        for (File envFile : envFiles) {
            if (!envFile.exists()) continue;
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith(key + "=")) {
                        return line.substring((key + "=").length()).trim();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }
}
