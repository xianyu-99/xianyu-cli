package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;

import java.util.List;

/**
 * 对话面板：展示聊天历史与输入框。
 */
public class ChatPanel {

    private final TuiContext context;
    private final Panel panel;
    private final Panel historyPanel;
    private final TextBox inputBox;

    public ChatPanel(TuiContext context) {
        this.context = context;
        this.panel = new Panel(new BorderLayout());

        // 历史消息区域
        this.historyPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        historyPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        Panel historyContainer = new Panel(new BorderLayout());
        historyContainer.addComponent(historyPanel, BorderLayout.Location.CENTER);
        panel.addComponent(historyContainer, BorderLayout.Location.CENTER);

        // 输入区域
        Panel inputArea = new Panel(new BorderLayout());
        this.inputBox = new TextBox(new TerminalSize(60, 3));
        this.inputBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        this.inputBox.setCaretWarp(true);

        inputArea.addComponent(inputBox, BorderLayout.Location.CENTER);
        Button sendBtn = new Button("发送", () -> {
            String text = inputBox.getText().trim();
            if (!text.isEmpty()) {
                context.addChatMessage("user", text);
                context.fireAction("send:" + text);
                inputBox.setText("");
                refreshHistory();
            }
        });
        inputArea.addComponent(sendBtn, BorderLayout.Location.RIGHT);
        panel.addComponent(inputArea, BorderLayout.Location.BOTTOM);

        // 初始欢迎消息
        context.addChatMessage("system", "欢迎使用 YuCLI TUI！输入消息后点击发送。");
        refreshHistory();
    }

    public Component getComponent() {
        return panel;
    }

    /**
     * 刷新历史消息显示。
     */
    public void refreshHistory() {
        historyPanel.removeAllComponents();
        List<String> history = context.getChatHistory();
        int start = Math.max(0, history.size() - 100); // 只显示最近 100 条
        for (int i = start; i < history.size(); i++) {
            String msg = history.get(i);
            Label label = createMessageLabel(msg);
            historyPanel.addComponent(label);
        }
        historyPanel.invalidate();
    }

    /**
     * Agent 回复后追加到历史并刷新。
     */
    public void appendAgentMessage(String text) {
        context.addChatMessage("agent", text);
        refreshHistory();
    }

    /**
     * 获取输入框当前内容。
     */
    public String getInputText() {
        return inputBox.getText();
    }

    private Label createMessageLabel(String msg) {
        Label label = new Label(msg);
        if (msg.startsWith("[user]")) {
            label.setForegroundColor(TextColor.ANSI.CYAN);
        } else if (msg.startsWith("[agent]")) {
            label.setForegroundColor(TextColor.ANSI.GREEN);
        } else if (msg.startsWith("[system]")) {
            label.setForegroundColor(TextColor.ANSI.YELLOW);
        }
        label.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning));
        return label;
    }
}
