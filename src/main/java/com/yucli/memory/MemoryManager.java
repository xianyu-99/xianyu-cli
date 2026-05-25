package com.yucli.memory;

import com.yucli.llm.LlmClient;
import com.yucli.session.SessionMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Memory 管理器 - Memory 系统的门面类
 *
 * 统一管理短期记忆、长期记忆、上下文压缩和检索，
 * 为 Agent 提供简洁的记忆存取接口。
 *
 * 第 12 期长上下文工程：支持长/短双模式
 * - 短模式（< 32k）：完整 Memory 策略（摘要、检索、压缩）
 * - 长模式（≥ 100k）：跳过摘要压缩，提高 RAG top-K，允许直接装填大段内容
 */
public class MemoryManager {

    public enum ContextMode {
        SHORT,   // < 32k 窗口，走完整记忆策略
        LONG     // ≥ 100k 窗口，跳过压缩，放宽限制
    }

    private final ConversationMemory shortTermMemory;
    private final LongTermMemory longTermMemory;
    private final ContextCompressor compressor;
    private final MemoryRetriever retriever;
    private final TokenBudget tokenBudget;
    private final ContextMode contextMode;

    public MemoryManager(LlmClient llmClient) {
        this(llmClient, 32768, 200000, null);
    }

    /**
     * @param llmClient      LLM 客户端（用于压缩时的摘要生成）
     * @param shortTermBudget 短期记忆 token 预算
     * @param contextWindow  模型上下文窗口大小
     */
    public MemoryManager(LlmClient llmClient, int shortTermBudget, int contextWindow) {
        this(llmClient, shortTermBudget, contextWindow, null);
    }

    public MemoryManager(LlmClient llmClient, int shortTermBudget, int contextWindow, LongTermMemory longTermMemory) {
        this.shortTermMemory = new ConversationMemory(shortTermBudget);
        this.longTermMemory = longTermMemory != null ? longTermMemory : new LongTermMemory();
        this.compressor = new ContextCompressor(llmClient);
        this.retriever = new MemoryRetriever(shortTermMemory, this.longTermMemory);
        this.tokenBudget = new TokenBudget(contextWindow);
        this.contextMode = resolveContextMode(contextWindow);
    }

    private static ContextMode resolveContextMode(int contextWindow) {
        if (contextWindow >= 100_000) {
            return ContextMode.LONG;
        }
        return ContextMode.SHORT;
    }

    public void setLlmClient(LlmClient llmClient) {
        this.compressor.setLlmClient(llmClient);
    }

