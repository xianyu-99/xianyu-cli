package com.yucli.tui;

import com.googlecode.lanterna.gui2.Label;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TUI 上下文：共享状态与事件总线。
 */
public class TuiContext {

    private String modelName = "glm-5.1";
    private String modeName = "ReAct";
    private Path selectedFile;
    private final List<String> chatHistory = new ArrayList<>();

    private int activeTabIndex = 0;
    private Label modelLabel;
    private Label modeLabel;

    private final List<Consumer<String>> tabSwitchListeners = new ArrayList<>();
    private final List<Consumer<String>> actionListeners = new ArrayList<>();

    public String getModelName() { return modelName; }
    public void setModelName(String name) {
        this.modelName = name;
        if (modelLabel != null) modelLabel.setText(" 模型: " + name + " ");
    }

    public String getModeName() { return modeName; }
    public void setModeName(String mode) {
        this.modeName = mode;
        if (modeLabel != null) modeLabel.setText(" 模式: " + mode + " ");
    }

    public Path getSelectedFile() { return selectedFile; }
    public void setSelectedFile(Path file) { this.selectedFile = file; }

    public List<String> getChatHistory() { return chatHistory; }
    public void addChatMessage(String role, String text) {
        chatHistory.add("[" + role + "] " + text);
    }

    public int getActiveTabIndex() { return activeTabIndex; }
    public void setActiveTabIndex(int idx) { this.activeTabIndex = idx; }

    public void setStatusLabel(Label model, Label mode) {
        this.modelLabel = model;
        this.modeLabel = mode;
    }

    public void fireTabSwitch(String tabName) {
        activeTabIndex = switch (tabName) {
            case "chat" -> 0;
            case "code" -> 1;
            case "config" -> 2;
            default -> activeTabIndex;
        };
        for (Consumer<String> l : tabSwitchListeners) l.accept(tabName);
    }

    public void fireAction(String action) {
        for (Consumer<String> l : actionListeners) l.accept(action);
    }

    public void onTabSwitch(Consumer<String> listener) {
        tabSwitchListeners.add(listener);
    }

    public void onAction(Consumer<String> listener) {
        actionListeners.add(listener);
    }
}
