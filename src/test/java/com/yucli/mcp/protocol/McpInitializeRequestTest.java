package com.yucli.mcp.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yucli.ProductInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpInitializeRequestTest {

    @Test
    void clientInfoUsesProductVersion() {
        ObjectNode json = McpInitializeRequest.toJson();

        assertEquals(ProductInfo.VERSION, json.path("clientInfo").path("version").asText());
    }
}
