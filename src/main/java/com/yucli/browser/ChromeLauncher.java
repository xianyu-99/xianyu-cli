package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chrome/Chromium 进程启动器。
 *
 * 负责通过 ProcessBuilder 启动 Chrome 并指定 --remote-debugging-port，
 * 以及通过 JVM 退出 hook 确保进程被清理。
 */
public class ChromeLauncher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STARTUP_POLL_MS = 500;
    private static final int STARTUP_TIMEOUT_MS = 15_000;

    private Process chromeProcess;
    private int debugPort = 9222;

    /**
     * 启动 Chrome/Chromium，返回调试端口。
     *
     * @param headless 是否使用无头模式
     * @param userDataDir 用户数据目录（null 表示使用临时目录）
     * @return 调试端口号
     */
    public int launch(boolean headless, String userDataDir) throws Exception {
        String chromePath = findChromeExecutable();
        if (chromePath == null) {
            throw new RuntimeException("未找到 Chrome/Chromium 可执行文件。请确保 Chrome 已安装，或在 Windows 上设置 CHROME_PATH 环境变量。");
        }

        List<String> args = new ArrayList<>();
        args.add(chromePath);
        args.add("--remote-debugging-port=" + debugPort);
        args.add("--no-first-run");
        args.add("--no-default-browser-check");

        if (headless) {
            args.add("--headless=new");
            args.add("--no-sandbox");
            args.add("--disable-gpu");
            args.add("--disable-dev-shm-usage");
        }

        if (userDataDir != null && !userDataDir.isBlank()) {
            args.add("--user-data-dir=" + userDataDir);
        }

        args.add("about:blank");

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO(); // 将 Chrome 的 stdout/stderr 继承到当前进程
        this.chromeProcess = pb.start();

        // 注册 JVM 退出 hook 自动清理
        Runtime.getRuntime().addShutdownHook(new Thread(this::kill));

        // 等待 Chrome 就绪
        waitForChromeReady();

        return debugPort;
    }

    /**
     * 检查 Chrome 是否已就绪（轮询 /json/version）。
     */
    private void waitForChromeReady() throws Exception {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                URL url = new URL("http://localhost:" + debugPort + "/json/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // Chrome 还未就绪，继续轮询
            }
            Thread.sleep(STARTUP_POLL_MS);
        }
        throw new RuntimeException("Chrome 启动超时（" + STARTUP_TIMEOUT_MS + "ms），请检查 Chrome 是否可用。");
    }

    /**
     * 关闭 Chrome 进程。
     */
    public void kill() {
        if (chromeProcess != null && chromeProcess.isAlive()) {
            chromeProcess.destroy();
            try {
                if (!chromeProcess.waitFor(5, TimeUnit.SECONDS)) {
                    chromeProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                chromeProcess.destroyForcibly();
            }
        }
        chromeProcess = null;
    }

    public boolean isAlive() {
        return chromeProcess != null && chromeProcess.isAlive();
    }

    public int getDebugPort() {
        return debugPort;
    }

    /**
     * 在不同平台上查找 Chrome/Chromium 可执行文件。
     */
    private static String findChromeExecutable() {
        // 优先从环境变量读取
        String envPath = System.getenv("CHROME_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }

        String os = System.getProperty("os.name").toLowerCase();
        List<String> candidates = new ArrayList<>();

        if (os.contains("win")) {
            candidates.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add(System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");
            candidates.add("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        } else if (os.contains("mac")) {
            candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            candidates.add("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
        } else {
            // Linux
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/google-chrome-stable");
            candidates.add("/usr/bin/chromium");
            candidates.add("/usr/bin/chromium-browser");
            candidates.add("/usr/bin/microsoft-edge");
        }

        for (String candidate : candidates) {
            if (candidate != null && new java.io.File(candidate).exists()) {
                return candidate;
            }
        }

        // 尝试从 PATH 中查找
        try {
            String cmd = os.contains("win") ? "where chrome" : "which google-chrome chromium chromium-browser";
            Process p = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank() && !line.contains("未找到") && !line.contains("not found")) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
