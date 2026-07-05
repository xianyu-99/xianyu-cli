package com.yucli.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {

    // ---- Message static factories ----

    @Test
    void messageSystem_setsRoleAndContent() {
        LlmClient.Message msg = LlmClient.Message.system("sys-prompt");
        assertEquals("system", msg.role());
        assertEquals("sys-prompt", msg.content());
        assertNull(msg.reasoningContent());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageUser_setsRoleAndContent() {
        LlmClient.Message msg = LlmClient.Message.user("hello");
        assertEquals("user", msg.role());
        assertEquals("hello", msg.content());
        assertNull(msg.reasoningContent());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageAssistant_contentOnly() {
        LlmClient.Message msg = LlmClient.Message.assistant("reply");
        assertEquals("assistant", msg.role());
        assertEquals("reply", msg.content());
        assertNull(msg.reasoningContent());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageAssistant_reasoningAndContent() {
        LlmClient.Message msg = LlmClient.Message.assistant("think-step", "reply");
        assertEquals("assistant", msg.role());
        assertEquals("reply", msg.content());
        assertEquals("think-step", msg.reasoningContent());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageAssistant_contentWithToolCalls() {
        LlmClient.ToolCall tc = new LlmClient.ToolCall("tc1",
                new LlmClient.ToolCall.Function("myFunc", "{}"));
        LlmClient.Message msg = LlmClient.Message.assistant("calling...", List.of(tc));
        assertEquals("assistant", msg.role());
        assertEquals("calling...", msg.content());
        assertNull(msg.reasoningContent());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("tc1", msg.toolCalls().get(0).id());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageAssistant_reasoningWithToolCalls() {
        LlmClient.ToolCall tc = new LlmClient.ToolCall("tc2",
                new LlmClient.ToolCall.Function("fn", "{\"a\":1}"));
        LlmClient.Message msg = LlmClient.Message.assistant("reason", "done", List.of(tc));
        assertEquals("assistant", msg.role());
        assertEquals("done", msg.content());
        assertEquals("reason", msg.reasoningContent());
        assertNotNull(msg.toolCalls());
        assertEquals(1, msg.toolCalls().size());
        assertNull(msg.toolCallId());
    }

    @Test
    void messageTool_setsRoleAndToolCallId() {
        LlmClient.Message msg = LlmClient.Message.tool("call-42", "result-data");
        assertEquals("tool", msg.role());
        assertEquals("result-data", msg.content());
        assertNull(msg.reasoningContent());
        assertNull(msg.toolCalls());
        assertEquals("call-42", msg.toolCallId());
    }

    // ---- ChatResponse ----

    @Test
    void chatResponse_hasToolCalls_null_returnsFalse() {
        LlmClient.ChatResponse resp = new LlmClient.ChatResponse(
                "assistant", "hi", null, null, 10, 5, 0);
        assertFalse(resp.hasToolCalls());
    }

    @Test
    void chatResponse_hasToolCalls_empty_returnsFalse() {
        LlmClient.ChatResponse resp = new LlmClient.ChatResponse(
                "assistant", "hi", null, List.of(), 10, 5, 0);
        assertFalse(resp.hasToolCalls());
    }

    @Test
    void chatResponse_hasToolCalls_nonEmpty_returnsTrue() {
        LlmClient.ToolCall tc = new LlmClient.ToolCall("id",
                new LlmClient.ToolCall.Function("f", "{}"));
        LlmClient.ChatResponse resp = new LlmClient.ChatResponse(
                "assistant", "hi", null, List.of(tc), 10, 5, 0);
        assertTrue(resp.hasToolCalls());
    }

    @Test
    void chatResponse_convenienceConstructor_noReasoning_defaultsCachedTokensToZero() {
        LlmClient.ChatResponse resp = new LlmClient.ChatResponse(
                "assistant", "hi", (List<LlmClient.ToolCall>) null, 100, 50);
        assertEquals(0, resp.cachedTokens());
        assertNull(resp.reasoningContent());
        assertEquals(100, resp.inputTokens());
        assertEquals(50, resp.outputTokens());
    }

    @Test
    void chatResponse_convenienceConstructor_withReasoning_defaultsCachedTokensToZero() {
        LlmClient.ChatResponse resp = new LlmClient.ChatResponse(
                "assistant", "hi", "reasoning", null, 100, 50);
        assertEquals(0, resp.cachedTokens());
        assertEquals("reasoning", resp.reasoningContent());
        assertEquals(100, resp.inputTokens());
        assertEquals(50, resp.outputTokens());
    }

    // ---- ToolCall and Tool records ----

    @Test
    void toolCall_recordAccessors() {
        LlmClient.ToolCall.Function fn = new LlmClient.ToolCall.Function("doIt", "{\"x\":1}");
        LlmClient.ToolCall tc = new LlmClient.ToolCall("id-1", fn);
        assertEquals("id-1", tc.id());
        assertEquals("doIt", tc.function().name());
        assertEquals("{\"x\":1}", tc.function().arguments());
    }

    @Test
    void tool_recordAccessors() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        LlmClient.Tool tool = new LlmClient.Tool("search", "Search the web", params);
        assertEquals("search", tool.name());
        assertEquals("Search the web", tool.description());
        assertNotNull(tool.parameters());
        assertEquals("object", tool.parameters().path("type").asText());
    }

    // ---- StreamListener.NO_OP ----

    @Test
    void streamListener_noOp_doesNotThrow() {
        LlmClient.StreamListener noOp = LlmClient.StreamListener.NO_OP;
        assertDoesNotThrow(() -> noOp.onReasoningDelta("delta"));
        assertDoesNotThrow(() -> noOp.onContentDelta("delta"));
    }

    // ---- Default interface methods ----

    @Test
    void defaultMaxContextWindow_returns128000() {
        LlmClient client = new LlmClient() {
            @Override
            public LlmClient.ChatResponse chat(java.util.List<LlmClient.Message> messages,
                                                java.util.List<LlmClient.Tool> tools) { return null; }
            @Override
            public LlmClient.ChatResponse chat(java.util.List<LlmClient.Message> messages,
                                                java.util.List<LlmClient.Tool> tools,
                                                LlmClient.StreamListener listener) { return null; }
            @Override
            public String getModelName() { return "test"; }
            @Override
            public String getProviderName() { return "test"; }
        };
        assertEquals(128_000, client.maxContextWindow());
        assertFalse(client.supportsPromptCaching());
    }
}
