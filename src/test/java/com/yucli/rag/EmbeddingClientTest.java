package com.yucli.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingClientTest {

    @TempDir
    Path tempDir;

    @Test
    void testDefaultConfiguration() {
        EmbeddingClient client = new EmbeddingClient();
        assertEquals("ollama", client.getProvider());
        assertEquals("nomic-embed-text:latest", client.getModel());
    }

    @Test
    void testCustomConfiguration() {
        EmbeddingClient client = new EmbeddingClient("zhipu", "embedding-3",
                "https://open.bigmodel.cn/api/paas/v4", "test-key");
        assertEquals("zhipu", client.getProvider());
        assertEquals("embedding-3", client.getModel());
    }

    @Test
    void testEmptyInputReturnsEmptyArray() throws Exception {
        EmbeddingClient client = new EmbeddingClient();
        assertEquals(0, client.embed("").length);
        assertEquals(0, client.embed(null).length);
    }

    @Test
    void readsEmbeddingConfigFromDotEnv() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
                EMBEDDING_PROVIDER=zhipu
                EMBEDDING_MODEL=embedding-3
                EMBEDDING_BASE_URL=https://example.test/api
                EMBEDDING_API_KEY=dotenv-key
                """);
        System.setProperty("YuCLI.env.dir", tempDir.toString());
        try {
            EmbeddingClient client = new EmbeddingClient();

            assertEquals("zhipu", client.getProvider());
            assertEquals("embedding-3", client.getModel());
            assertEquals("https://example.test/api", client.getBaseUrl());
            assertEquals("dotenv-key", client.getApiKey());
        } finally {
            System.clearProperty("YuCLI.env.dir");
        }
    }
}
