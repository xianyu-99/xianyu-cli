package com.yucli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.yucli.ProductInfo;
import com.yucli.agent.Agent;

import java.io.IOException;
import java.io.PrintStream;

/**
 * YuCLI TUI 应用入口。
 *
 * 基于 Lanterna 的终端 UI，提供：
 * - 左侧固定 ChatPanel 对话区
 * - 右侧 fileTree/code/config Tab
 * - 底部状态栏显示模型、模式、右侧 Tab 和左右焦点
 */
public class TuiApplication {

    private final WindowBasedTextGUI gui;
    private final BasicWindow mainWindow;
    private final Screen screen;
    private final TuiContext context;
    private final Panel contentPanel;
    private final ChatPanel chatPanel;
    private final FileTreePanel fileTreePanel;
    private final CodePanel codePanel;
    private final ConfigPanel configPanel;
    private Label activeTabLabel;
    private Label focusLabel;
    private boolean closed;
    private boolean focusOnLeftPane = true;

    public TuiApplication() throws IOException {
        this(null);
    }

    public TuiApplication(Agent agent) throws IOException {
        this(agent, createDefaultScreen());
    }

    TuiApplication(Agent agent, Screen screen) throws IOException {
        this.screen = screen;
        this.screen.startScreen();
        this.gui = new MultiWindowTextGUI(screen);
        this.mainWindow = new BasicWindow(windowTitle()) {
            @Override
            public boolean handleInput(KeyStroke keyStroke) {
                if (TuiApplication.this.handleInput(keyStroke)) {
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
        this.fileTreePanel = new FileTreePanel(context);
        this.codePanel = new CodePanel(context);
        this.configPanel = new ConfigPanel(context);
        this.contentPanel = new Panel(new BorderLayout());

        buildLayout();
        registerApplicationActions();
    }

    boolean handleInput(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.F1) {
            switchTab(TuiContext.TAB_FILE_TREE);
            return true;
        }
        if (keyStroke.getKeyType() == KeyType.F2) {
            switchTab(TuiContext.TAB_CODE);
            return true;
        }
        if (keyStroke.getKeyType() == KeyType.F3) {
            switchTab(TuiContext.TAB_CONFIG);
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
        if (keyStroke.getKeyType() == KeyType.Character && keyStroke.getCharacter() == 't' && keyStroke.isCtrlDown()) {
            toggleFocusPane();
            return true;
        }
        return false;
    }

    boolean isFocusOnLeftPane() {
        return focusOnLeftPane;
    }

    String getActiveRightTabName() {
        return context.getActiveTabName();
    }

    TuiContext getContext() {
        return context;
    }

    private void registerApplicationActions() {
        context.onAction(action -> {
            if (action.startsWith("open_file:")) {
                focusOnLeftPane = false;
                switchTab(TuiContext.TAB_CODE);
            }
        });
    }

    private void toggleFocusPane() {
        focusOnLeftPane = !focusOnLeftPane;
        if (focusOnLeftPane) {
            chatPanel.takeFocus();
        } else {
            focusActiveRightPanel();
        }
        updateStatusLabels();
    }

    private void focusActiveRightPanel() {
        switch (context.getActiveTabName()) {
            case TuiContext.TAB_FILE_TREE -> fileTreePanel.takeFocus();
            case TuiContext.TAB_CODE -> codePanel.takeFocus();
            case TuiContext.TAB_CONFIG -> configPanel.takeFocus();
        }
    }

    private static Screen createDefaultScreen() throws IOException {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setTerminalEmulatorTitle("YuCLI TUI");
        return factory.createScreen();
    }

    private void buildLayout() {
        Panel root = new Panel(new BorderLayout());

        // 顶部菜单栏
        root.addComponent(createMenuBar(), BorderLayout.Location.TOP);

        // 中间内容区：左右分栏
        Panel content = new Panel(new LinearLayout(Direction.HORIZONTAL));

        // 左侧固定对话区
        Panel leftContainer = new Panel(new BorderLayout());
        leftContainer.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
        leftContainer.addComponent(chatPanel.getComponent(), BorderLayout.Location.CENTER);
        content.addComponent(leftContainer);

        // 右侧内容面板：文件树 / 代码 / 配置
        Panel rightContainer = new Panel(new BorderLayout());
        rightContainer.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
        rightContainer.addComponent(createTabPanel(), BorderLayout.Location.CENTER);
        content.addComponent(rightContainer);

        root.addComponent(content, BorderLayout.Location.CENTER);

        // 底部状态栏
        root.addComponent(createStatusBar(), BorderLayout.Location.BOTTOM);

        mainWindow.setComponent(root);
        gui.addWindow(mainWindow);
    }

    private Panel createMenuBar() {
        Panel menu = new Panel(new LinearLayout(Direction.HORIZONTAL));
        menu.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

        menu.addComponent(new Button("文件 (F1)", () -> switchTab(TuiContext.TAB_FILE_TREE)));
        menu.addComponent(new Button("代码 (F2)", () -> switchTab(TuiContext.TAB_CODE)));
        menu.addComponent(new Button("配置 (F3)", () -> switchTab(TuiContext.TAB_CONFIG)));
        menu.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        menu.addComponent(new Button("发送 (F5)", () -> context.fireAction("send")));
        menu.addComponent(new Button("退出 (F10)", this::stop));

        return menu;
    }

    private Panel createTabPanel() {
        Panel tabContainer = new Panel(new BorderLayout());

        // Tab 按钮栏
        Panel tabButtons = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button fileTreeBtn = new Button("[文件]", () -> switchTab(TuiContext.TAB_FILE_TREE));
        Button codeBtn = new Button(" 代码 ", () -> switchTab(TuiContext.TAB_CODE));
        Button configBtn = new Button(" 配置 ", () -> switchTab(TuiContext.TAB_CONFIG));
        tabButtons.addComponent(fileTreeBtn);
        tabButtons.addComponent(codeBtn);
        tabButtons.addComponent(configBtn);
        tabContainer.addComponent(tabButtons, BorderLayout.Location.TOP);

        // 右侧内容面板 - 初始显示文件树
        contentPanel.addComponent(fileTreePanel.getComponent(), BorderLayout.Location.CENTER);
        tabContainer.addComponent(contentPanel, BorderLayout.Location.CENTER);

        // 监听 tab 切换以更新按钮状态
        context.onTabSwitch(tabName -> {
            fileTreeBtn.setLabel(tabName.equals(TuiContext.TAB_FILE_TREE) ? "[文件]" : " 文件 ");
            codeBtn.setLabel(tabName.equals(TuiContext.TAB_CODE) ? "[代码]" : " 代码 ");
            configBtn.setLabel(tabName.equals(TuiContext.TAB_CONFIG) ? "[配置]" : " 配置 ");
            updateStatusLabels();
        });

        return tabContainer;
    }

    private void switchTab(String tabName) {
        if (TuiContext.tabIndexFor(tabName) == null) {
            return;
        }
        contentPanel.removeAllComponents();
        switch (tabName) {
            case TuiContext.TAB_FILE_TREE -> contentPanel.addComponent(fileTreePanel.getComponent(), BorderLayout.Location.CENTER);
            case TuiContext.TAB_CODE -> contentPanel.addComponent(codePanel.getComponent(), BorderLayout.Location.CENTER);
            case TuiContext.TAB_CONFIG -> contentPanel.addComponent(configPanel.getComponent(), BorderLayout.Location.CENTER);
            default -> {
                return;
            }
        }
        context.fireTabSwitch(tabName);
        contentPanel.invalidate();
        if (!focusOnLeftPane) {
            focusActiveRightPanel();
        }
        updateStatusLabels();
    }

    private Panel createStatusBar() {
        Panel status = new Panel(new LinearLayout(Direction.HORIZONTAL));

        Label modelLabel = new Label(" 模型: " + context.getModelName() + " ");
        Label modeLabel = new Label(" 模式: " + context.getModeName() + " ");
        this.activeTabLabel = new Label("");
        this.focusLabel = new Label("");
        Label hintLabel = new Label(" F1文件 F2代码 F3配置 | Ctrl+T切换左右焦点 | F5发送 | F10退出 ");

        status.addComponent(modelLabel);
        status.addComponent(modeLabel);
        status.addComponent(activeTabLabel);
        status.addComponent(focusLabel);
        status.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        status.addComponent(hintLabel);

        context.setStatusLabel(modelLabel, modeLabel);
        updateStatusLabels();
        return status;
    }

    private void updateStatusLabels() {
        if (activeTabLabel != null) {
            activeTabLabel.setText(" 右侧: " + displayNameForRightTab(context.getActiveTabName()) + " ");
        }
        if (focusLabel != null) {
            String focusText = focusOnLeftPane
                    ? "左侧聊天"
                    : "右侧" + displayNameForRightTab(context.getActiveTabName());
            focusLabel.setText(" 焦点: " + focusText + " ");
        }
    }

    private static String displayNameForRightTab(String tabName) {
        return switch (tabName) {
            case TuiContext.TAB_CODE -> "代码";
            case TuiContext.TAB_CONFIG -> "配置";
            default -> "文件";
        };
    }

    public void run() {
        try {
            gui.waitForWindowToClose(mainWindow);
        } finally {
            closeResources();
        }
    }

    public void stop() {
        mainWindow.close();
        closeResources();
    }

    public static String windowTitle() {
        return "YuCLI TUI v" + ProductInfo.VERSION;
    }

    private synchronized void closeResources() {
        if (closed) {
            return;
        }
        closed = true;
        chatPanel.shutdown();
        try {
            screen.stopScreen();
        } catch (IOException e) {
            System.err.println("TUI 关闭失败: " + e.getMessage());
        }
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
        launch(agent, TuiApplication::new, System.err);
    }

    static boolean launch(Agent agent, ApplicationFactory factory, PrintStream err) {
        try {
            TuiApplication app = factory.create(agent);
            app.run();
            return true;
        } catch (IOException e) {
            err.println("TUI 启动失败: " + e.getMessage());
            return false;
        }
    }

    @FunctionalInterface
    interface ApplicationFactory {
        TuiApplication create(Agent agent) throws IOException;
    }
}
