package com.yucli.mcp.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.mcp.transport.McpTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void pairsResponseByNumericId() throws Exception {
        LoopbackTransport transport = new LoopbackTransport("""
                {"jsonrpc":"2.0","id":1,"result":{"ok":true}}
                """);
        JsonRpcClient client = new JsonRpcClient(transport);

        JsonNode result = client.request("ping", MAPPER.createObjectNode(), 1);

        assertTrue(result.path("ok").asBoolean());
        assertTrue(transport.sent.path("id").isNumber());
    }

    @Test
    void mapsJsonRpcErrorToException() {
        LoopbackTransport transport = new LoopbackTransport("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"missing"}}
                """);
        JsonRpcClient client = new JsonRpcClient(transport);

        JsonRpcException error = assertThrows(JsonRpcException.class,
                () -> client.request("missing", MAPPER.createObjectNode(), 1));
        assertEquals(-32601, error.code());
    }

    @Test
    void closeCompletesPendingRequestExceptionally() throws Exception {
        SilentTransport transport = new SilentTransport();
        JsonRpcClient client = new JsonRpcClient(transport);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "test-jsonrpc-pending-request");
            thread.setDaemon(true);
            return thread;
        });

        Future<JsonNode> request = executor.submit(() -> client.request("hang", MAPPER.createObjectNode(), 30));

        try {
            assertTrue(transport.awaitSent(), "request should be sent before closing the client");

            client.close();

            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> request.get(300, TimeUnit.MILLISECONDS));
            assertInstanceOf(IOException.class, error.getCause());
        } finally {
            request.cancel(true);
            executor.shutdownNow();
        }
    }

    @Test
    void requestAfterCloseFailsImmediatelyWithoutSending() {
        SilentTransport transport = new SilentTransport();
        JsonRpcClient client = new JsonRpcClient(transport);
        client.close();

        IOException error = assertThrows(IOException.class,
                () -> client.request("late", MAPPER.createObjectNode(), 30));

        assertTrue(error.getMessage().contains("closed"));
        assertEquals(0, transport.sendCount());
    }

    private static final class LoopbackTransport implements McpTransport {
        private final String response;
        private Consumer<JsonNode> listener;
        private JsonNode sent;

        private LoopbackTransport(String response) {
            this.response = response;
        }

        @Override
        public void send(JsonNode message) throws IOException {
            sent = message;
            listener.accept(MAPPER.readTree(response));
        }

        @Override
        public void onReceive(Consumer<JsonNode> listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
        }
    }

    private static final class SilentTransport implements McpTransport {
        private final CountDownLatch sent = new CountDownLatch(1);
        private final AtomicInteger sendCount = new AtomicInteger();

        private boolean awaitSent() throws InterruptedException {
            return sent.await(1, TimeUnit.SECONDS);
        }

        private int sendCount() {
            return sendCount.get();
        }

        @Override
        public void send(JsonNode message) {
            sendCount.incrementAndGet();
            sent.countDown();
        }

        @Override
        public void onReceive(Consumer<JsonNode> listener) {
        }

        @Override
        public void close() {
        }
    }
}
