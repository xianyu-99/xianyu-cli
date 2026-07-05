package com.yucli.runtime.headless;

import com.yucli.agent.Agent;
import com.yucli.llm.LlmClient;

import java.util.List;
import java.util.Objects;

public final class HeadlessRunner {

    private final HeadlessTaskExecutor executor;

    public HeadlessRunner(HeadlessTaskExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static HeadlessRunner withReactAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent");
        return new HeadlessRunner((task, mode) -> {
            requireReactMode(mode);
            return agent.run(task);
        });
    }

    public static HeadlessRunner withMinimalReactLlmClient(LlmClient llmClient) {
        Objects.requireNonNull(llmClient, "llmClient");
        return new HeadlessRunner((task, mode) -> {
            requireReactMode(mode);
            LlmClient.ChatResponse response = llmClient.chat(
                    List.of(LlmClient.Message.user(task)),
                    List.of(),
                    LlmClient.StreamListener.NO_OP
            );
            return response.content();
        });
    }

    public HeadlessRunResult run(String task, String mode) {
        long startNanos = System.nanoTime();
        HeadlessRunMode parsedMode;
        try {
            parsedMode = HeadlessRunMode.parse(mode);
            return run(new HeadlessRunRequest(task, parsedMode), startNanos);
        } catch (Exception e) {
            return HeadlessRunResult.failure(task, normalizeModeForFailure(mode), e, elapsedMillis(startNanos));
        }
    }

    public HeadlessRunResult run(HeadlessRunRequest request) {
        return run(request, System.nanoTime());
    }

    public HeadlessRunResult runAndWrite(HeadlessRunRequest request, JsonlEventWriter writer) throws java.io.IOException {
        Objects.requireNonNull(writer, "writer");
        HeadlessRunResult result = run(request);
        writer.writeResult(result);
        return result;
    }

    private HeadlessRunResult run(HeadlessRunRequest request, long startNanos) {
        Objects.requireNonNull(request, "request");
        try {
            String output = executor.run(request.task(), request.mode());
            return HeadlessRunResult.success(request.task(), request.mode(), output, elapsedMillis(startNanos));
        } catch (Exception e) {
            return HeadlessRunResult.failure(request.task(), request.mode(), e, elapsedMillis(startNanos));
        }
    }

    private static void requireReactMode(HeadlessRunMode mode) {
        if (mode != HeadlessRunMode.REACT) {
            throw new UnsupportedOperationException("Headless mode not implemented yet: " + mode.value());
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String normalizeModeForFailure(String mode) {
        return mode == null || mode.isBlank() ? HeadlessRunMode.REACT.value() : mode.trim().toLowerCase();
    }
}
