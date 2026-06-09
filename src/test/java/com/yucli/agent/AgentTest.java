package com.yucli.agent;

import com.yucli.llm.LlmClient;
import com.yucli.session.Session;
import com.yucli.session.SessionMessage;
import com.yucli.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    @Test
    void restoreSessionReplacesConversationHistoryUsedForNextRun() {
        CapturingLlmClient llm = new CapturingLlmClient();
        Agent agent = new Agent(llm, new ToolRegistry());

        agent.run("old-session-message");

        Session restored = new Session("s1", 1L);
        restored.addMessage(new SessionMessage("user", "restored-user-message", 1L, 1));
        restored.addMessage(new SessionMessage("assistant", "restored-assistant-message", 2L, 1));

        agent.restoreSession(restored);
        agent.run("continue");

        assertTrue(llm.lastMessages.stream().anyMatch(m ->
                "user".equals(m.role()) && "restored-user-message".equals(m.content())));
        assertTrue(llm.lastMessages.stream().anyMatch(m ->
                "assistant".equals(m.role()) && "restored-assistant-message".equals(m.content())));
        assertFalse(llm.lastMessages.stream().anyMatch(m ->
                "user".equals(m.role()) && "old-session-message".equals(m.content())));
    }

    private static final class CapturingLlmClient implements LlmClient {
        private List<Message> lastMessages = List.of();

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            lastMessages = List.copyOf(messages);
            return new ChatResponse("assistant", "ok", null, 1, 1);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "test-model";
        }

        @Override
        public String getProviderName() {
            return "test-provider";
        }
    }
}
