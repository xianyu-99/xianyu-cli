package com.yucli.hook;

import com.yucli.llm.LlmClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class HookPromptExecutor {
    private static final String SYSTEM_PROMPT = """
            You are a YuCLI hook decision engine. Return exactly one JSON object and no Markdown.
            Valid outputs:
            {"decision":"allow"}
            {"decision":"deny","reason":"..."}
            {"decision":"modify","reason":"...","arguments":{...}}
            For modify decisions, "arguments" must replace the hook payload arguments object.
            """;

    private volatile LlmClient llmClient;

    HookPromptExecutor() {
    }

    HookPromptExecutor(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    HookPromptResult execute(String prompt, String payloadJson, int timeoutSeconds) {
        LlmClient client = llmClient;
        if (client == null) {
            return new HookPromptResult("", false, false,
                    "hook prompt requires LlmClient but none was configured");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "YuCLI-hook-llm");
            thread.setDaemon(true);
            return thread;
        });
        Future<HookPromptResult> future = null;
        try {
            future = executor.submit(() -> callLlm(client, prompt, payloadJson));
            return future.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }
            return new HookPromptResult("", false, true,
                    "hook prompt timed out after " + timeoutSeconds + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HookPromptResult("", false, false, "hook prompt interrupted");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            return new HookPromptResult("", false, false, message);
        } finally {
            executor.shutdownNow();
        }
    }

    private HookPromptResult callLlm(LlmClient client, String prompt, String payloadJson) throws Exception {
        String content = """
                Hook instruction:
                %s

                Hook payload JSON:
                %s
                """.formatted(prompt == null ? "" : prompt, payloadJson == null ? "{}" : payloadJson);
        LlmClient.ChatResponse response = client.chat(
                List.of(
                        LlmClient.Message.system(SYSTEM_PROMPT),
                        LlmClient.Message.user(content)),
                List.of());
        String output = response == null ? "" : response.content();
        if (output == null || output.isBlank()) {
            return new HookPromptResult("", false, false,
                    "hook prompt returned no structured decision content");
        }
        return new HookPromptResult(output, true, false, null);
    }

    record HookPromptResult(String output, boolean success, boolean timedOut, String error) {
        String failureMessage() {
            if (timedOut || error != null) {
                return error == null ? "hook prompt failed" : error;
            }
            return "hook prompt failed";
        }
    }
}
