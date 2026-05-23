package com.yucli.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码面板：展示文件内容，支持基础语法高亮。
 *
 * Lanterna 3.1.1 的 TextBox 不支持富文本/逐字符着色，
 * 因此改用 Panel + 每行一个 Label 的方式实现。
 */
public class CodePanel {

    private static final int MAX_VISIBLE_LINES = 50;

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "public", "private", "protected", "static", "final",
            "class", "interface", "void", "return", "if", "else",
            "for", "while", "import", "package", "new", "this",
            "try", "catch", "throws", "true", "false", "null",
            "extends", "implements", "int", "long", "double", "float",
            "boolean", "char", "byte", "short", "super", "switch",
            "case", "default", "break", "continue", "do", "finally",
            "instanceof", "synchronized", "volatile", "transient", "abstract", "native",
            "strictfp", "const", "goto", "enum", "assert", "var"
    );

    private static final TextColor COLOR_KEYWORD = TextColor.ANSI.CYAN;
    private static final TextColor COLOR_STRING  = TextColor.ANSI.GREEN;
    private static final TextColor COLOR_COMMENT = TextColor.ANSI.WHITE;
    private static final TextColor COLOR_NUMBER  = TextColor.ANSI.YELLOW;
    private static final TextColor COLOR_DEFAULT = TextColor.ANSI.DEFAULT;

    // 匹配顺序：注释 > 字符串 > 数字 > 关键字 > 标识符/其他
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(//.*$)|(/\\*.*?\\*/)|" +           // 单行注释 //... 或 多行注释 /*...*/
            "(\"(?:[^\"\\\\]|\\\\.)*\")|" +     // 双引号字符串
            "('(?:[^'\\\\]|\\\\.)*')|" +          // 单引号字符/字符串
            "\\b(\\d+(?:\\.\\d+)?(?:[lLfFdD])?)\\b|" + // 数字
            "\\b([a-zA-Z_]\\w*)\\b|" +            // 标识符（含关键字）
            "(\\s+)|" +                            // 空白
            "(.)"                                  // 其他单个字符
    );

    private final TuiContext context;
    private final Panel panel;
    private final Panel codeLinesPanel;
    private final Label fileLabel;
    private final Label infoLabel;

    public CodePanel(TuiContext context) {
        this.context = context;
        this.panel = new Panel(new BorderLayout());

        // 顶部文件路径
        this.fileLabel = new Label("未选择文件");
        Panel header = new Panel(new LinearLayout(Direction.HORIZONTAL));
        header.addComponent(fileLabel);
        panel.addComponent(header, BorderLayout.Location.TOP);

        // 代码区域：垂直排列的 Label 列表（每行一个 Label 或一个水平 Panel）
        this.codeLinesPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(codeLinesPanel, BorderLayout.Location.CENTER);

        // 底部信息栏（行数提示等）
        this.infoLabel = new Label("");
        panel.addComponent(infoLabel, BorderLayout.Location.BOTTOM);

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
            clearCodeLines();
            infoLabel.setText("");
            return;
        }

        try {
            String content = Files.readString(file);
            String fileName = file.getFileName().toString();
            fileLabel.setText(fileName + " (" + file.toAbsolutePath() + ")");
            renderCode(fileName, content);
        } catch (IOException e) {
            fileLabel.setText("读取失败: " + file.getFileName());
            clearCodeLines();
            infoLabel.setText("无法读取文件: " + e.getMessage());
        }
    }

    /**
     * 直接设置显示的代码内容。
     */
    public void setCode(String fileName, String code) {
        fileLabel.setText(fileName);
        renderCode(fileName, code != null ? code : "");
    }

    /**
     * 获取当前显示的代码（仅保留最近一次的原始文本，不保证实时同步）。
     */
    public String getCode() {
        // 从各 Label 中拼接回原始文本
        StringBuilder sb = new StringBuilder();
        for (Component c : codeLinesPanel.getChildrenList()) {
            if (c instanceof Label lbl) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(lbl.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 获取当前显示的文件名。
     */
    public String getCurrentFileName() {
        return fileLabel.getText();
    }

    /* ---------- private helpers ---------- */

    private void clearCodeLines() {
        codeLinesPanel.removeAllComponents();
        codeLinesPanel.invalidate();
    }

    private void renderCode(String fileName, String content) {
        codeLinesPanel.removeAllComponents();

        String[] lines = content.split("\r?\n", -1);
        boolean isJava = fileName.endsWith(".java");

        int displayCount = Math.min(lines.length, MAX_VISIBLE_LINES);
        for (int i = 0; i < displayCount; i++) {
            Component lineComponent = isJava
                    ? buildHighlightedLine(lines[i])
                    : new Label(lines[i]);
            codeLinesPanel.addComponent(lineComponent);
        }

        if (lines.length > MAX_VISIBLE_LINES) {
            int omitted = lines.length - MAX_VISIBLE_LINES;
            infoLabel.setText("... 已省略 " + omitted + " 行（共 " + lines.length + " 行）");
        } else {
            infoLabel.setText("共 " + lines.length + " 行");
        }

        codeLinesPanel.invalidate();
    }

    /**
     * 为单行 Java 代码构建带颜色属性的 Label。
     *
     * Lanterna 3.1.1 的 Label 支持 setForegroundColor/setBackgroundColor，
     * 但不支持逐字符变色。为了在一行内展示多种颜色，我们使用一个水平 Panel，
     * 将一行拆分为多个 Label（每个 token 一个），分别设置前景色。
     */
    private Component buildHighlightedLine(String line) {
        Panel linePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        linePanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning));

        Matcher m = TOKEN_PATTERN.matcher(line);
        while (m.find()) {
            String token = m.group();
            TextColor color = COLOR_DEFAULT;

            if (m.group(1) != null || m.group(2) != null) {
                // 注释
                color = COLOR_COMMENT;
            } else if (m.group(3) != null || m.group(4) != null) {
                // 字符串/字符
                color = COLOR_STRING;
            } else if (m.group(5) != null) {
                // 数字
                color = COLOR_NUMBER;
            } else if (m.group(6) != null) {
                // 标识符 —— 判断是否为关键字
                if (JAVA_KEYWORDS.contains(token)) {
                    color = COLOR_KEYWORD;
                }
            }

            Label tokenLabel = new Label(token);
            if (color != COLOR_DEFAULT) {
                tokenLabel.setForegroundColor(color);
            }
            linePanel.addComponent(tokenLabel);
        }

        // 处理空行（matcher 可能没有任何匹配）
        if (linePanel.getChildrenList().isEmpty()) {
            linePanel.addComponent(new Label(" "));
        }

        return linePanel;
    }
}
