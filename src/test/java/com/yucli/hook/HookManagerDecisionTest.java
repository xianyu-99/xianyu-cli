package com.yucli.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookManagerDecisionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preToolUseDeniesFromStructuredStdout(@TempDir Path tempDir) {
        HookManager manager = new HookManager(
                hooks(new HookDefinition("write_file", List.of("deny"), 3)),
                tempDir,
                stdoutExecutor("{\"decision\":\"deny\",\"reason\":\"blocked by hook\"}"));

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"blocked.txt\",\"content\":\"no\"}");

        assertFalse(decision.allowed());
        assertEquals(HookDecision.Decision.DENY, decision.decision());
        assertEquals("blocked by hook", decision.reason());
    }

    @Test
    void preToolUseModifyUpdatesDecisionAndLaterPayloads(@TempDir Path tempDir) throws Exception {
        List<String> payloads = new ArrayList<>();
        HookCommandExecutor executor = new HookCommandExecutor() {
            private int calls;

            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                payloads.add(inputJson);
                calls++;
                if (calls == 1) {
                    return success("""
                            {"decision":"modify","arguments":{"path":"modified.txt","content":"after"}}
                            """);
                }
                return success("{\"decision\":\"allow\"}");
            }
        };
        HookManager manager = new HookManager(
                hooks(new HookDefinition("write_file", List.of("first", "second"), 3)),
                tempDir,
                executor);

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"original.txt\",\"content\":\"before\"}");

        assertTrue(decision.allowed());
        assertTrue(decision.modified());
        JsonNode modified = MAPPER.readTree(decision.argumentsJsonOr("{}"));
        assertEquals("modified.txt", modified.path("path").asText());
        assertEquals("after", modified.path("content").asText());

        assertEquals(2, payloads.size());
        JsonNode secondPayload = MAPPER.readTree(payloads.get(1));
        assertEquals("modified.txt", secondPayload.path("arguments").path("path").asText());
        assertEquals("after", secondPayload.path("arguments").path("content").asText());
        JsonNode rawArguments = MAPPER.readTree(secondPayload.path("arguments_raw").asText());
        assertEquals("modified.txt", rawArguments.path("path").asText());
    }

    @Test
    void successfulNonJsonStdoutKeepsLegacyAllowBehavior(@TempDir Path tempDir) {
        HookManager manager = new HookManager(
                hooks(new HookDefinition("*", List.of("legacy"), 3)),
                tempDir,
                stdoutExecutor("legacy output"));

        HookDecision decision = manager.runPreToolUse("list_dir", "{\"path\":\".\"}");

        assertTrue(decision.allowed());
        assertFalse(decision.modified());
        assertEquals(HookDecision.Decision.ALLOW, decision.decision());
    }

    @Test
    void userPromptSubmitCanModifyPromptAndCarriesLifecyclePayload(@TempDir Path tempDir) throws Exception {
        List<String> payloads = new ArrayList<>();
        HookCommandExecutor executor = new HookCommandExecutor() {
            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                payloads.add(inputJson);
                return success("""
                        {"decision":"modify","arguments":{"prompt":"modified prompt","mode":"team"}}
                        """);
            }
        };
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.USER_PROMPT_SUBMIT, List.of(new HookDefinition("plan", List.of("rewrite"), 3)));
        HookManager manager = new HookManager(hooks, tempDir, executor);

        HookDecision decision = manager.runUserPromptSubmit("original prompt", "plan", "cli", "SWITCH_PLAN");

        assertTrue(decision.allowed());
        assertTrue(decision.modified());
        assertEquals("modified prompt", decision.arguments().path("prompt").asText());
        JsonNode payload = MAPPER.readTree(payloads.get(0));
        assertEquals("UserPromptSubmit", payload.path("event").asText());
        assertEquals("plan", payload.path("hook_target").asText());
        assertEquals("original prompt", payload.path("prompt").asText());
        assertEquals("cli", payload.path("source").asText());
        assertEquals("SWITCH_PLAN", payload.path("command_type").asText());
    }

    @Test
    void lifecycleHookFailureOnlyWarns(@TempDir Path tempDir) {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("react");
        definition.setUrl("https://hooks.example/agent-start");
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.AGENT_START, List.of(definition));
        HookManager manager = new HookManager(
                hooks,
                tempDir,
                stdoutExecutor(""),
                new RecordingHttpClient(new HookHttpClient.HookHttpResult(
                        500,
                        "nope",
                        false,
                        null,
                        "https://hooks.example/agent-start")),
                null);

        String stderr = captureStderr(() -> manager.runAgentStart("react", "hello", "agent"));

        assertTrue(stderr.contains("[Hook] AgentStart hook failed"), stderr);
        assertTrue(stderr.contains("500"), stderr);
    }

    @Test
    void preToolUseDeniesFromHttpResponse(@TempDir Path tempDir) throws Exception {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("write_file");
        definition.setUrl("https://hooks.example/pre");
        RecordingHttpClient httpClient = new RecordingHttpClient(
                new HookHttpClient.HookHttpResult(
                        200,
                        "{\"decision\":\"deny\",\"reason\":\"blocked by http hook\"}",
                        false,
                        null,
                        "https://hooks.example/pre"));
        HookManager manager = new HookManager(
                hooks(definition),
                tempDir,
                stdoutExecutor(""),
                httpClient,
                null);

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"blocked.txt\",\"content\":\"no\"}");

        assertFalse(decision.allowed());
        assertEquals("blocked by http hook", decision.reason());
        assertEquals(1, httpClient.payloads.size());
        JsonNode payload = MAPPER.readTree(httpClient.payloads.get(0));
        assertEquals("PreToolUse", payload.path("event").asText());
        assertEquals("write_file", payload.path("tool_name").asText());
        assertEquals("blocked.txt", payload.path("arguments").path("path").asText());
    }

    @Test
    void preToolUseHttpModifyFeedsPromptHookWithoutTools(@TempDir Path tempDir) throws Exception {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("write_file");
        definition.setUrl("https://hooks.example/pre");
        definition.setPrompt("Decide whether the modified write is safe.");
        RecordingHttpClient httpClient = new RecordingHttpClient(
                new HookHttpClient.HookHttpResult(
                        200,
                        "{\"decision\":\"modify\",\"arguments\":{\"path\":\"modified.txt\",\"content\":\"after\"}}",
                        false,
                        null,
                        "https://hooks.example/pre"));
        RecordingLlmClient llmClient = new RecordingLlmClient("{\"decision\":\"allow\"}");
        HookManager manager = new HookManager(
                hooks(definition),
                tempDir,
                stdoutExecutor(""),
                httpClient,
                llmClient);

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"original.txt\",\"content\":\"before\"}");

        assertTrue(decision.allowed());
        assertTrue(decision.modified());
        JsonNode modified = MAPPER.readTree(decision.argumentsJsonOr("{}"));
        assertEquals("modified.txt", modified.path("path").asText());
        assertEquals(1, llmClient.messages.size());
        assertTrue(llmClient.tools.get(0).isEmpty(), "prompt hook must not pass tools to LLM");
        assertTrue(llmClient.messages.get(0).get(1).content().contains("\"modified.txt\""));
    }

    @Test
    void httpHookOptionsUseExtendedClient(@TempDir Path tempDir) {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("write_file");
        definition.setUrl("https://hooks.example/pre");
        definition.setHeaders(Map.of("X-Team", "platform"));
        definition.setAuthToken("hook-token");
        definition.setSignatureSecret("hmac-secret");
        definition.setRetryCount(2);
        definition.setRetryBackoffMillis(25L);
        RecordingHttpClient httpClient = new RecordingHttpClient(
                new HookHttpClient.HookHttpResult(
                        200,
                        "{\"decision\":\"allow\"}",
                        false,
                        null,
                        "https://hooks.example/pre"));
        HookManager manager = new HookManager(
                hooks(definition),
                tempDir,
                stdoutExecutor(""),
                httpClient,
                null);

        HookDecision decision = manager.runPreToolUse(
                "write_file",
                "{\"path\":\"safe.txt\",\"content\":\"ok\"}");

        assertTrue(decision.allowed());
        assertEquals(1, httpClient.headers.size());
        assertEquals("platform", httpClient.headers.get(0).get("X-Team"));
        assertEquals("Bearer hook-token", httpClient.headers.get(0).get("Authorization"));
        assertEquals("hmac-secret", httpClient.signatureSecrets.get(0));
        assertEquals(2, httpClient.retryCounts.get(0));
        assertEquals(25L, httpClient.retryBackoffMillis.get(0));
    }

    @Test
    void postToolUseHttpFailureOnlyWarns(@TempDir Path tempDir) {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("*");
        definition.setUrl("https://hooks.example/post");
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.POST_TOOL_USE, List.of(definition));
        HookManager manager = new HookManager(
                hooks,
                tempDir,
                stdoutExecutor(""),
                new RecordingHttpClient(new HookHttpClient.HookHttpResult(
                        500,
                        "nope",
                        false,
                        null,
                        "https://hooks.example/post")),
                null);

        String stderr = captureStderr(() ->
                manager.runPostToolUse("list_dir", "{\"path\":\".\"}", "ok", 1));

        assertTrue(stderr.contains("[Hook] PostToolUse hook failed"), stderr);
        assertTrue(stderr.contains("500"), stderr);
    }

    @Test
    void asyncLifecycleHookDoesNotBlockCaller(@TempDir Path tempDir) throws Exception {
        HookDefinition definition = new HookDefinition();
        definition.setMatcher("react");
        definition.setCommand("slow");
        definition.setAsync(true);
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.AGENT_FINISH, List.of(definition));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        HookCommandExecutor executor = new HookCommandExecutor() {
            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                calls.incrementAndGet();
                return success("");
            }
        };
        HookManager manager = new HookManager(hooks, tempDir, executor);

        long start = System.nanoTime();
        manager.runAgentFinish("react", "input", "result", 1, "", false);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 1_000, "async hook should not block caller for latch release");
        assertTrue(started.await(1, TimeUnit.SECONDS), "async hook should start in background");
        release.countDown();
        assertTrue(manager.awaitAsyncHooks(2_000));
        assertEquals(1, calls.get());
    }

    @Test
    void promptHookWithoutLlmDeniesBlockingAndWarnsNonBlocking(@TempDir Path tempDir) {
        HookDefinition pre = new HookDefinition();
        pre.setMatcher("write_file");
        pre.setPrompt("decide");
        HookDefinition post = new HookDefinition();
        post.setMatcher("*");
        post.setPrompt("audit");
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(pre));
        hooks.put(HookEvent.POST_TOOL_USE, List.of(post));
        HookManager manager = new HookManager(
                hooks,
                tempDir,
                stdoutExecutor(""),
                new RecordingHttpClient(),
                null);

        HookDecision decision = manager.runPreToolUse("write_file", "{\"path\":\"a.txt\"}");
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("LlmClient"), decision.reason());

        String stderr = captureStderr(() ->
                manager.runPostToolUse("list_dir", "{\"path\":\".\"}", "ok", 1));
        assertTrue(stderr.contains("LlmClient"), stderr);
    }

    @Test
    void statusReportsConfiguredHookSummary(@TempDir Path tempDir) {
        HookDefinition pre = new HookDefinition("write_file", List.of("echo pre"), 3);
        pre.setUrl("https://hooks.example/pre");
        pre.setPrompts(List.of("review", "audit"));
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(pre));
        hooks.put(HookEvent.POST_TOOL_USE, List.of(
                new HookDefinition("*", List.of("echo post", "echo audit"), 0)));
        HookManager manager = new HookManager(hooks, tempDir, stdoutExecutor(""));

        HookManager.HookStatus status = manager.status();

        assertTrue(status.enabled());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), status.projectPath());
        assertEquals(2, status.totalHooks());
        assertEquals(1, status.hookCounts().get("PreToolUse"));
        assertEquals(1, status.hookCounts().get("PostToolUse"));
        assertEquals("PreToolUse", status.hooks().get(0).event());
        assertEquals("write_file", status.hooks().get(0).matcher());
        assertEquals(List.of("echo pre"), status.hooks().get(0).commands());
        assertEquals(3, status.executorCounts().get("command"));
        assertEquals(1, status.executorCounts().get("http"));
        assertEquals(2, status.executorCounts().get("prompt"));
        assertEquals(1, status.hooks().get(0).httpCount());
        assertEquals(2, status.hooks().get(0).promptCount());
        assertEquals(10, status.hooks().get(1).timeoutSeconds());
    }

    private static Map<HookEvent, List<HookDefinition>> hooks(HookDefinition definition) {
        Map<HookEvent, List<HookDefinition>> hooks = new EnumMap<>(HookEvent.class);
        hooks.put(HookEvent.PRE_TOOL_USE, List.of(definition));
        return hooks;
    }

    private static String captureStderr(Runnable runnable) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true)) {
            System.setErr(capture);
            runnable.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString();
    }

    private static HookCommandExecutor stdoutExecutor(String output) {
        return new HookCommandExecutor() {
            @Override
            HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
                return success(output);
            }
        };
    }

    private static HookCommandExecutor.HookCommandResult success(String output) {
        return new HookCommandExecutor.HookCommandResult(0, output, false, null);
    }

    private static class RecordingHttpClient extends HookHttpClient {
        private final Queue<HookHttpResult> results = new ArrayDeque<>();
        private final List<String> payloads = new ArrayList<>();
        private final List<Map<String, String>> headers = new ArrayList<>();
        private final List<String> signatureSecrets = new ArrayList<>();
        private final List<Integer> retryCounts = new ArrayList<>();
        private final List<Long> retryBackoffMillis = new ArrayList<>();

        RecordingHttpClient(HookHttpResult... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        HookHttpResult post(String url, String inputJson, int timeoutSeconds) {
            payloads.add(inputJson);
            if (results.isEmpty()) {
                return new HookHttpResult(200, "", false, null, url);
            }
            return results.remove();
        }

        @Override
        HookHttpResult post(String url, String inputJson, int timeoutSeconds,
                            Map<String, String> headers, String signatureSecret,
                            int retryCount, long retryBackoffMillis) {
            this.headers.add(headers == null ? Map.of() : Map.copyOf(headers));
            this.signatureSecrets.add(signatureSecret);
            this.retryCounts.add(retryCount);
            this.retryBackoffMillis.add(retryBackoffMillis);
            return post(url, inputJson, timeoutSeconds);
        }
    }

    private static class RecordingLlmClient implements LlmClient {
        private final String output;
        private final List<List<Message>> messages = new ArrayList<>();
        private final List<List<Tool>> tools = new ArrayList<>();

        RecordingLlmClient(String output) {
            this.output = output;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            this.messages.add(messages);
            this.tools.add(tools);
            return new ChatResponse("assistant", output, null, 1, 1);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
                throws IOException {
            return chat(messages, tools);
        }

        @Override
        public String getModelName() {
            return "fake";
        }

        @Override
        public String getProviderName() {
            return "fake";
        }
    }
}
