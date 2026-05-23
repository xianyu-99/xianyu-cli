package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.yucli.agent.Agent;

import java.io.IOException;

/**
 * YuCLI TUI 应用入口。
 *
 * 基于 Lanterna 的终端 UI，提供：
 * - 左侧文件树浏览
 * - 右侧 Tab 切换：对话 / 代码 / 配置
 * - 底部状态栏
 */
public class TuiApplication {

    private final WindowBasedTextGUI gui;
    private final BasicWindow mainWindow;
    private final TuiContext context;
    private final Panel contentPanel;
    private final ChatPanel chatPanel;
    private final CodePanel codePanel;
    private final ConfigPanel configPanel;

    public TuiApplication() throws IOException {
        this(null);
    }

    public TuiApplication(Agent agent) throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setTerminalEmulatorTitle("YuCLI TUI");
        Screen screen = factory.createScreen();
        screen.startScreen();

        this.gui = new MultiWindowTextGUI(screen);
        this.mainWindow = new BasicWindow("YuCLI TUI v16.0.0") {
            @Override
            public boolean handleInput(KeyStroke keyStroke) {
                if (keyStroke.getKeyType() == KeyType.F1) {
                    switchTab("chat");
                    return true;
                }
                if (keyStroke.getKeyType() == KeyType.F2) {
                    switchTab("code");
                    return true;
                }
                if (keyStroke.getKeyType() == KeyType.F3) {
                    switchTab("config");
                    return true;
                }
                if (keyStroke.getKeyType() == KeyType.F5) {
                    context.fireAction("send");
                    return true;
                }
                if (keyStroke.getKeyType() == KeyType.F10) {
                    stop();
                    return true;
                }
                return super.handleInput(keyStroke);
            }
        };
        this.mainWindow.setHints(java.util.Collections.singletonList(Window.Hint.FULL_SCREEN));

        this.context = new TuiContext();
        this.context.setAgent(agent);

        // 预创建面板
        this.chatPanel = new ChatPanel(context);
        this.codePanel = new CodePanel(context);
        this.configPanel = new ConfigPanel(context);
        this.contentPanel = new Panel(new BorderLayout());

        buildLayout();
    }

    private void buildLayout() {
        Panel root = new Panel(new BorderLayout());

        // 顶部菜单栏
        root.addComponent(createMenuBar(), BorderLayout.Location.TOP);

        // 中间内容区：左右分栏
        Panel content = new Panel(new BorderLayout());

        // 左侧文件树
        FileTreePanel fileTree = new FileTreePanel(context);
        content.addComponent(fileTree.getComponent(), BorderLayout.Location.LEFT);

        // 右侧内容面板（Tab 切换）
        content.addComponent(createTabPanel(), BorderLayout.Location.CENTER);

        root.addComponent(content, BorderLayout.Location.CENTER);

        // 底部状态栏
        root.addComponent(createStatusBar(), BorderLayout.Location.BOTTOM);

        mainWindow.setComponent(root);
        gui.addWindow(mainWindow);
    }

    private Panel createMenuBar() {
        Panel menu = new Panel(new LinearLayout(Direction.HORIZONTAL));
        menu.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

        menu.addComponent(new Button("对话 (F1)", () -> switchTab("chat")));
        menu.addComponent(new Button("代码 (F2)", () -> switchTab("code")));
        menu.addComponent(new Button("配置 (F3)", () -> switchTab("config")));
        menu.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        menu.addComponent(new Button("发送 (F5)", () -> context.fireAction("send")));
        menu.addComponent(new Button("退出 (F10)", this::stop));

        return menu;
    }

    private Panel createTabPanel() {
        Panel tabContainer = new Panel(new BorderLayout());

        // Tab 按钮栏
        Panel tabButtons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button chatBtn = new Button("[对话]", () -> switchTab("chat"));
        Button codeBtn = new Button(" 代码 ", () -> switchTab("code"));
        Button configBtn = new Button(" 配置 ", () -> switchTab("config"));
        tabButtons.addComponent(chatBtn);
        tabButtons.addComponent(codeBtn);
        tabButtons.addComponent(configBtn);
        tabContainer.addComponent(tabButtons, BorderLayout.Location.TOP);

        // 内容面板 - 初始显示对话
        contentPanel.addComponent(chatPanel.getComponent(), BorderLayout.Location.CENTER);
        tabContainer.addComponent(contentPanel, BorderLayout.Location.CENTER);

        // 监听 tab 切换以更新按钮状态
        context.onTabSwitch(tabName -> {
            chatBtn.setLabel(tabName.equals("chat") ? "[对话]" : " 对话 ");
            codeBtn.setLabel(tabName.equals("code") ? "[代码]" : " 代码 ");
            configBtn.setLabel(tabName.equals("config") ? "[配置]" : " 配置 ");
        });

        return tabContainer;
    }

    private void switchTab(String tabName) {
        contentPanel.removeAllComponents();
        switch (tabName) {
            case "chat" -> contentPanel.addComponent(chatPanel.getComponent(), BorderLayout.Location.CENTER);
            case "code" -> contentPanel.addComponent(codePanel.getComponent(), BorderLayout.Location.CENTER);
            case "config" -> contentPanel.addComponent(configPanel.getComponent(), BorderLayout.Location.CENTER);
        }
        context.fireTabSwitch(tabName);
        contentPanel.invalidate();
    }

    private Panel createStatusBar() {
        Panel status = new Panel(new LinearLayout(Direction.HORIZONTAL));

        Label modelLabel = new Label(" 模型: " + context.getModelName() + " ");
        Label modeLabel = new Label(" 模式: " + context.getModeName() + " ");
        Label hintLabel = new Label(" Tab切换 | F5发送 | F10退出 ");

        status.addComponent(modelLabel);
        status.addComponent(modeLabel);
        status.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        status.addComponent(hintLabel);

        context.setStatusLabel(modelLabel, modeLabel);
        return status;
    }

    public void run() {
        gui.waitForWindowToClose(mainWindow);
    }

    public void stop() {
        mainWindow.close();
    }

    /**
     * 启动 TUI 模式（无 Agent）。
     */
    public static void launch() {
        launch(null);
    }

    /**
     * 启动 TUI 模式，传入已初始化的 Agent。
     */
    public static void launch(Agent agent) {
        try {
            TuiApplication app = new TuiApplication(agent);
            app.run();
        } catch (IOException e) {
            System.err.println("TUI 启动失败: " + e.getMessage());
            System.exit(1);
        }
    }
}
