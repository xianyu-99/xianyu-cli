package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdpSessionCleanDomTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getCleanDomUsesBrowserSideCleanerAndMaxLength() throws Exception {
        RecordingWebSocketClient client = RecordingWebSocketClient.returning("<body><main>clean</main></body>");
        CdpSession session = new CdpSession(client);

        String result = session.getCleanDom(1234);

        assertEquals("<body><main>clean</main></body>", result);
        assertEquals("Runtime.evaluate", client.lastMethod);
        assertTrue(client.lastParams.path("returnByValue").asBoolean());
        assertTrue(client.lastParams.path("awaitPromise").asBoolean());

        String script = client.lastParams.path("expression").asText();
        assertTrue(script.contains("})(1234);"));
        assertTrue(script.contains("parts.push(text.slice(0, remaining))"));
        assertTrue(script.contains("truncated, total exceeds"));
        assertTrue(script.contains("name === \"class\" || name === \"style\" || name.startsWith(\"on\")"));
        assertTrue(script.contains("\"SCRIPT\""));
        assertTrue(script.contains("\"IFRAME\""));
        assertFalse(script.contains("document.documentElement.outerHTML"));
    }

    @Test
    void getCleanDomClampsNegativeMaxLengthBeforeBuildingScript() throws Exception {
        RecordingWebSocketClient client = RecordingWebSocketClient.returning("");
        CdpSession session = new CdpSession(client);

        session.getCleanDom(-1);

        String script = client.lastParams.path("expression").asText();
        assertTrue(script.contains("})(0);"));
    }

    @Test
    void getCleanDomReportsRuntimeEvaluateException() {
        RecordingWebSocketClient client = RecordingWebSocketClient.throwingScriptException("ReferenceError");
        CdpSession session = new CdpSession(client);

        RuntimeException error = assertThrows(RuntimeException.class, () -> session.getCleanDom(8000));

        assertTrue(error.getMessage().contains("DOM 摘要脚本执行失败"));
        assertTrue(error.getMessage().contains("ReferenceError"));
    }

    private static class RecordingWebSocketClient extends CdpWebSocketClient {
        private final JsonNode response;
        private String lastMethod;
        private ObjectNode lastParams;

        private RecordingWebSocketClient(JsonNode response) {
            this.response = response;
        }

        static RecordingWebSocketClient returning(String value) {
            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode runtimeResult = MAPPER.createObjectNode();
            runtimeResult.put("type", "string");
            runtimeResult.put("value", value);
            response.set("result", runtimeResult);
            return new RecordingWebSocketClient(response);
        }

        static RecordingWebSocketClient throwingScriptException(String message) {
            ObjectNode response = MAPPER.createObjectNode();
            response.set("result", MAPPER.createObjectNode().put("type", "undefined"));
            response.set("exceptionDetails", MAPPER.createObjectNode().put("text", message));
            return new RecordingWebSocketClient(response);
        }

        @Override
        public JsonNode sendSync(String method, ObjectNode params) {
            this.lastMethod = method;
            this.lastParams = params;
            return response;
        }
    }
}
