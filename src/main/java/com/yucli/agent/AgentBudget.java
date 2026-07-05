package com.yucli.agent;

import com.yucli.llm.LlmClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Exit budget for a single ReAct run.
 *
 * <p>This is a task-level guardrail, not the model's context-window limit. It
 * prevents runaway tool loops and unexpected token spend while allowing the
 * active context to be managed separately by compaction and result trimming.</p>
 */
public class AgentBudget {

    public enum ExitReason {
        WITHIN_BUDGET,
        CONTEXT_WINDOW_NEAR_LIMIT,
        TOKEN_BUDGET_EXCEEDED,
        STAGNATION_DETECTED,
        HARD_ITERATION_LIMIT
    }

    private static final int DEFAULT_TOKEN_BUDGET = 258_000;
    private static final int DEFAULT_CONTEXT_WINDOW = 128_000;
    private static final int DEFAULT_STAGNATION_WINDOW = 3;
    private static final int DEFAULT_HARD_MAX_ITERATIONS = 50;
    private static final double DEFAULT_CONTEXT_WATERMARK_RATIO = 0.92;

    private final int tokenBudget;
    private final int contextWindow;
    private final double contextWatermarkRatio;
    private final int contextTokenWatermark;
    private final int stagnationWindow;
    private final int hardMaxIterations;

    private final Deque<String> recentToolSignatures = new ArrayDeque<>();
    private int iteration;
    private int totalInputTokens;
    private int totalOutputTokens;
    private int totalCachedTokens;
    private int lastInputTokens;
    private int maxInputTokens;
    private boolean stagnant;

    public AgentBudget(int tokenBudget, int stagnationWindow, int hardMaxIterations) {
        this(tokenBudget, DEFAULT_CONTEXT_WINDOW, DEFAULT_CONTEXT_WATERMARK_RATIO,
                stagnationWindow, hardMaxIterations);
    }

