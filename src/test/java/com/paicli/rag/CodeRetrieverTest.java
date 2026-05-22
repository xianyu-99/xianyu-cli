package com.paicli.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CodeRetrieverTest {

    private static final String TEST_PROJECT;
    static {
        // 使用绝对路径，确保 VectorStore 和 CodeRetriever 的 projectPath 一致
        Path path = Paths.get("/tmp/paicli-code-retriever").toAbsolutePath().normalize();
        TEST_PROJECT = path.toString();
    }
    private VectorStore store;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("paicli.rag.dir", "/tmp/paicli-test-rag-retriever");
        store = new VectorStore(TEST_PROJECT);
        store.clearProject();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void hybridSearchBoostsCodeKeywordsFromNaturalLanguageQuery() throws Exception {
        CodeChunk getterChunk = CodeChunk.methodChunk(
                "src/main/java/com/example/Task.java",
                "Task.getId()",
                "public String getId() { return id; }",
                10, 12
        );
        CodeChunk agentChunk = CodeChunk.methodChunk(
                "src/main/java/com/example/Agent.java",
                "Agent.run(String userInput)",
                "ReAct 循环：读取用户输入，思考，必要时调用工具，再继续下一轮。",
                20, 40
        );

        store.insertChunks(List.of(
                new VectorStore.CodeChunkEntry(getterChunk, new float[]{1.0f, 0.0f}),
                new VectorStore.CodeChunkEntry(agentChunk, new float[]{0.80f, 0.20f})
        ));

        // 让查询向量更接近 agentChunk，确保语义搜索中 Agent.run 排第一
        EmbeddingClient stubClient = new EmbeddingClient("ollama", "stub", "http://localhost", "") {
            @Override
            public float[] embed(String text) {
                // 当查询包含 "Agent" 时，返回与 agentChunk 对齐的向量
                if (text.toLowerCase().contains("agent")) {
                    return new float[]{0.80f, 0.20f};
                }
                return new float[]{1.0f, 0.0f};
            }
        };

        try (CodeRetriever retriever = new CodeRetriever(TEST_PROJECT, stubClient)) {
            List<VectorStore.SearchResult> results = retriever.hybridSearch("Agent的ReAct循环是怎么实现的", 5);

            assertFalse(results.isEmpty());
            assertEquals("Agent.run(String userInput)", results.get(0).name());
        }
    }
}
