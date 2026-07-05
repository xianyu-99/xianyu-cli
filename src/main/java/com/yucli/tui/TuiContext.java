package com.yucli.tui;

import com.googlecode.lanterna.gui2.Label;
import com.yucli.agent.Agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TUI 上下文：共享状态与事件总线。
 *
 * 当前布局固定为左侧 ChatPanel，右侧通过 fileTree/code/config 三个 Tab 切换内容。
 */
public class TuiContext {

    public static final String TAB_FILE_TREE = "fileTree";
    public static final String TAB_CODE = "code";
    public static final String TAB_CONFIG = "config";

    private String modelName = "glm-5.1";
    private String modeName = "ReAct";
    private Path selectedFile;
    private final List<String> chatHistory = new ArrayList<>();
    private Agent agent;

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

    public String getActiveTabName() {
        return switch (activeTabIndex) {
            case 0 -> TAB_FILE_TREE;
            case 1 -> TAB_CODE;
            case 2 -> TAB_CONFIG;
            default -> TAB_FILE_TREE;
        };
    }

    public void fireTabSwitch(String tabName) {
        Integer nextIndex = tabIndexFor(tabName);
        if (nextIndex == null) {
            return;
        }
        activeTabIndex = nextIndex;
        for (Consumer<String> l : tabSwitchListeners) l.accept(tabName);
    }

    static Integer tabIndexFor(String tabName) {
        if (tabName == null) {
            return null;
        }
        return switch (tabName) {
            case TAB_FILE_TREE -> 0;
            case TAB_CODE -> 1;
            case TAB_CONFIG -> 2;
            default -> null;
        };
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

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }
}
