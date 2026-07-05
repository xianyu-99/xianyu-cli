package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdpSessionScreenshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void selectorScreenshotUsesElementClip() throws Exception {
        RecordingWebSocketClient client = new RecordingWebSocketClient();
        CdpSession session = new CdpSession(client);

        String data = session.captureScreenshot("#target", false);

        assertEquals("png-base64", data);
        assertTrue(client.lastEvaluateScript.contains("document.querySelector('#target')"));
        assertTrue(client.lastEvaluateScript.contains("scrollIntoView"));
        assertEquals("Page.captureScreenshot", client.lastMethod);
        assertTrue(client.captureParams.path("captureBeyondViewport").asBoolean());

        JsonNode clip = client.captureParams.path("clip");
        assertEquals(12.5, clip.path("x").asDouble());
        assertEquals(34.25, clip.path("y").asDouble());
        assertEquals(120.0, clip.path("width").asDouble());
        assertEquals(44.0, clip.path("height").asDouble());
        assertEquals(1.0, clip.path("scale").asDouble());
    }

    @Test
    void selectorScreenshotFailsWhenElementIsMissing() {
        RecordingWebSocketClient client = new RecordingWebSocketClient();
        client.elementValue = NullNode.getInstance();
        CdpSession session = new CdpSession(client);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> session.captureScreenshot("#missing", false));

        assertTrue(error.getMessage().contains("未找到元素: #missing"));
    }

    @Test
    void fullPageScreenshotStillUsesContentSizeClip() throws Exception {
        RecordingWebSocketClient client = new RecordingWebSocketClient();
        CdpSession session = new CdpSession(client);

        session.captureScreenshot(null, true);

        JsonNode clip = client.captureParams.path("clip");
        assertEquals(0, clip.path("x").asInt());
        assertEquals(0, clip.path("y").asInt());
        assertEquals(1440.0, clip.path("width").asDouble());
        assertEquals(2200.0, clip.path("height").asDouble());
    }

    private static class RecordingWebSocketClient extends CdpWebSocketClient {
        private JsonNode elementValue = elementRect();
        private String lastMethod;
        private String lastEvaluateScript;
        private ObjectNode captureParams;

        @Override
        public JsonNode sendSync(String method, ObjectNode params) {
            lastMethod = method;
            if ("Runtime.evaluate".equals(method)) {
                lastEvaluateScript = params.path("expression").asText();
                ObjectNode response = MAPPER.createObjectNode();
                ObjectNode result = MAPPER.createObjectNode();
                result.set("value", elementValue);
                response.set("result", result);
                return response;
            }
            if ("Page.getLayoutMetrics".equals(method)) {
                ObjectNode response = MAPPER.createObjectNode();
                response.set("contentSize", MAPPER.createObjectNode()
                        .put("width", 1440)
                        .put("height", 2200));
                return response;
            }
            if ("Page.captureScreenshot".equals(method)) {
                captureParams = params;
                return MAPPER.createObjectNode().put("data", "png-base64");
            }
            throw new AssertionError("Unexpected method: " + method);
        }

        private static ObjectNode elementRect() {
            return MAPPER.createObjectNode()
                    .put("x", 12.5)
                    .put("y", 34.25)
                    .put("width", 120)
                    .put("height", 44);
        }
    }
}
