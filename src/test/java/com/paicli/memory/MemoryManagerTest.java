package com.paicli.memory;

import com.paicli.llm.GLMClient;
import com.paicli.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCompressBeforeShortTermMemoryEvictsOldEntries() {
        StubGLMClient llmClient = new StubGLMClient(List.of(
                new LlmClient.ChatResponse("assistant", "压缩摘要", null, 100, 20)
        ));
        MemoryManager memoryManager = new MemoryManager(
                llmClient,
                40,
                32000,
                new LongTermMemory(tempDir.toFile())
        );
        String longMessage = "a".repeat(36);

        memoryManager.addUserMessage(longMessage);
        memoryManager.addAssistantMessage(longMessage);
        memoryManager.addUserMessage(longMessage);
        memoryManager.addAssistantMessage(longMessage);

        assertTrue(memoryManager.getShortTermMemory().getAll().stream()
                .anyMatch(entry -> entry.getType() == MemoryEntry.MemoryType.SUMMARY));
    }

    @Test
    void shouldClearLongTermMemoryOnlyWhenExplicitlyRequested() {
        LongTermMemory longTermMemory = new LongTermMemory(tempDir.toFile());
        MemoryManager memoryManager = new MemoryManager(new StubGLMClient(List.of()), 32768, 128000, longTermMemory);

        memoryManager.storeFact("用户偏好使用中文交流");
        memoryManager.storeFact("项目路径: /tmp/demo");
        assertEquals(2, longTermMemory.size());

        memoryManager.clearLongTerm();

        assertEquals(0, longTermMemory.size());
    }

    @Test
    void shortContextModeBelow100k() {
        MemoryManager manager = new MemoryManager(new StubGLMClient(List.of()), 32768, 32000, null);
        assertEquals(MemoryManager.ContextMode.SHORT, manager.getContextMode());
    }

    @Test
    void longContextModeAt100kOrAbove() {
        MemoryManager manager = new MemoryManager(new StubGLMClient(List.of()), 32768, 100_000, null);
        assertEquals(MemoryManager.ContextMode.LONG, manager.getContextMode());

        MemoryManager manager2 = new MemoryManager(new StubGLMClient(List.of()), 32768, 1_000_000, null);
        assertEquals(MemoryManager.ContextMode.LONG, manager2.getContextMode());
    }

    @Test
    void longContextModeSkipsCompression() {
        StubGLMClient llmClient = new StubGLMClient(List.of(
                new LlmClient.ChatResponse("assistant", "压缩摘要", null, 100, 20)
        ));
        MemoryManager memoryManager = new MemoryManager(llmClient, 40, 100_000, null);
        String longMessage = "a".repeat(36);

        memoryManager.addUserMessage(longMessage);
        memoryManager.addAssistantMessage(longMessage);
        memoryManager.addUserMessage(longMessage);
        memoryManager.addAssistantMessage(longMessage);

        // LONG 模式下不压缩，所以不会有 SUMMARY 条目
        assertFalse(memoryManager.getShortTermMemory().getAll().stream()
                .anyMatch(entry -> entry.getType() == MemoryEntry.MemoryType.SUMMARY));
    }

    private static final class StubGLMClient extends GLMClient {
        private final Queue<ChatResponse> responses;

        private StubGLMClient(List<ChatResponse> responses) {
            super("test-key");
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            ChatResponse response = responses.poll();
            if (response == null) {
                throw new IOException("缺少预设响应");
            }
            return response;
        }
    }
}
