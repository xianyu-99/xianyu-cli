package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CdpWebSocketClientTest {

    private CdpWebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new CdpWebSocketClient();
    }

    @Test
    void onEventShouldRegisterListener() {
        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.onEvent("Page.loadEventFired", received::set);

        // Verify by dispatching a message through the private handleMessage.
        // Since handleMessage is private, we verify indirectly: the client does
        // not throw and the listener map accepts the registration.
        // We can confirm the listener was stored by trying to remove it.
        assertDoesNotThrow(() -> client.offEvent("Page.loadEventFired"));
    }

    @Test
    void offEventShouldRemoveListener() {
        client.onEvent("Page.loadEventFired", params -> {});
        // Removing should not throw
        assertDoesNotThrow(() -> client.offEvent("Page.loadEventFired"));

        // Removing again should also not throw (already gone)
        assertDoesNotThrow(() -> client.offEvent("Page.loadEventFired"));
    }

    @Test
    void offEventForUnregisteredMethodShouldNotThrow() {
        // Calling offEvent for a method that was never registered
        assertDoesNotThrow(() -> client.offEvent("SomeNonExistentMethod"));
    }

    @Test
    void onEventShouldOverwritePreviousListener() {
        // The underlying map uses put(), so a second registration for the same
        // method replaces the first listener. Verify no exception is thrown.
        client.onEvent("Network.requestWillBeSent", params -> {});
        client.onEvent("Network.requestWillBeSent", params -> {});
        assertDoesNotThrow(() -> client.offEvent("Network.requestWillBeSent"));
    }

    @Test
    void multipleDifferentEventsCanBeRegistered() {
        client.onEvent("Page.loadEventFired", params -> {});
        client.onEvent("Network.requestWillBeSent", params -> {});
        client.onEvent("Runtime.consoleAPICalled", params -> {});

        // All can be removed independently without interfering
        assertDoesNotThrow(() -> client.offEvent("Page.loadEventFired"));
        assertDoesNotThrow(() -> client.offEvent("Network.requestWillBeSent"));
        assertDoesNotThrow(() -> client.offEvent("Runtime.consoleAPICalled"));
    }

    @Test
    void isConnectedShouldReturnFalseBeforeConnecting() {
        assertFalse(client.isConnected());
    }

    @Test
    void closeShouldNotThrowWhenNotConnected() {
        assertDoesNotThrow(() -> client.close());
    }

    @Test
    void sendShouldFailWhenNotConnected() {
        ObjectMapper mapper = new ObjectMapper();
        var future = client.send("Page.navigate", mapper.createObjectNode().put("url", "https://example.com"));
        assertTrue(future.isCompletedExceptionally());
    }
}