    public AgentBudget(int tokenBudget, int contextWindow, double contextWatermarkRatio,
                       int stagnationWindow, int hardMaxIterations) {
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive");
        }
        if (contextWatermarkRatio <= 0 || contextWatermarkRatio > 1) {
            throw new IllegalArgumentException("contextWatermarkRatio must be in (0, 1]");
        }
        if (stagnationWindow < 2) {
            throw new IllegalArgumentException("stagnationWindow must be >= 2");
        }
        if (hardMaxIterations <= 0) {
            throw new IllegalArgumentException("hardMaxIterations must be positive");
        }
        this.tokenBudget = tokenBudget;
        this.contextWindow = contextWindow;
        this.contextWatermarkRatio = contextWatermarkRatio;
        this.contextTokenWatermark = Math.max(1, (int) (contextWindow * contextWatermarkRatio));
        this.stagnationWindow = stagnationWindow;
        this.hardMaxIterations = hardMaxIterations;
    }

    public static AgentBudget fromSystemProperties() {
        return new AgentBudget(
                readIntConfig("YuCLI.react.token.budget", "YUCLI_REACT_TOKEN_BUDGET", DEFAULT_TOKEN_BUDGET),
                readIntConfig("YuCLI.react.context.window", "YUCLI_REACT_CONTEXT_WINDOW", DEFAULT_CONTEXT_WINDOW),
                readRatioConfig("YuCLI.react.context.watermark.ratio",
                        "YUCLI_REACT_CONTEXT_WATERMARK_RATIO", DEFAULT_CONTEXT_WATERMARK_RATIO),
                readIntConfig("YuCLI.react.stagnation.window", "YUCLI_REACT_STAGNATION_WINDOW", DEFAULT_STAGNATION_WINDOW),
                readIntConfig("YuCLI.react.hard.max.iterations", "YUCLI_REACT_HARD_MAX_ITERATIONS", DEFAULT_HARD_MAX_ITERATIONS)
        );
    }

    public static AgentBudget fromLlmClient(LlmClient client) {
        int clientContextWindow = client == null ? DEFAULT_CONTEXT_WINDOW : client.maxContextWindow();
        int contextWindow = readIntConfig("YuCLI.react.context.window",
                "YUCLI_REACT_CONTEXT_WINDOW", safeContextWindow(clientContextWindow));
        return new AgentBudget(
                readIntConfig("YuCLI.react.token.budget", "YUCLI_REACT_TOKEN_BUDGET", DEFAULT_TOKEN_BUDGET),
                contextWindow,
                readRatioConfig("YuCLI.react.context.watermark.ratio",
                        "YUCLI_REACT_CONTEXT_WATERMARK_RATIO", DEFAULT_CONTEXT_WATERMARK_RATIO),
                readIntConfig("YuCLI.react.stagnation.window", "YUCLI_REACT_STAGNATION_WINDOW", DEFAULT_STAGNATION_WINDOW),
                readIntConfig("YuCLI.react.hard.max.iterations", "YUCLI_REACT_HARD_MAX_ITERATIONS", DEFAULT_HARD_MAX_ITERATIONS)
        );
    }

    public int beginIteration() {
        return ++iteration;
    }

    public void recordTokens(int inputTokens, int outputTokens) {
        recordTokens(inputTokens, outputTokens, 0);
    }

    public void recordTokens(int inputTokens, int outputTokens, int cachedTokens) {
        int input = Math.max(0, inputTokens);
        int output = Math.max(0, outputTokens);
        int cached = Math.max(0, Math.min(cachedTokens, input));
        this.totalInputTokens += input;
        this.totalOutputTokens += output;
        this.totalCachedTokens += cached;
        this.lastInputTokens = input;
        this.maxInputTokens = Math.max(maxInputTokens, input);
    }

    public void recordToolCalls(List<LlmClient.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            recentToolSignatures.clear();
            return;
        }
        String signature = signatureOf(toolCalls);
        recentToolSignatures.addLast(signature);
        while (recentToolSignatures.size() > stagnationWindow) {
            recentToolSignatures.removeFirst();
        }
        if (recentToolSignatures.size() == stagnationWindow) {
            String first = recentToolSignatures.peekFirst();
            stagnant = recentToolSignatures.stream().allMatch(sig -> sig.equals(first));
        }
    }

    public ExitReason check() {
        if (stagnant) {
            return ExitReason.STAGNATION_DETECTED;
        }
        if (lastInputTokens >= contextTokenWatermark) {
            return ExitReason.CONTEXT_WINDOW_NEAR_LIMIT;
        }
        if (effectiveTokenUsage() >= tokenBudget) {
            return ExitReason.TOKEN_BUDGET_EXCEEDED;
        }
        if (iteration >= hardMaxIterations) {
            return ExitReason.HARD_ITERATION_LIMIT;
        }
        return ExitReason.WITHIN_BUDGET;
    }

    public int iteration() {
        return iteration;
    }

    public int totalInputTokens() {
        return totalInputTokens;
    }

    public int totalOutputTokens() {
        return totalOutputTokens;
    }

    public int totalCachedTokens() {
        return totalCachedTokens;
    }

    public int lastInputTokens() {
        return lastInputTokens;
    }

    public int maxInputTokens() {
        return maxInputTokens;
    }

    public int rawTokenUsage() {
        return totalInputTokens + totalOutputTokens;
    }

    public int effectiveTokenUsage() {
        return Math.max(0, totalInputTokens - totalCachedTokens) + totalOutputTokens;
    }

    public int tokenBudget() {
        return tokenBudget;
    }

    public int contextWindow() {
        return contextWindow;
    }

    public double contextWatermarkRatio() {
        return contextWatermarkRatio;
    }

    public int contextTokenWatermark() {
        return contextTokenWatermark;
    }

    public int hardMaxIterations() {
        return hardMaxIterations;
    }

    public int stagnationWindow() {
        return stagnationWindow;
    }

    public String describeExit(ExitReason reason) {
        return switch (reason) {
            case WITHIN_BUDGET -> "未触发兜底条件";
            case CONTEXT_WINDOW_NEAR_LIMIT -> String.format(Locale.ROOT,
                    "当前上下文接近模型窗口（上一轮输入 %d / %d，水位线 %d），任务被提前收尾；建议 /clear、拆分任务或调高 YUCLI_REACT_CONTEXT_WINDOW",
                    lastInputTokens, contextWindow, contextTokenWatermark);
            case TOKEN_BUDGET_EXCEEDED -> String.format(Locale.ROOT,
                    "Token 预算已用尽（有效 %d / %d，原始 %d，缓存命中 %d），任务被强制收尾",
                    effectiveTokenUsage(), tokenBudget, rawTokenUsage(), totalCachedTokens);
            case STAGNATION_DETECTED -> String.format(Locale.ROOT,
                    "检测到连续 %d 轮重复的工具调用，疑似死循环，已强制收尾",
                    stagnationWindow);
            case HARD_ITERATION_LIMIT -> String.format(Locale.ROOT,
                    "达到硬轮数上限（%d），已强制收尾", hardMaxIterations);
        };
    }

    private static String signatureOf(List<LlmClient.ToolCall> toolCalls) {
        StringBuilder sb = new StringBuilder();
        for (LlmClient.ToolCall tc : toolCalls) {
            sb.append(tc.function().name()).append('|').append(tc.function().arguments()).append(';');
        }
        return sb.toString();
    }

    private static int safeContextWindow(int contextWindow) {
        return contextWindow > 0 ? contextWindow : DEFAULT_CONTEXT_WINDOW;
    }

    private static int readIntConfig(String propertyKey, String envKey, int defaultValue) {
        String raw = System.getProperty(propertyKey);
        if ((raw == null || raw.isBlank()) && envKey != null) {
            raw = System.getenv(envKey);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double readRatioConfig(String propertyKey, String envKey, double defaultValue) {
        String raw = System.getProperty(propertyKey);
        if ((raw == null || raw.isBlank()) && envKey != null) {
            raw = System.getenv(envKey);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed > 0 && parsed <= 1 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
