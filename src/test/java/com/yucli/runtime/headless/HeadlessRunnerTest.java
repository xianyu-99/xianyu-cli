package com.yucli.runtime.headless;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessRunnerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void runWithFakeLlmReturnsSuccessResult() {
        FakeLlmClient llmClient = new FakeLlmClient("fake result");
        HeadlessRunner runner = HeadlessRunner.withMinimalReactLlmClient(llmClient);

        HeadlessRunResult result = runner.run("summarize repo", "react");

        assertTrue(result.success());
        assertEquals("summarize repo", result.task());
        assertEquals("react", result.mode());
        assertEquals("fake result", result.result());
        assertEquals("", result.error());
        assertTrue(result.durationMs() >= 0);
        assertEquals("summarize repo", llmClient.lastMessages.get(0).content());
    }

    @Test
    void unsupportedModeReturnsFailureResult() {
        HeadlessRunner runner = HeadlessRunner.withMinimalReactLlmClient(new FakeLlmClient("unused"));

        HeadlessRunResult result = runner.run("make a plan", "plan");

        assertFalse(result.success());
        assertEquals("plan", result.mode());
        assertEquals("", result.result());
        assertTrue(result.error().contains("Headless mode not implemented yet: plan"));
    }

    @Test
    void runAndWriteWritesOneJsonlResultEvent() throws IOException {
        HeadlessRunner runner = HeadlessRunner.withMinimalReactLlmClient(new FakeLlmClient("jsonl output"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HeadlessRunResult result;
        try (JsonlEventWriter writer = new JsonlEventWriter(output)) {
            result = runner.runAndWrite(HeadlessRunRequest.react("emit event"), writer);
        }

        String jsonl = output.toString(StandardCharsets.UTF_8);
        String[] lines = jsonl.strip().split("\\R");
        assertEquals(1, lines.length);

        JsonNode node = MAPPER.readTree(lines[0]);
        assertEquals(result.task(), node.get("task").asText());
        assertEquals("react", node.get("mode").asText());
        assertTrue(node.get("success").asBoolean());
        assertEquals("jsonl output", node.get("result").asText());
        assertEquals("", node.get("error").asText());
        assertNotNull(node.get("durationMs"));
    }

    private static final class FakeLlmClient implements LlmClient {
        private final String content;
        private List<Message> lastMessages = List.of();

        private FakeLlmClient(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            lastMessages = List.copyOf(messages);
            return new ChatResponse("assistant", content, null, 1, 1);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "fake-model";
        }

        @Override
        public String getProviderName() {
            return "fake-provider";
        }
    }
}