    /**
     * 添加用户消息到短期记忆
     */
    public void addUserMessage(String content) {
        MemoryEntry entry = new MemoryEntry(
                "user-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "user"),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    /**
     * 添加助手回复到短期记忆
     */
    public void addAssistantMessage(String content) {
        MemoryEntry entry = new MemoryEntry(
                "assistant-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "assistant"),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    // 工具结果在记忆中的最大长度（完整结果已在任务消息历史里，记忆只需保留摘要）
    private static final int MAX_TOOL_RESULT_CHARS = 500;

    /**
     * 添加工具执行结果到短期记忆（截断过长结果，避免快速撑满预算）
     */
    public void addToolResult(String toolName, String result) {
        String truncated = result.length() > MAX_TOOL_RESULT_CHARS
                ? result.substring(0, MAX_TOOL_RESULT_CHARS) + "...(已截断)"
                : result;
        String content = "[" + toolName + "] " + truncated;
        MemoryEntry entry = new MemoryEntry(
                "tool-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.TOOL_RESULT,
                Map.of("source", "tool", "toolName", toolName),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    public void addCompressedSummary(String content, int tokenCount) {
        String cleanContent = content.startsWith("[compressed] ") ? content.substring(12) : content;
        MemoryEntry entry = new MemoryEntry(
                "summary-" + UUID.randomUUID().toString().substring(0, 8),
                cleanContent,
                MemoryEntry.MemoryType.SUMMARY,
                Map.of("source", "session-restore"),
                tokenCount > 0 ? tokenCount : MemoryEntry.estimateTokens(cleanContent)
        );
        shortTermMemory.storeCompressedSummary(entry);
    }

    /**
     * 存储关键事实到长期记忆
     */
    public void storeFact(String fact) {
        MemoryEntry entry = new MemoryEntry(
                "fact-" + UUID.randomUUID().toString().substring(0, 8),
                fact,
                MemoryEntry.MemoryType.FACT,
                Map.of("source", "fact"),
                MemoryEntry.estimateTokens(fact)
        );
        longTermMemory.store(entry);
    }

    /**
     * 检索与查询最相关的记忆
     */
    public List<MemoryEntry> retrieveRelevant(String query, int limit) {
        return retriever.retrieve(query, limit);
    }

    /**
     * 构建用于 LLM 的记忆上下文
     */
    public String buildContextForQuery(String query, int maxTokens) {
        return retriever.buildContextForQuery(query, maxTokens);
    }

    /**
     * 记录 token 使用
     */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens);
    }

    /**
     * 检查并触发压缩（由 Agent 在 LLM 调用前主动调用）
     *
     * @return 是否执行了压缩
     */
    public boolean compressIfNeeded() {
        // 长上下文模式下跳过摘要压缩（窗口足够大，不需要压缩）
        if (contextMode == ContextMode.LONG) {
            return false;
        }
        if (!tokenBudget.needsCompression(shortTermMemory)) {
            return false;
        }
        System.out.println("📦 短期记忆接近预算上限，触发压缩...");
        String summary = compressor.compress(shortTermMemory);
        if (summary != null) {
            System.out.println("   压缩完成，摘要: " +
                    summary.substring(0, Math.min(100, summary.length())) + "...");
        }
        return summary != null;
    }

    /**
     * 清空短期记忆（保留长期记忆）
     */
    public void clearShortTerm() {
        shortTermMemory.clear();
    }

    /**
     * 清空长期记忆
     */
    public void clearLongTerm() {
        longTermMemory.clear();
    }

    /**
     * 获取记忆系统的整体状态
     */
    public String getSystemStatus() {
        return shortTermMemory.getStatusSummary() + "\n" +
                longTermMemory.getStatusSummary() + "\n" +
                tokenBudget.getUsageReport();
    }

    public ContextMode getContextMode() {
        return contextMode;
    }

    public List<SessionMessage> exportToSession() {
        List<SessionMessage> messages = new ArrayList<>();
        for (MemoryEntry entry : shortTermMemory.getAll()) {
            SessionMessage msg = new SessionMessage();
            msg.setContent(entry.getContent());
            msg.setTimestamp(entry.getTimestamp().toEpochMilli());
            msg.setTokenCount(entry.getTokenCount());

            Map<String, String> meta = entry.getMetadata();
            String source = meta.getOrDefault("source", "unknown");
            msg.setRole(switch (source) {
                case "user" -> "user";
                case "assistant" -> "assistant";
                case "tool" -> "tool";
                default -> "system";
            });
            if ("tool".equals(source)) {
                msg.setToolName(meta.get("toolName"));
            }
            messages.add(msg);
        }
        for (MemoryEntry entry : shortTermMemory.getCompressedSummaries()) {
            SessionMessage msg = new SessionMessage();
            msg.setRole("system");
            msg.setContent("[compressed] " + entry.getContent());
            msg.setTimestamp(entry.getTimestamp().toEpochMilli());
            msg.setTokenCount(entry.getTokenCount());
            messages.add(msg);
        }
        return messages;
    }

    public void loadFromSession(com.yucli.session.Session session) {
        shortTermMemory.clear();
        if (session.getMessages() == null) return;
        for (SessionMessage msg : session.getMessages()) {
            if ("user".equals(msg.getRole())) {
                addUserMessage(msg.getContent());
            } else if ("assistant".equals(msg.getRole())) {
                addAssistantMessage(msg.getContent());
            } else if ("tool".equals(msg.getRole()) && msg.getToolName() != null) {
                addToolResult(msg.getToolName(), msg.getContent());
            } else if ("system".equals(msg.getRole())) {
                addCompressedSummary(msg.getContent(), msg.getTokenCount());
            }
        }
    }

    // Getter
    public ConversationMemory getShortTermMemory() { return shortTermMemory; }
    public LongTermMemory getLongTermMemory() { return longTermMemory; }
    public TokenBudget getTokenBudget() { return tokenBudget; }
}
