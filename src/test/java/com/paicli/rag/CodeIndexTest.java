package com.paicli.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeIndexTest {

    @Test
    void testIndexNonExistentPath() {
        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("/non/existent/path");
        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("路径不存在"));
    }

    @Test
    void testIndexCurrentProject() {
        System.setProperty("paicli.rag.dir", "/tmp/paicli-test-rag-index");
        // 使用 stub embedding client，避免依赖 Ollama
        EmbeddingClient stubClient = new EmbeddingClient("stub", "stub", "http://localhost", "") {
            @Override
            public float[] embed(String text) {
                return new float[]{1.0f, 0.0f, 0.0f, 0.0f};
            }
        };
        CodeIndex indexer = new CodeIndex(stubClient);
        // 索引测试资源目录
        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");
        assertTrue(result.chunkCount() > 0, "应该至少索引一个代码块");
        assertTrue(result.message().contains("索引完成"));
    }
}
