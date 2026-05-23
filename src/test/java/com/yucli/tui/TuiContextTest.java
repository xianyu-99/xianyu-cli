package com.yucli.tui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TuiContextTest {

    @Test
    void defaultValues() {
        TuiContext ctx = new TuiContext();
        assertEquals("glm-5.1", ctx.getModelName());
        assertEquals("ReAct", ctx.getModeName());
        assertNull(ctx.getSelectedFile());
        assertTrue(ctx.getChatHistory().isEmpty());
        assertEquals(0, ctx.getActiveTabIndex());
    }

    @Test
    void setModelNameUpdatesState() {
        TuiContext ctx = new TuiContext();
        ctx.setModelName("deepseek-v4");
        assertEquals("deepseek-v4", ctx.getModelName());
    }

    @Test
    void setModeNameUpdatesState() {
        TuiContext ctx = new TuiContext();
        ctx.setModeName("Plan");
        assertEquals("Plan", ctx.getModeName());
    }

    @Test
    void selectedFileTracking() {
        TuiContext ctx = new TuiContext();
        Path file = Path.of("test.java");
        ctx.setSelectedFile(file);
        assertEquals(file, ctx.getSelectedFile());
    }

    @Test
    void chatHistoryAccumulates() {
        TuiContext ctx = new TuiContext();
        ctx.addChatMessage("user", "hello");
        ctx.addChatMessage("agent", "hi");
        List<String> history = ctx.getChatHistory();
        assertEquals(2, history.size());
        assertEquals("[user] hello", history.get(0));
        assertEquals("[agent] hi", history.get(1));
    }

    @Test
    void tabSwitchListenersFire() {
        TuiContext ctx = new TuiContext();
        List<String> fired = new ArrayList<>();
        ctx.onTabSwitch(fired::add);

        ctx.fireTabSwitch("code");
        assertEquals(1, fired.size());
        assertEquals("code", fired.get(0));
        assertEquals(1, ctx.getActiveTabIndex());

        ctx.fireTabSwitch("config");
        assertEquals(2, ctx.getActiveTabIndex());
    }

    @Test
    void actionListenersFire() {
        TuiContext ctx = new TuiContext();
        List<String> fired = new ArrayList<>();
        ctx.onAction(fired::add);

        ctx.fireAction("send:test");
        assertEquals(1, fired.size());
        assertEquals("send:test", fired.get(0));
    }

    @Test
    void multipleListenersSupported() {
        TuiContext ctx = new TuiContext();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        ctx.onTabSwitch(first::add);
        ctx.onTabSwitch(second::add);

        ctx.fireTabSwitch("chat");
        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }
}
