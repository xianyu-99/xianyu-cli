package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置面板：管理 YuCLI 运行时配置与模型切换。
 */
public class ConfigPanel {

    private final TuiContext context;
    private final Panel panel;
    private final TextBox modelInput;
    private final TextBox modeInput;
    private final Label statusLabel;

    public ConfigPanel(TuiContext context) {
        this.context = context;
        this.panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

        // 标题
        panel.addComponent(new Label("YuCLI 配置"));
        panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // 模型设置
        panel.addComponent(new Label("当前模型:"));
        this.modelInput = new TextBox(new TerminalSize(40, 1));
        this.modelInput.setText(context.getModelName());
        panel.addComponent(modelInput);
        panel.addComponent(new Button("切换模型", this::switchModel));
        panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // 模式设置
        panel.addComponent(new Label("当前模式 (ReAct / Plan / Team):"));
        this.modeInput = new TextBox(new TerminalSize(40, 1));
        this.modeInput.setText(context.getModeName());
        panel.addComponent(modeInput);
        panel.addComponent(new Button("切换模式", this::switchMode));
        panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // 状态显示
        this.statusLabel = new Label("状态: 就绪");
        statusLabel.setForegroundColor(TextColor.ANSI.GREEN);
        panel.addComponent(statusLabel);
        panel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // 快捷操作
        panel.addComponent(new Label("快捷操作:"));
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actionPanel.addComponent(new Button("清空历史", () -> {
            context.getChatHistory().clear();
            setStatus("对话历史已清空");
        }));
        actionPanel.addComponent(new Button("打开 .env", this::openEnvFile));
        panel.addComponent(actionPanel);
    }

    public Component getComponent() {
        return panel;
    }

    private void switchModel() {
        String model = modelInput.getText().trim();
        if (!model.isEmpty()) {
            context.setModelName(model);
            context.fireAction("switch_model:" + model);
            setStatus("模型已切换: " + model);
        }
    }

    private void switchMode() {
        String mode = modeInput.getText().trim();
        if (!mode.isEmpty()) {
            context.setModeName(mode);
            context.fireAction("switch_mode:" + mode);
            setStatus("模式已切换: " + mode);
        }
    }

    private void openEnvFile() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            envPath = Path.of(System.getProperty("user.home"), ".env");
        }
        if (Files.exists(envPath)) {
            context.setSelectedFile(envPath);
            context.fireAction("open_file:" + envPath.toAbsolutePath());
            setStatus("已打开: " + envPath);
        } else {
            setStatus("未找到 .env 文件");
        }
    }

    private void setStatus(String message) {
        statusLabel.setText("状态: " + message);
    }

    /**
     * 获取模型输入框内容。
     */
    public String getModelInput() {
        return modelInput.getText();
    }

    /**
     * 获取模式输入框内容。
     */
    public String getModeInput() {
        return modeInput.getText();
    }
}
