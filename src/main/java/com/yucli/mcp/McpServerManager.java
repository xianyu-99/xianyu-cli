package com.yucli.mcp;

import com.yucli.mcp.auth.McpOAuthClient;
import com.yucli.mcp.auth.TokenStore;
import com.yucli.mcp.config.McpConfigLoader;
import com.yucli.mcp.config.McpServerConfig;
import com.yucli.mcp.notifications.NotificationRouter;
import com.yucli.mcp.protocol.McpToolDescriptor;
import com.yucli.mcp.resources.McpResourceCache;
import com.yucli.mcp.resources.McpResourceContent;
import com.yucli.mcp.resources.McpResourceDescriptor;
import com.yucli.mcp.resources.McpResourceTool;
import com.yucli.policy.AuditLog;
import com.yucli.mcp.transport.McpTransport;
import com.yucli.mcp.transport.StdioTransport;
import com.yucli.mcp.transport.StreamableHttpTransport;
import com.yucli.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class McpServerManager implements AutoCloseable {
    private final ToolRegistry toolRegistry;
    private final Path projectDir;
    private final McpConfigLoader configLoader;
    private final Map<String, McpServer> servers = new ConcurrentHashMap<>();
    private final McpResourceCache resourceCache = new McpResourceCache();
    private final TokenStore tokenStore = new TokenStore();
    private final ConcurrentHashMap<String, Integer> restartCounts = new ConcurrentHashMap<>();
    private static final int MAX_AUTO_RESTARTS = 3;
    private static final long[] RESTART_BACKOFF_MS = {1000, 5000, 15000};
    private volatile com.yucli.llm.LlmClient llmClient;

    public McpServerManager(ToolRegistry toolRegistry, Path projectDir) {
        this(toolRegistry, projectDir, new McpConfigLoader(projectDir));
    }

    public McpServerManager(ToolRegistry toolRegistry, Path projectDir, McpConfigLoader configLoader) {
        this.toolRegistry = toolRegistry;
        this.projectDir = projectDir.toAbsolutePath().normalize();
        this.configLoader = configLoader;
    }

    public void setLlmClient(com.yucli.llm.LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public void loadConfiguredServers() throws IOException {
        Map<String, McpServerConfig> configs = configLoader.load();
        servers.clear();
        configs.forEach((name, config) -> servers.put(name, new McpServer(name, config)));
    }

    public void startAll() {
        List<McpServer> targets = servers.values().stream()
                .filter(server -> !server.config().isDisabled())
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        // 用专属 daemon executor，避免 npx/uvx 冷启动期间占满 ForkJoinPool.commonPool 影响其他并发任务。
        AtomicInteger threadId = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(targets.size(), 8),
                r -> {
                    Thread t = new Thread(r, "YuCLI-mcp-startup-" + threadId.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        try {
            List<CompletableFuture<Void>> futures = targets.stream()
                    .map(server -> CompletableFuture.runAsync(() -> start(server), executor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    public synchronized String restart(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        unregisterTools(server);
        server.close();
        server.config().setDisabled(false);
        start(server);
        return server.status() == McpServerStatus.READY
                ? "✅ MCP server 已重启: " + name
                : "❌ MCP server 重启失败: " + name + " - " + server.errorMessage();
    }

    public synchronized String disable(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        unregisterTools(server);
        server.close();
        server.config().setDisabled(true);
        server.status(McpServerStatus.DISABLED);
        server.errorMessage(null);
        return "⏸️ MCP server 已禁用: " + name;
    }

    public synchronized String enable(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        server.config().setDisabled(false);
        start(server);
        return server.status() == McpServerStatus.READY
                ? "▶️ MCP server 已启用: " + name
                : "❌ MCP server 启用失败: " + name + " - " + server.errorMessage();
    }

    public String logs(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        List<String> lines = server.logs();
        if (lines.isEmpty()) {
            return "📭 MCP server 暂无 stderr 日志: " + name;
        }
        return String.join(System.lineSeparator(), lines);
    }

    public Collection<McpServer> servers() {
        return servers.values().stream()
                .sorted(java.util.Comparator.comparing(McpServer::name))
                .toList();
    }

    public String formatStatus() {
        StringBuilder sb = new StringBuilder("🔌 MCP Servers\n");
        if (servers.isEmpty()) {
            sb.append("  未配置 MCP server。配置文件: ~/.YuCLI/mcp.json 或 .YuCLI/mcp.json");
            return sb.toString();
        }
        for (McpServer server : servers()) {
            String status = switch (server.status()) {
                case READY -> "● ready";
                case STARTING -> "… starting";
                case DISABLED -> "○ disabled";
                case ERROR -> "✗ error";
            };
            String tools = server.status() == McpServerStatus.READY
                    ? server.tools().size() + (server.tools().size() == 1 ? " tool" : " tools")
                    : "—";
            String uptime = server.status() == McpServerStatus.READY ? "uptime " + formatDuration(server.uptime()) : "";
            String pid = server.processId() == null ? "" : "pid " + server.processId();
            String error = server.status() == McpServerStatus.ERROR && server.errorMessage() != null
                    ? server.errorMessage()
                    : "";
            sb.append(String.format("  %-14s %-11s %-6s %-9s %-10s %s %s%n",
                    server.name(), status, server.transportName(), tools, uptime, pid, error));
        }
        return sb.toString().trim();
    }

    public String startupSummary() {
        if (servers.isEmpty()) {
            return "🔌 MCP server：未配置（可创建 ~/.YuCLI/mcp.json 或 .YuCLI/mcp.json）";
        }
        long ready = servers.values().stream().filter(s -> s.status() == McpServerStatus.READY).count();
        int tools = servers.values().stream().mapToInt(s -> s.tools().size()).sum();
        StringBuilder sb = new StringBuilder("🔌 启动 MCP server（" + servers.size() + " 个）...\n");
        for (McpServer server : servers()) {
            if (server.status() == McpServerStatus.READY) {
                sb.append(String.format("   ✓ %-14s %-6s %3d 工具%n",
                        server.name(), server.transportName(), server.tools().size()));
            } else if (server.status() == McpServerStatus.DISABLED) {
                sb.append(String.format("   ○ %-14s %-6s disabled%n", server.name(), server.transportName()));
            } else {
                sb.append(String.format("   ✗ %-14s %-6s 启动失败: %s%n",
                        server.name(), server.transportName(), server.errorMessage()));
            }
        }
        sb.append("   ").append(ready).append("/").append(servers.size())
                .append(" 就绪，共 ").append(tools).append(" 个 MCP 工具");
        return sb.toString();
    }

    public List<McpResourceDescriptor> resourceCandidates() {
        return resourceCache.all();
    }

    public List<String> allPrompts() {
        List<String> allPrompts = new ArrayList<>();
        for (McpServer server : servers()) {
            if (server.status() != McpServerStatus.READY || server.client() == null) {
                continue;
            }
            try {
                List<String> prompts = server.client().listPrompts();
                for (String prompt : prompts) {
                    allPrompts.add("[" + server.name() + "] " + prompt);
                }
            } catch (Exception e) {
                System.err.println("[MCP] Failed to list prompts from server: " + e.getMessage());
            }
        }
        return allPrompts;
    }

    public String resources(String serverName) {
        McpServer server = servers.get(serverName);
        if (server == null) {
            return "未找到 MCP server: " + serverName;
        }
        if (server.client() == null || server.status() != McpServerStatus.READY) {
            return "MCP server 未就绪: " + serverName + " (" + server.status() + ")";
        }
        try {
            List<McpResourceDescriptor> resources = refreshResources(server);
            return McpClient.formatResources(resources);
        } catch (Exception e) {
            return "读取 MCP resources 失败 (" + serverName + "): " + e.getMessage();
        }
    }

    public String prompts(String serverName) {
        McpServer server = servers.get(serverName);
        if (server == null) {
            return "未找到 MCP server: " + serverName;
        }
        if (server.client() == null || server.status() != McpServerStatus.READY) {
            return "MCP server 未就绪: " + serverName + " (" + server.status() + ")";
        }
        try {
            List<String> prompts = server.client().listPrompts();
            if (prompts.isEmpty()) {
                return "📭 该 MCP server 暂无 prompts: " + serverName;
            }
            StringBuilder sb = new StringBuilder("🧩 MCP prompts - ").append(serverName).append('\n');
            for (String prompt : prompts) {
                sb.append("- ").append(prompt).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "读取 MCP prompts 失败 (" + serverName + "): " + e.getMessage();
        }
    }

    public ResourceReadResult readResourceForMention(String serverName, String uri) throws IOException {
        McpServer server = servers.get(serverName);
        if (server == null) {
            throw new IOException("未找到 MCP server: " + serverName);
        }
        if (server.client() == null || server.status() != McpServerStatus.READY) {
            throw new IOException("MCP server 未就绪: " + serverName + " (" + server.status() + ")");
        }
        long start = System.nanoTime();
        String toolName = McpToolDescriptor.namespaced(serverName, McpResourceTool.READ_RESOURCE);
        String args = "{\"uri\":\"" + uri.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        try {
            if (resourceCache.isServerStale(serverName)) {
                refreshResources(server);
            }
            List<McpResourceContent> contents = server.client().readResource(uri);
            resourceCache.markResourceFresh(serverName, uri);
            toolRegistry.getAuditLog().record(AuditLog.AuditEntry.allowByMention(
                    toolName, args, elapsedMillis(start)));
            return ResourceReadResult.from(contents);
        } catch (Exception e) {
            toolRegistry.getAuditLog().record(AuditLog.AuditEntry.error(
                    toolName, args, e.getMessage(), elapsedMillis(start)));
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(e.getMessage(), e);
        }
    }

    private void start(McpServer server) {
        unregisterTools(server);
        server.close();
        if (server.config().isDisabled()) {
            server.status(McpServerStatus.DISABLED);
            return;
        }
        server.status(McpServerStatus.STARTING);
        server.errorMessage(null);
        try {
            configLoader.prepare(server.config());
            McpOAuthClient oauthClient = null;
            if (server.config().isOauth() && server.config().isHttp()) {
                oauthClient = new McpOAuthClient(server.name(), server.config(), tokenStore);
                if (!oauthClient.isTokenValid()) {
                    System.out.println("OAuth server " + server.name() + " 无有效 token，需要授权。");
                    try {
                        oauthClient.authorize();
                    } catch (Exception e) {
                        throw new IOException("OAuth 授权失败: " + e.getMessage(), e);
                    }
                }
            }
            McpTransport transport = createTransport(server.config());
            if (transport instanceof StreamableHttpTransport httpTransport && oauthClient != null) {
                httpTransport.setTokenProvider(oauthClient);
            }
            McpClient client = new McpClient(server.name(), transport);
            client.initialize();
            registerNotificationHandlers(server, client);
            registerSamplingHandler(server, client);
            List<McpToolDescriptor> tools = buildToolList(server, client);
            replaceTools(server, client, tools);
            // Auto-restart on unexpected exit for stdio servers (register before READY to avoid race)
            if (transport instanceof StdioTransport stdioTransport) {
                stdioTransport.onExit(() -> handleUnexpectedExit(server));
            }

            server.client(client);
            server.tools(tools);
            server.markStarted();
            server.status(McpServerStatus.READY);
            restartCounts.remove(server.name());
        } catch (Exception e) {
            server.close();
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            server.errorMessage("[" + server.name() + "] " + root.getClass().getSimpleName() + ": " + root.getMessage());
            System.err.println("[MCP] Server '" + server.name() + "' start failed: " + root.getMessage());
            e.printStackTrace(System.err);
            server.status(McpServerStatus.ERROR);
        }
    }

    private void handleUnexpectedExit(McpServer server) {
        if (server.config().isDisabled()) {
            return;
        }
        int count = restartCounts.getOrDefault(server.name(), 0);
        if (count >= MAX_AUTO_RESTARTS) {
            server.status(McpServerStatus.ERROR);
            server.errorMessage("自动重启次数已达上限 (" + MAX_AUTO_RESTARTS + ")，停止重启");
            System.out.println("⚠️ MCP server " + server.name() + " 自动重启次数已达上限，停止重启");
            return;
        }
        long delayMs = RESTART_BACKOFF_MS[Math.min(count, RESTART_BACKOFF_MS.length - 1)];
        restartCounts.put(server.name(), count + 1);
        System.out.println("⚠️ MCP server " + server.name() + " 异常退出，" + (delayMs / 1000) + " 秒后自动重启 (" + (count + 1) + "/" + MAX_AUTO_RESTARTS + ")");
        Thread.ofVirtual().name("YuCLI-mcp-restart-" + server.name()).start(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (this) {
                start(server);
                if (server.status() == McpServerStatus.READY) {
                    System.out.println("✅ MCP server " + server.name() + " 自动重启成功");
                }
            }
        });
    }

    private List<McpToolDescriptor> buildToolList(McpServer server, McpClient client) throws IOException {
        List<McpToolDescriptor> tools = new ArrayList<>(client.listTools());
        if (client.supportsResources()) {
            List<McpResourceDescriptor> resources = client.listResources();
            resourceCache.put(server.name(), resources);
            tools.addAll(McpResourceTool.descriptors(server.name()));
        }
        validateNoDuplicateTools(server.name(), tools);
        return tools;
    }

    private void replaceTools(McpServer server, McpClient client, List<McpToolDescriptor> tools) {
        toolRegistry.replaceMcpToolsForServer(server.name(), tools,
                descriptor -> isResourceVirtualTool(descriptor)
                        ? McpResourceTool.invoker(client, descriptor)
                        : args -> invokeMcpTool(client, descriptor, args));
    }

    private boolean isResourceVirtualTool(McpToolDescriptor descriptor) {
        return McpResourceTool.LIST_RESOURCES.equals(descriptor.name())
                || McpResourceTool.READ_RESOURCE.equals(descriptor.name());
    }

    private void registerNotificationHandlers(McpServer server, McpClient client) {
        NotificationRouter router = new NotificationRouter();
        router.on("notifications/tools/list_changed", ignored -> {
            try {
                List<McpToolDescriptor> tools = buildToolList(server, client);
                replaceTools(server, client, tools);
                server.tools(tools);
            } catch (Exception e) {
                server.errorMessage("[" + server.name() + "] tools/list_changed 处理失败: " + e.getMessage());
                System.err.println("[MCP] Server '" + server.name() + "' tools/list_changed handler error: " + e.getMessage());
            }
        });
        router.on("notifications/resources/list_changed", ignored -> resourceCache.invalidateServer(server.name()));
        router.on("notifications/resources/updated", params -> {
            String uri = params.path("uri").asText("");
            if (!uri.isBlank()) {
                resourceCache.invalidateResource(server.name(), uri);
            }
        });
        client.onNotification(router);
    }

    private void registerSamplingHandler(McpServer server, McpClient client) {
        client.onSamplingRequest(params -> {
            if (llmClient == null) {
                throw new RuntimeException("LLM client 未初始化，无法处理 sampling 请求");
            }
            try {
                return handleSamplingRequest(params);
            } catch (Exception e) {
                throw new RuntimeException("sampling/createMessage 处理失败: " + e.getMessage(), e);
            }
        });
    }

    private com.fasterxml.jackson.databind.JsonNode handleSamplingRequest(com.fasterxml.jackson.databind.JsonNode params) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

        // Parse messages from params
        com.fasterxml.jackson.databind.JsonNode messagesNode = params.path("messages");
        java.util.List<com.yucli.llm.LlmClient.Message> messages = new java.util.ArrayList<>();

        // Add system prompt if provided
        String systemPrompt = params.path("systemPrompt").asText("");
        if (!systemPrompt.isBlank()) {
            messages.add(com.yucli.llm.LlmClient.Message.system(systemPrompt));
        }

        // Convert MCP messages to LlmClient messages
        if (messagesNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode msg : messagesNode) {
                String role = msg.path("role").asText("user");
                com.fasterxml.jackson.databind.JsonNode contentNode = msg.path("content");
                String text;
                if (contentNode.isTextual()) {
                    text = contentNode.asText();
                } else if (contentNode.has("text")) {
                    text = contentNode.path("text").asText();
                } else {
                    text = contentNode.toString();
                }
                messages.add(new com.yucli.llm.LlmClient.Message(role, text));
            }
        }

        // Call local LLM (maxTokens from MCP request not supported by current LlmClient interface)
        com.yucli.llm.LlmClient.ChatResponse response = llmClient.chat(messages, List.of());

        // Build MCP response
        result.put("model", llmClient.getModelName());
        result.put("role", "assistant");
        com.fasterxml.jackson.databind.node.ObjectNode content = result.putObject("content");
        content.put("type", "text");
        content.put("text", response.content() != null ? response.content() : "");
        return result;
    }

    private List<McpResourceDescriptor> refreshResources(McpServer server) throws IOException {
        List<McpResourceDescriptor> resources = server.client().listResources();
        resources = resources.stream()
                .sorted(Comparator.comparing(McpResourceDescriptor::uri))
                .toList();
        resourceCache.put(server.name(), resources);
        return resources;
    }

    /**
     * MCP 工具执行入口：把 LLM 给的 JSON 参数透传给 server 的 tools/call，并把异常转成 LLM 可读字符串。
     * 提取成独立方法是为了让 server 维度的错误信息（serverName/toolName）在堆栈和日志里清晰可见。
     */
    private static String invokeMcpTool(McpClient client, McpToolDescriptor descriptor, String argumentsJson) {
        try {
            return client.callTool(descriptor.name(), argumentsJson);
        } catch (Exception e) {
            return "MCP 工具调用失败 (" + descriptor.serverName() + "/" + descriptor.name() + "): " + e.getMessage();
        }
    }

    private McpTransport createTransport(McpServerConfig config) throws IOException {
        if (config.isHttp()) {
            return new StreamableHttpTransport(config.getUrl(), config.getHeaders());
        }
        return new StdioTransport(config.getCommand(), config.getArgs(), config.getEnv(), projectDir);
    }

    private void validateNoDuplicateTools(String serverName, List<McpToolDescriptor> tools) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (McpToolDescriptor tool : tools) {
            counts.merge(tool.name(), 1, Integer::sum);
        }
        List<String> duplicates = new ArrayList<>();
        counts.forEach((name, count) -> {
            if (count > 1) duplicates.add(name);
        });
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("MCP server " + serverName + " 返回重复工具名: " + duplicates);
        }
    }

    private void unregisterTools(McpServer server) {
        for (McpToolDescriptor tool : server.tools()) {
            toolRegistry.unregisterMcpTool(tool.namespacedName());
        }
        server.tools(List.of());
    }

    private static long elapsedMillis(long startedAtNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    public record ResourceReadResult(String content, String mimeType) {
        static ResourceReadResult from(List<McpResourceContent> contents) {
            if (contents == null || contents.isEmpty()) {
                return new ResourceReadResult("", "text/plain");
            }
            StringBuilder text = new StringBuilder();
            String firstMimeType = null;
            for (McpResourceContent content : contents) {
                if (firstMimeType == null || firstMimeType.isBlank()) {
                    firstMimeType = content.mimeType();
                }
                if (content.isText()) {
                    text.append(content.text());
                } else {
                    text.append("[binary resource blob omitted, base64 length=")
                            .append(content.blob() == null ? 0 : content.blob().length())
                            .append(']');
                }
                text.append(System.lineSeparator());
            }
            return new ResourceReadResult(text.toString().trim(), firstMimeType);
        }
    }

    public String authServer(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        if (!server.config().isOauth()) {
            return "MCP server 未配置 OAuth: " + name;
        }
        McpOAuthClient oauthClient = new McpOAuthClient(name, server.config(), tokenStore);
        try {
            oauthClient.authorize();
            return "OAuth 授权成功: " + name + "（token 已保存，重启 server 即可使用）";
        } catch (Exception e) {
            return "OAuth 授权失败: " + name + " - " + e.getMessage();
        }
    }

    public String authStatus() {
        StringBuilder sb = new StringBuilder("OAuth 认证状态\n");
        boolean any = false;
        for (McpServer server : servers()) {
            if (!server.config().isOauth()) continue;
            any = true;
            boolean valid = tokenStore.hasValidToken(server.name());
            String status = valid ? "已认证" : "未认证";
            TokenStore.TokenEntry entry = tokenStore.getToken(server.name());
            String expires = "";
            if (entry != null && entry.expiresAtEpochSeconds() > 0) {
                expires = " (过期: " + java.time.Instant.ofEpochSecond(entry.expiresAtEpochSeconds()) + ")";
            }
            sb.append(String.format("  %-14s %s%s%n", server.name(), status, expires));
        }
        if (!any) {
            sb.append("  没有配置 OAuth 的 MCP server");
        }
        return sb.toString().trim();
    }

    public String authRevoke(String name) {
        McpServer server = servers.get(name);
        if (server == null) {
            return "未找到 MCP server: " + name;
        }
        if (!server.config().isOauth()) {
            return "MCP server 未配置 OAuth: " + name;
        }
        if (!tokenStore.hasValidToken(name) && tokenStore.getToken(name) == null) {
            return "没有存储的 token: " + name;
        }
        tokenStore.removeToken(name);
        return "已撤销 OAuth token: " + name;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h";
    }

    @Override
    public void close() {
        for (McpServer server : servers.values()) {
            unregisterTools(server);
            server.close();
        }
    }
}
