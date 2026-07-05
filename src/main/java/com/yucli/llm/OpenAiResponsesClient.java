package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiResponsesClient extends AbstractOpenAiCompatibleClient {

    private final String providerName;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxContextWindow;
    private final boolean supportsPromptCaching;
    private final String reasoningEffort;

    public OpenAiResponsesClient(
            String providerName,
            String baseUrl,
            String apiKey,
            String model,
            String defaultBaseUrl,
            String defaultModel,
            int maxContextWindow,
            boolean supportsPromptCaching,
            String reasoningEffort
    ) {
        this.providerName = providerName;
        this.apiUrl = buildResponsesUrl(baseUrl, defaultBaseUrl);
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : defaultModel;
        this.maxContextWindow = maxContextWindow;
        this.supportsPromptCaching = supportsPromptCaching;
        this.reasoningEffort = reasoningEffort;
    }

    static String buildResponsesUrl(String baseUrl, String defaultBaseUrl) {
        String raw = baseUrl != null && !baseUrl.isBlank() ? baseUrl : defaultBaseUrl;
        String normalized = raw.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/responses")) {
            return normalized;
        }
        if (normalized.matches(".*/v\\d+(?:\\.\\d+)?$")) {
            return normalized + "/responses";
        }
        return normalized + "/v1/responses";
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

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
        return chat(messages, tools, StreamListener.NO_OP);
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
        StreamListener streamListener = listener == null ? StreamListener.NO_OP : listener;
        RequestBody body = RequestBody.create(
                buildRequestBody(messages, tools).toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(getApiUrl())
                .header("Authorization", "Bearer " + getApiKey())
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = SHARED_HTTP_CLIENT.newCall(request).execute()) {
            ResponseBody responseBodyObj = response.body();
            if (!response.isSuccessful()) {
                String errorBody = responseBodyObj != null ? responseBodyObj.string() : "empty response body";
                throw new IOException("API request failed: " + response.code() + " - " + errorBody);
            }
            if (responseBodyObj == null) {
                throw new IOException("API returned an empty response body");
            }

            BufferedSource source = responseBodyObj.source();
            String role = "assistant";
            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            List<ToolCallAccumulator> toolAccumulators = new ArrayList<>();
            Map<String, ToolCallAccumulator> toolAccumulatorsByItemId = new HashMap<>();
            UsageAccumulator usage = new UsageAccumulator();

            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }

                String payload = trimmed.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }

                JsonNode root = mapper.readTree(payload);
                role = handleEvent(root, role, content, reasoning, toolAccumulators,
                        toolAccumulatorsByItemId, usage, streamListener);
            }

            return new ChatResponse(
                    role,
                    content.toString(),
                    reasoning.toString(),
                    buildToolCalls(toolAccumulators),
                    usage.inputTokens,
                    usage.outputTokens,
                    usage.cachedTokens
            );
        }
    }

    ObjectNode buildRequestBody(List<Message> messages, List<Tool> tools) {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", getModel());
        requestBody.put("stream", true);
        requestBody.put("store", false);

        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            ObjectNode reasoningNode = requestBody.putObject("reasoning");
            reasoningNode.put("effort", reasoningEffort);
        }

        ArrayNode inputArray = requestBody.putArray("input");
        for (Message msg : messages) {
            if (msg.toolCallId() != null && !msg.toolCallId().isBlank()) {
                ObjectNode outputNode = inputArray.addObject();
                outputNode.put("type", "function_call_output");
                outputNode.put("call_id", msg.toolCallId());
                outputNode.put("output", msg.content() == null ? "" : msg.content());
                continue;
            }

            if (msg.content() != null && !msg.content().isBlank()) {
                ObjectNode messageNode = inputArray.addObject();
                messageNode.put("role", msg.role());
                messageNode.put("content", msg.content());
            }

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                for (ToolCall toolCall : msg.toolCalls()) {
                    ObjectNode functionCallNode = inputArray.addObject();
                    functionCallNode.put("type", "function_call");
                    functionCallNode.put("call_id", toolCall.id());
                    functionCallNode.put("name", toolCall.function().name());
                    functionCallNode.put("arguments", toolCall.function().arguments());
                }
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());
                if (tool.parameters() != null) {
                    toolNode.set("parameters", tool.parameters());
                } else {
                    toolNode.set("parameters", mapper.createObjectNode());
                }
            }
        }

        return requestBody;
    }

    private String handleEvent(
            JsonNode root,
            String currentRole,
            StringBuilder content,
            StringBuilder reasoning,
            List<ToolCallAccumulator> toolAccumulators,
            Map<String, ToolCallAccumulator> toolAccumulatorsByItemId,
            UsageAccumulator usage,
            StreamListener streamListener
    ) throws IOException {
        parseUsage(root.path("usage"), usage);
        parseUsage(root.path("response").path("usage"), usage);

        String type = root.path("type").asText("");
        if ("response.failed".equals(type)) {
            throw responseError(root);
        }

        if ("response.output_text.delta".equals(type)) {
            String delta = root.path("delta").asText("");
            if (!delta.isEmpty()) {
                content.append(delta);
                streamListener.onContentDelta(delta);
            }
            return currentRole;
        }

        if (type.contains("reasoning") && type.endsWith(".delta")) {
            String delta = root.path("delta").asText("");
            if (!delta.isEmpty()) {
                reasoning.append(delta);
                streamListener.onReasoningDelta(delta);
            }
            return currentRole;
        }

        if ("response.function_call_arguments.delta".equals(type)) {
            ToolCallAccumulator accumulator = accumulatorFor(toolAccumulators, toolAccumulatorsByItemId, root);
            accumulator.arguments.append(root.path("delta").asText(""));
            return currentRole;
        }

        if ("response.function_call_arguments.done".equals(type)) {
            ToolCallAccumulator accumulator = accumulatorFor(toolAccumulators, toolAccumulatorsByItemId, root);
            String arguments = root.path("arguments").asText("");
            if (!arguments.isEmpty()) {
                accumulator.replaceArguments(arguments);
            }
            return currentRole;
        }

        if ("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) {
            mergeOutputItem(root.path("item"), toolAccumulators, toolAccumulatorsByItemId, root);
            return currentRole;
        }

        if ("response.completed".equals(type)) {
            return mergeCompletedResponse(root.path("response"), currentRole, content,
                    toolAccumulators, toolAccumulatorsByItemId, usage);
        }

        return currentRole;
    }

    private void parseUsage(JsonNode usageNode, UsageAccumulator usage) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return;
        }

        usage.inputTokens = firstPositive(
                usageNode.path("input_tokens").asInt(0),
                usageNode.path("prompt_tokens").asInt(0),
                usage.inputTokens
        );
        usage.outputTokens = firstPositive(
                usageNode.path("output_tokens").asInt(0),
                usageNode.path("completion_tokens").asInt(0),
                usage.outputTokens
        );
        usage.cachedTokens = firstPositive(
                usageNode.path("input_tokens_details").path("cached_tokens").asInt(0),
                usageNode.path("prompt_tokens_details").path("cached_tokens").asInt(0),
                usageNode.path("prompt_cache_hit_tokens").asInt(0),
                usage.cachedTokens
        );
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private ToolCallAccumulator accumulatorFor(
            List<ToolCallAccumulator> accumulators,
            Map<String, ToolCallAccumulator> accumulatorsByItemId,
            JsonNode event
    ) {
        String itemId = event.path("item_id").asText("");
        if (!itemId.isEmpty() && accumulatorsByItemId.containsKey(itemId)) {
            return accumulatorsByItemId.get(itemId);
        }

        int index = event.path("output_index").asInt(accumulators.size());
        while (accumulators.size() <= index) {
            accumulators.add(new ToolCallAccumulator());
        }

        ToolCallAccumulator accumulator = accumulators.get(index);
        if (!itemId.isEmpty()) {
            accumulator.itemId = itemId;
            accumulatorsByItemId.put(itemId, accumulator);
        }
        return accumulator;
    }

    private void mergeOutputItem(
            JsonNode item,
            List<ToolCallAccumulator> accumulators,
            Map<String, ToolCallAccumulator> accumulatorsByItemId,
            JsonNode event
    ) {
        if (item == null || item.isMissingNode() || !"function_call".equals(item.path("type").asText(""))) {
            return;
        }

        ToolCallAccumulator accumulator = accumulatorFor(accumulators, accumulatorsByItemId, event);
        String itemId = item.path("id").asText("");
        if (!itemId.isEmpty()) {
            accumulator.itemId = itemId;
            accumulatorsByItemId.put(itemId, accumulator);
        }

        String callId = item.path("call_id").asText("");
        if (!callId.isEmpty()) {
            accumulator.id = callId;
        } else if (!itemId.isEmpty() && (accumulator.id == null || accumulator.id.isBlank())) {
            accumulator.id = itemId;
        }

        String name = item.path("name").asText("");
        if (!name.isEmpty()) {
            accumulator.name = name;
        }

        String arguments = item.path("arguments").asText("");
        if (!arguments.isEmpty()) {
            accumulator.replaceArguments(arguments);
        }
    }

    private String mergeCompletedResponse(
            JsonNode responseNode,
            String currentRole,
            StringBuilder content,
            List<ToolCallAccumulator> accumulators,
            Map<String, ToolCallAccumulator> accumulatorsByItemId,
            UsageAccumulator usage
    ) {
        if (responseNode == null || responseNode.isMissingNode() || responseNode.isNull()) {
            return currentRole;
        }

        parseUsage(responseNode.path("usage"), usage);

        String role = currentRole;
        JsonNode output = responseNode.path("output");
        if (!output.isArray()) {
            return role;
        }

        for (int i = 0; i < output.size(); i++) {
            JsonNode item = output.get(i);
            String type = item.path("type").asText("");
            if ("message".equals(type)) {
                String itemRole = item.path("role").asText("");
                if (!itemRole.isEmpty()) {
                    role = itemRole;
                }
                if (content.length() == 0) {
                    appendMessageOutputText(item, content);
                }
            } else if ("function_call".equals(type)) {
                ObjectNode syntheticEvent = mapper.createObjectNode();
                syntheticEvent.put("output_index", i);
                mergeOutputItem(item, accumulators, accumulatorsByItemId, syntheticEvent);
            }
        }
        return role;
    }

    private void appendMessageOutputText(JsonNode item, StringBuilder content) {
        JsonNode itemContent = item.path("content");
        if (!itemContent.isArray()) {
            String text = itemContent.asText("");
            if (!text.isEmpty()) {
                content.append(text);
            }
            return;
        }

        for (JsonNode contentItem : itemContent) {
            String type = contentItem.path("type").asText("");
            if ("output_text".equals(type) || "text".equals(type)) {
                String text = contentItem.path("text").asText("");
                if (!text.isEmpty()) {
                    content.append(text);
                }
            }
        }
    }

    private IOException responseError(JsonNode root) {
        JsonNode error = root.path("response").path("error");
        if (error.isMissingNode() || error.isNull()) {
            error = root.path("error");
        }
        String message = error.path("message").asText(root.toString());
        return new IOException("API request failed: " + message);
    }

    private List<ToolCall> buildToolCalls(List<ToolCallAccumulator> accumulators) {
        if (accumulators.isEmpty()) {
            return null;
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : accumulators) {
            if (accumulator.id == null || accumulator.id.isBlank()
                    || accumulator.name == null || accumulator.name.isBlank()) {
                continue;
            }
            toolCalls.add(new ToolCall(
                    accumulator.id,
                    new ToolCall.Function(accumulator.name, accumulator.arguments.toString())
            ));
        }
        return toolCalls.isEmpty() ? null : toolCalls;
    }

    private static final class UsageAccumulator {
        private int inputTokens;
        private int outputTokens;
        private int cachedTokens;
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String itemId;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void replaceArguments(String value) {
            arguments.setLength(0);
            arguments.append(value);
        }
    }
}
