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
        assertEquals(TuiContext.TAB_FILE_TREE, ctx.getActiveTabName());
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

        ctx.fireTabSwitch(TuiContext.TAB_CODE);
        assertEquals(1, fired.size());
        assertEquals(TuiContext.TAB_CODE, fired.get(0));
        assertEquals(1, ctx.getActiveTabIndex());

        ctx.fireTabSwitch(TuiContext.TAB_CONFIG);
        assertEquals(2, ctx.getActiveTabIndex());
    }

    @Test
    void activeTabNameTracksRightPaneTabs() {
        TuiContext ctx = new TuiContext();

        assertEquals(TuiContext.TAB_FILE_TREE, ctx.getActiveTabName());

        ctx.fireTabSwitch(TuiContext.TAB_CODE);
        assertEquals(TuiContext.TAB_CODE, ctx.getActiveTabName());

        ctx.fireTabSwitch(TuiContext.TAB_CONFIG);
        assertEquals(TuiContext.TAB_CONFIG, ctx.getActiveTabName());

        ctx.fireTabSwitch(TuiContext.TAB_FILE_TREE);
        assertEquals(TuiContext.TAB_FILE_TREE, ctx.getActiveTabName());
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

        ctx.fireTabSwitch(TuiContext.TAB_FILE_TREE);
        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }
}
