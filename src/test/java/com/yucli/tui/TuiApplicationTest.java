package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
}
