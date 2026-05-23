package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 左侧文件树面板：浏览项目文件结构，点击文件可发送给 Agent。
 */
public class FileTreePanel {

    private final TuiContext context;
    private final Panel panel;
    private final ActionListBox fileList;
    private Path currentDir;
    private final Deque<Path> navigationStack = new ArrayDeque<>();
    private static final int MAX_VISIBLE_FILES = 50;

    public FileTreePanel(TuiContext context) {
        this.context = context;
        this.currentDir = Path.of(System.getProperty("user.dir"));
        this.panel = new Panel(new BorderLayout());

        // 标题栏
        Panel header = new Panel(new LinearLayout(Direction.HORIZONTAL));
        header.addComponent(new Label("文件"));
        header.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        header.addComponent(new Button("↑", this::goUp));
        header.addComponent(new Button("刷新", this::refresh));
        panel.addComponent(header, BorderLayout.Location.TOP);

        // 文件列表
        this.fileList = new ActionListBox(new TerminalSize(28, 20));
        this.fileList.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(fileList, BorderLayout.Location.CENTER);

        // 底部提示
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        footer.addComponent(new Label("Enter打开"));
        panel.addComponent(footer, BorderLayout.Location.BOTTOM);

        refresh();
    }

    public Component getComponent() {
        return panel;
    }

    private void refresh() {
        fileList.clearItems();
        if (!navigationStack.isEmpty()) {
            fileList.addItem("../", () -> goUp());
        }

        if (!Files.isDirectory(currentDir)) {
            fileList.addItem("(无法访问)", () -> {});
            return;
        }

        try (Stream<Path> stream = Files.list(currentDir)) {
            List<Path> entries = stream
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .limit(MAX_VISIBLE_FILES)
                    .toList();

            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                boolean isDir = Files.isDirectory(entry);
                String display = isDir ? "[DIR] " + name : "  " + name;
                Path target = entry;
                if (isDir) {
                    fileList.addItem(display, () -> enterDirectory(target));
                } else {
                    fileList.addItem(display, () -> selectFile(target));
                }
            }

            if (entries.size() == MAX_VISIBLE_FILES) {
                fileList.addItem("(更多文件已截断...)", () -> {});
            }
        } catch (IOException e) {
            fileList.addItem("(读取失败: " + e.getMessage() + ")", () -> {});
        }
    }

    private void enterDirectory(Path dir) {
        navigationStack.push(currentDir);
        currentDir = dir;
        refresh();
    }

    private void goUp() {
        if (!navigationStack.isEmpty()) {
            currentDir = navigationStack.pop();
        } else {
            Path parent = currentDir.getParent();
            if (parent != null) {
                currentDir = parent;
            }
        }
        refresh();
    }

    private void selectFile(Path file) {
        context.setSelectedFile(file);
        context.fireAction("open_file:" + file.toAbsolutePath());
    }

    /**
     * 返回当前浏览目录。
     */
    public Path getCurrentDir() {
        return currentDir;
    }
}
