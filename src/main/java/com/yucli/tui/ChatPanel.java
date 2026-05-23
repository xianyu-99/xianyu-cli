package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.yucli.agent.Agent;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话面板：展示聊天历史与输入框。
 */
public class ChatPanel {

    private final TuiContext context;
    private final Panel panel;
    private final Panel historyPanel;
    private final TextBox inputBox;
    private final ExecutorService executor;

    public ChatPanel(TuiContext context) {
        this.context = context;
        this.panel = new Panel(new BorderLayout());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "YuCLI-tui-agent-runner");
            t.setDaemon(true);
            return t;
        });

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
        Button sendBtn = new Button("发送", this::doSend);
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

    /**
     * 发送用户输入并在后台线程中调用 Agent。
     */
    private void doSend() {
        String text = inputBox.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        context.addChatMessage("user", text);
        context.fireAction("send:" + text);
        inputBox.setText("");
        refreshHistory();

        Agent agent = context.getAgent();
        if (agent == null) {
            appendAgentMessage("❌ Agent 未初始化，无法处理请求。");
            return;
        }

        // 禁用输入，防止重复发送
        inputBox.setEnabled(false);

        executor.submit(() -> {
            try {
                String response = agent.run(text);
                // 回到 UI 线程更新界面
                panel.getTextGUI().getGUIThread().invokeLater(() -> {
                    appendAgentMessage(response);
                    inputBox.setEnabled(true);
                });
            } catch (Exception e) {
                panel.getTextGUI().getGUIThread().invokeLater(() -> {
                    appendAgentMessage("❌ Agent 执行异常: " + e.getMessage());
                    inputBox.setEnabled(true);
                });
            }
        });
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
