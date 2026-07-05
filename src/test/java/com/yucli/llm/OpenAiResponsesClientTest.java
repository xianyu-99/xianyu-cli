package com.yucli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultModel_whenModelIsBlank() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(
                "openai",
                "https://api.example.com/v1",
                "key",
                " ",
                "https://api.openai.com/v1",
                "gpt-default",
                128_000,
                false,
                null
        );

        assertEquals("gpt-default", client.getModelName());
    }

    @Test
    void buildResponsesUrl_appendsToVersionedBaseUrl() {
        assertEquals(
                "https://api.example.com/v1/responses",
                OpenAiResponsesClient.buildResponsesUrl("https://api.example.com/v1", "ignored")
        );
    }

    @Test
    void buildResponsesUrl_keepsFullEndpoint() {
        assertEquals(
                "https://api.example.com/v1/responses",
                OpenAiResponsesClient.buildResponsesUrl("https://api.example.com/v1/responses", "ignored")
        );
    }

    @Test
    void buildResponsesUrl_appendsVersionWhenBaseUrlHasNoVersion() {
        assertEquals(
                "https://api.example.com/v1/responses",
                OpenAiResponsesClient.buildResponsesUrl("https://api.example.com", "ignored")
        );
    }

    @Test
    void buildRequestBody_usesResponsesShapeAndReasoningEffort() {
        OpenAiResponsesClient client = new OpenAiResponsesClient(
                "openai",
                "https://api.example.com/v1",
                "key",
                "gpt-test",
                "https://api.openai.com/v1",
                "gpt-default",
                128_000,
                false,
                "xhigh"
        );

        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");
        LlmClient.Tool tool = new LlmClient.Tool("read_file", "Read a file", params);
        LlmClient.ToolCall toolCall = new LlmClient.ToolCall(
                "call_1",
                new LlmClient.ToolCall.Function("read_file", "{\"path\":\"README.md\"}")
        );

        JsonNode root = client.buildRequestBody(
                List.of(
                        LlmClient.Message.user("hi"),
                        LlmClient.Message.assistant("", List.of(toolCall)),
                        LlmClient.Message.tool("call_1", "contents")
                ),
                List.of(tool)
        );

        assertEquals("gpt-test", root.path("model").asText());
        assertTrue(root.path("stream").asBoolean());
        assertFalse(root.path("store").asBoolean());
        assertEquals("xhigh", root.path("reasoning").path("effort").asText());

        JsonNode input = root.path("input");
        assertEquals("user", input.get(0).path("role").asText());
        assertEquals("function_call", input.get(1).path("type").asText());
        assertEquals("call_1", input.get(1).path("call_id").asText());
        assertEquals("function_call_output", input.get(2).path("type").asText());
        assertEquals("contents", input.get(2).path("output").asText());

        JsonNode toolNode = root.path("tools").get(0);
        assertEquals("function", toolNode.path("type").asText());
        assertEquals("read_file", toolNode.path("name").asText());
        assertEquals("object", toolNode.path("parameters").path("type").asText());
    }

    @Test
    void chat_parsesResponsesSseAndPostsToResponsesEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"type":"response.output_text.delta","delta":"O"}

                            data: {"type":"response.output_text.delta","delta":"K"}

                            data: {"type":"response.completed","response":{"usage":{"input_tokens":3,"output_tokens":2},"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"OK"}]}]}}

                            data: [DONE]

                            """));

            OpenAiResponsesClient client = new OpenAiResponsesClient(
                    "openai",
                    server.url("/v1").toString(),
                    "key",
                    "gpt-test",
                    "https://api.openai.com/v1",
                    "gpt-default",
                    128_000,
                    false,
                    "xhigh"
            );

            LlmClient.ChatResponse response = client.chat(List.of(LlmClient.Message.user("hi")), List.of());
            RecordedRequest request = server.takeRequest();

            assertEquals("/v1/responses", request.getPath());
            assertEquals("Bearer key", request.getHeader("Authorization"));
            assertEquals("OK", response.content());
            assertEquals("assistant", response.role());
            assertEquals(3, response.inputTokens());
            assertEquals(2, response.outputTokens());

            JsonNode requestBody = mapper.readTree(request.getBody().readUtf8());
            assertEquals("xhigh", requestBody.path("reasoning").path("effort").asText());
        }
    }
}
