package com.yucli.tui;

import com.googlecode.lanterna.gui2.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 代码面板：展示文件内容。
 */
public class CodePanel {

    private final TuiContext context;
    private final Panel panel;
    private final TextBox codeArea;
    private final Label fileLabel;

    public CodePanel(TuiContext context) {
        this.context = context;
        this.panel = new Panel(new BorderLayout());

        // 顶部文件路径
        this.fileLabel = new Label("未选择文件");
        Panel header = new Panel(new LinearLayout(Direction.HORIZONTAL));
        header.addComponent(fileLabel);
        panel.addComponent(header, BorderLayout.Location.TOP);

        // 代码区域（只读）
        this.codeArea = new TextBox();
        this.codeArea.setReadOnly(true);
        this.codeArea.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(codeArea, BorderLayout.Location.CENTER);

        // 监听上下文文件变化
        context.onAction(action -> {
            if (action.startsWith("open_file:")) {
                String path = action.substring("open_file:".length());
                loadFile(Path.of(path));
            }
        });
    }

    public Component getComponent() {
        return panel;
    }

    /**
     * 加载并显示文件内容。
     */
    public void loadFile(Path file) {
        if (!Files.isRegularFile(file)) {
            fileLabel.setText("无效文件: " + file);
            codeArea.setText("");
            return;
        }

        try {
            String content = Files.readString(file);
            fileLabel.setText(file.getFileName().toString() + " (" + file.toAbsolutePath() + ")");
            codeArea.setText(content);
        } catch (IOException e) {
            fileLabel.setText("读取失败: " + file.getFileName());
            codeArea.setText("无法读取文件: " + e.getMessage());
        }
    }

    /**
     * 直接设置显示的代码内容。
     */
    public void setCode(String fileName, String code) {
        fileLabel.setText(fileName);
        codeArea.setText(code != null ? code : "");
    }

    /**
     * 获取当前显示的代码。
     */
    public String getCode() {
        return codeArea.getText();
    }

    /**
     * 获取当前显示的文件名。
     */
    public String getCurrentFileName() {
        return fileLabel.getText();
    }
}
