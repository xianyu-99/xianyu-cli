package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiApplicationTest {

    @Test
    void windowTitleUsesCurrentProductVersion() {
        assertEquals("YuCLI TUI v19.0.0", TuiApplication.windowTitle());
    }

    @Test
    void chatPanelShutdownStopsBackgroundExecutor() {
        ChatPanel chatPanel = new ChatPanel(new TuiContext());

        assertFalse(chatPanel.isShutdown());
        chatPanel.shutdown();

        assertTrue(chatPanel.isShutdown());
    }

    @Test
    void sendActionSubmitsChatInput() {
        TuiContext context = new TuiContext();
        ChatPanel chatPanel = new ChatPanel(context);
        chatPanel.setInputText("hello");

        context.fireAction("send");

        assertEquals("", chatPanel.getInputText());
        assertTrue(context.getChatHistory().contains("[user] hello"));
        assertTrue(context.getChatHistory().stream()
                .anyMatch(message -> message.contains("Agent 未初始化")));
    }

    @Test
    void stopStopsUnderlyingScreen() throws IOException {
        TrackingScreen screen = new TrackingScreen();
        TuiApplication app = new TuiApplication(null, screen);

        assertTrue(screen.started);
        assertFalse(screen.stopped);

        app.stop();

        assertTrue(screen.stopped);
    }

    private static final class TrackingScreen extends TerminalScreen {
        private boolean started;
        private boolean stopped;

        private TrackingScreen() throws IOException {
            super(new DefaultVirtualTerminal(new TerminalSize(80, 24)));
        }

        @Override
        public synchronized void startScreen() throws IOException {
            started = true;
            super.startScreen();
        }

        @Override
        public void stopScreen() throws IOException {
            stopped = true;
            super.stopScreen();
        }
    }

    @Test
    void openFileActionSwitchesRightPaneToCodeTab(@TempDir Path tempDir) throws IOException {
        TrackingScreen screen = new TrackingScreen();
        TuiApplication app = new TuiApplication(null, screen);
        Path file = Files.writeString(tempDir.resolve("Sample.java"), "class Sample {}");

        try {
            assertEquals(TuiContext.TAB_FILE_TREE, app.getActiveRightTabName());

            app.getContext().fireAction("open_file:" + file.toAbsolutePath());

            assertEquals(TuiContext.TAB_CODE, app.getActiveRightTabName());
            assertEquals(file.toAbsolutePath(), app.getContext().getSelectedFile());
            assertFalse(app.isFocusOnLeftPane(), "open_file should leave focus on the right pane");
        } finally {
            app.stop();
        }
    }

    @Test
    void ctrlTogglesFocusBetweenLeftChatAndActiveRightTab() throws IOException {
        TrackingScreen screen = new TrackingScreen();
        TuiApplication app = new TuiApplication(null, screen);

        try {
            assertTrue(app.isFocusOnLeftPane());

            app.handleInput(new KeyStroke('t', true, false));
            assertFalse(app.isFocusOnLeftPane(), "Ctrl+T should move focus to the right pane");
            assertEquals(TuiContext.TAB_FILE_TREE, app.getActiveRightTabName());

            app.handleInput(new KeyStroke(com.googlecode.lanterna.input.KeyType.F2));
            assertFalse(app.isFocusOnLeftPane(), "Switching right tabs should preserve right-pane focus");
            assertEquals(TuiContext.TAB_CODE, app.getActiveRightTabName());

            app.handleInput(new KeyStroke('t', true, false));
            assertTrue(app.isFocusOnLeftPane(), "Ctrl+T should move focus back to the left chat pane");
            assertEquals(TuiContext.TAB_CODE, app.getActiveRightTabName());
        } finally {
            app.stop();
        }
    }
}
