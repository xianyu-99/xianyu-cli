package com.yucli.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    @Test
    void defaultConstructorInitializesEmptyMessages() {
        Session session = new Session();
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    void parameterizedConstructorSetsIdAndTimestamps() {
        long now = 1700000000000L;
        Session session = new Session("abc-123", now);
        assertEquals("abc-123", session.getSessionId());
        assertEquals(now, session.getCreatedAt());
        assertEquals(now, session.getUpdatedAt());
        assertNotNull(session.getMessages());
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    void sessionIdGetterSetter() {
        Session session = new Session();
        assertNull(session.getSessionId());
        session.setSessionId("new-id");
        assertEquals("new-id", session.getSessionId());
    }

    @Test
    void createdAtGetterSetter() {
        Session session = new Session();
        assertEquals(0, session.getCreatedAt());
        session.setCreatedAt(12345L);
        assertEquals(12345L, session.getCreatedAt());
    }

    @Test
    void updatedAtGetterSetter() {
        Session session = new Session();
        assertEquals(0, session.getUpdatedAt());
        session.setUpdatedAt(99999L);
        assertEquals(99999L, session.getUpdatedAt());
    }

    @Test
    void modelNameGetterSetter() {
        Session session = new Session();
        assertNull(session.getModelName());
        session.setModelName("glm-4");
        assertEquals("glm-4", session.getModelName());
    }

    @Test
    void providerGetterSetter() {
        Session session = new Session();
        assertNull(session.getProvider());
        session.setProvider("zhipu");
        assertEquals("zhipu", session.getProvider());
    }

    @Test
    void taskSummaryGetterSetter() {
        Session session = new Session();
        assertNull(session.getTaskSummary());
        session.setTaskSummary("summarize code");
        assertEquals("summarize code", session.getTaskSummary());
    }

    @Test
    void totalTokensGetterSetter() {
        Session session = new Session();
        assertEquals(0, session.getTotalTokens());
        session.setTotalTokens(500);
        assertEquals(500, session.getTotalTokens());
    }

    @Test
    void messagesGetterSetter() {
        Session session = new Session();
        List<SessionMessage> msgs = new ArrayList<>();
        msgs.add(new SessionMessage("user", "hi", 1000L, 2));
        session.setMessages(msgs);
        assertEquals(1, session.getMessages().size());
        assertEquals("hi", session.getMessages().get(0).getContent());
    }

    @Test
    void addMessageAppendsToList() {
        Session session = new Session();
        SessionMessage msg1 = new SessionMessage("user", "first", 1000L, 5);
        SessionMessage msg2 = new SessionMessage("assistant", "second", 2000L, 8);

        session.addMessage(msg1);
        assertEquals(1, session.getMessages().size());

        session.addMessage(msg2);
        assertEquals(2, session.getMessages().size());
        assertEquals("first", session.getMessages().get(0).getContent());
        assertEquals("second", session.getMessages().get(1).getContent());
    }

    @Test
    void addMessageUpdatesTotalTokens() {
        Session session = new Session();
        session.addMessage(new SessionMessage("user", "hello", 1000L, 10));
        assertEquals(10, session.getTotalTokens());

        session.addMessage(new SessionMessage("assistant", "world", 2000L, 20));
        assertEquals(30, session.getTotalTokens());
    }

    @Test
    void addMessageUpdatesTimestamp() {
        Session session = new Session("id", 1000L);
        assertEquals(1000L, session.getUpdatedAt());

        // addMessage calls System.currentTimeMillis(), so updatedAt should increase
        long before = System.currentTimeMillis();
        session.addMessage(new SessionMessage("user", "msg", 1000L, 5));
        long after = System.currentTimeMillis();

        assertTrue(session.getUpdatedAt() >= before);
        assertTrue(session.getUpdatedAt() <= after);
    }

    @Test
    void messagesListIsMutable() {
        Session session = new Session();
        session.getMessages().add(new SessionMessage("user", "direct add", 1000L, 3));
        assertEquals(1, session.getMessages().size());

        session.getMessages().remove(0);
        assertTrue(session.getMessages().isEmpty());
    }

    @Test
    void getShortIdReturnsFirstEightChars() {
        Session session = new Session();
        session.setSessionId("abcdefgh12345678");
        assertEquals("abcdefgh", session.getShortId());
    }

    @Test
    void getShortIdReturnsFullIdIfShort() {
        Session session = new Session();
        session.setSessionId("short");
        assertEquals("short", session.getShortId());
    }

    @Test
    void getShortIdReturnsNullIfNoId() {
        Session session = new Session();
        assertNull(session.getShortId());
    }
}
