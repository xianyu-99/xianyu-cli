package com.yucli.agent;

import com.yucli.agent.config.AgentProfile;
import com.yucli.llm.GLMClient;
import com.yucli.llm.LlmClient;
import com.yucli.runtime.CancellationContext;
import com.yucli.runtime.CancellationToken;
import com.yucli.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentTest {

    @Test
    void shouldOnlyEnableToolsForWorker() throws Exception {
        assertFalse(invokeShouldUseTools(new SubAgent("planner", AgentRole.PLANNER,
                new GLMClient("test-key"), new ToolRegistry())));
        assertTrue(invokeShouldUseTools(new SubAgent("worker", AgentRole.WORKER,
                new GLMClient("test-key"), new ToolRegistry())));
        assertFalse(invokeShouldUseTools(new SubAgent("reviewer", AgentRole.REVIEWER,
                new GLMClient("test-key"), new ToolRegistry())));
    }

    @Test
    void shouldAppendProfileInstructionsAndToolWhitelistToSystemPrompt() {
        AgentProfile profile = new AgentProfile();
        profile.setName("repo-reader");
        profile.setRole("worker");
        profile.setInstructions("PROFILE_INSTRUCTION_MARKER");
        profile.setTools(List.of("read_file", "search_code"));

        CapturingSystemPromptClient llm = new CapturingSystemPromptClient();
        SubAgent worker = SubAgent.fromProfile(profile, llm, new ToolRegistry());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        AgentMessage result = worker.execute(AgentMessage.task("orchestrator", "inspect repo"), ps);

        assertEquals("repo-reader", result.fromAgent());
        assertTrue(llm.systemPrompt.contains("Profile custom instructions"));
        assertTrue(llm.systemPrompt.contains("PROFILE_INSTRUCTION_MARKER"));
        assertTrue(llm.systemPrompt.contains("Profile tool whitelist"));
        assertTrue(llm.systemPrompt.contains("read_file"));
        assertTrue(llm.systemPrompt.contains("search_code"));
    }

    @Test
    void shouldFilterToolDefinitionsWithProfileWhitelist() {
        AgentProfile profile = new AgentProfile();
        profile.setName("repo-reader");
        profile.setRole("worker");
        profile.setInstructions("Only read files");
        profile.setTools(List.of("read_file"));

        CapturingSystemPromptClient llm = new CapturingSystemPromptClient();
        SubAgent worker = SubAgent.fromProfile(profile, llm, new ToolRegistry());

        worker.execute(AgentMessage.task("orchestrator", "inspect repo"),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertNotNull(llm.capturedTools);
        assertEquals(List.of("read_file"), llm.capturedTools.stream().map(LlmClient.Tool::name).toList());
    }

    @Test
    void shouldApplyProfileDeniedToolsToToolDefinitions() {
        AgentProfile profile = new AgentProfile();
        profile.setName("safe-worker");
        profile.setRole("worker");
        profile.setInstructions("No shell access");
        profile.setDeniedTools(List.of("execute_command"));
        profile.setAllowedCommands(List.of("git status"));

        CapturingSystemPromptClient llm = new CapturingSystemPromptClient();
        SubAgent worker = SubAgent.fromProfile(profile, llm, new ToolRegistry());

        worker.execute(AgentMessage.task("orchestrator", "inspect repo"),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        List<String> names = llm.capturedTools.stream().map(LlmClient.Tool::name).toList();
        assertFalse(names.contains("execute_command"));
        assertTrue(names.contains("read_file"));
        assertTrue(llm.systemPrompt.contains("Profile execution scope"));
        assertTrue(llm.systemPrompt.contains("execute_command"));
        assertTrue(llm.systemPrompt.contains("git status"));
    }

    @Test
    void shouldRejectUnauthorizedProfileToolCallWithoutTouchingDelegateRegistry() {
        AgentProfile profile = new AgentProfile();
        profile.setName("limited-worker");
        profile.setRole("worker");
        profile.setInstructions("Only read files");
        profile.setTools(List.of("read_file"));

        UnauthorizedThenFinalClient llm = new UnauthorizedThenFinalClient();
        CountingToolRegistry tools = new CountingToolRegistry();
        SubAgent worker = SubAgent.fromProfile(profile, llm, tools);

        AgentMessage result = worker.execute(AgentMessage.task("orchestrator", "run a command"),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertEquals(AgentMessage.Type.RESULT, result.type());
        assertEquals(0, tools.executeToolsCalls.get(), "Unauthorized tool must not reach delegate registry");
        assertEquals(2, llm.chatCalls.get(), "SubAgent should continue after returning denial as tool result");
        assertTrue(llm.denialToolMessage.contains("工具调用被拒绝"));
        assertTrue(llm.denialToolMessage.contains("execute_command"));
    }

    @Test
    void shouldKeepDefaultToolDefinitionsWhenProfileWhitelistIsEmpty() {
        CapturingSystemPromptClient llm = new CapturingSystemPromptClient();
        ToolRegistry tools = new ToolRegistry();
        SubAgent worker = new SubAgent("default-worker", AgentRole.WORKER, llm, tools,
                "No custom restrictions", List.of());

        worker.execute(AgentMessage.task("orchestrator", "inspect repo"),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        List<String> expectedToolNames = tools.getToolDefinitions().stream().map(LlmClient.Tool::name).toList();
        List<String> actualToolNames = llm.capturedTools.stream().map(LlmClient.Tool::name).toList();
        assertEquals(expectedToolNames, actualToolNames);
    }

    @Test
    void shouldRouteLateReasoningToSupplementalSection() {
        // 模拟服务器先下发 content、再追加 reasoning 的情况
        ScriptedStreamClient llm = new ScriptedStreamClient(listener -> {
            listener.onContentDelta("最终答案内容");
            listener.onReasoningDelta("这段思考在答案之后才到");
        });
        SubAgent worker = new SubAgent("test-worker", AgentRole.WORKER, llm, new ToolRegistry());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        worker.execute(AgentMessage.task("orchestrator", "随便任务"), ps);
        ps.flush();

        String output = baos.toString(StandardCharsets.UTF_8);
        int contentHeadingIdx = output.indexOf("执行输出");
        int contentBodyIdx = output.indexOf("最终答案内容");
        int supplementalHeadingIdx = output.indexOf("补充思考");
        int lateReasoningIdx = output.indexOf("这段思考在答案之后才到");

        assertTrue(contentHeadingIdx >= 0, "执行输出 heading should appear: " + output);
        assertTrue(contentBodyIdx > contentHeadingIdx, "content body should be under 执行输出");
        assertTrue(supplementalHeadingIdx > contentBodyIdx,
                "late reasoning must appear under 补充思考 heading AFTER content, not mixed in");
        assertTrue(lateReasoningIdx > supplementalHeadingIdx,
                "late reasoning body should follow 补充思考 heading");
    }

    @Test
    void shouldPrintFreshHeadingsAcrossToolIterations() {
        // 两轮迭代：第一轮 content + tool_call（narration），第二轮纯 content（final answer）
        // resetBetweenIterations 被调用后，第二轮应该重新打印「执行思考」和「执行输出」标题
        MultiCallStreamClient llm = new MultiCallStreamClient(List.of(
                // 迭代 1：reasoning + content narration + tool_call
                new CallScript(
                        listener -> {
                            listener.onReasoningDelta("准备调用工具……");
                            listener.onContentDelta("我来调用 list_dir 工具");
                        },
                        new LlmClient.ChatResponse(
                                "assistant",
                                "我来调用 list_dir 工具",
                                "准备调用工具……",
                                List.of(new LlmClient.ToolCall(
                                        "call_1",
                                        new LlmClient.ToolCall.Function("list_dir", "{\"path\":\".\"}")
                                )),
                                10, 5
                        )
                ),
                // 迭代 2：reasoning + content（最终答案），无 tool_call
                new CallScript(
                        listener -> {
                            listener.onReasoningDelta("分析完成");
                            listener.onContentDelta("目录列出完毕");
                        },
                        new LlmClient.ChatResponse(
                                "assistant",
                                "目录列出完毕",
                                "分析完成",
                                null,
                                8, 3
                        )
                )
        ));
        SubAgent worker = new SubAgent("w1", AgentRole.WORKER, llm, new ToolRegistry());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        worker.execute(AgentMessage.task("orchestrator", "任务"), ps);
        ps.flush();

        String output = baos.toString(StandardCharsets.UTF_8);
        // 两轮各自打印一次「执行思考」
        int firstReasoning = output.indexOf("执行思考");
        int secondReasoning = firstReasoning < 0 ? -1 : output.indexOf("执行思考", firstReasoning + 1);
        assertTrue(firstReasoning >= 0, "第一轮应打印执行思考标题");
        assertTrue(secondReasoning > firstReasoning,
                "工具执行后第二轮应重新打印执行思考标题，实际输出：\n" + output);

        // 「执行输出」同样出现两次
        int firstContent = output.indexOf("执行输出");
        int secondContent = firstContent < 0 ? -1 : output.indexOf("执行输出", firstContent + 1);
        assertTrue(firstContent >= 0, "第一轮应打印执行输出标题");
        assertTrue(secondContent > firstContent,
                "工具执行后第二轮应重新打印执行输出标题，实际输出：\n" + output);
    }

    @Test
    void shouldNotEmitEmptyReasoningHeadingForWhitespaceDeltas() {
        // 仅下发空白 reasoning 然后下发 content —— 不能产生空的"执行思考"标题
        ScriptedStreamClient llm = new ScriptedStreamClient(listener -> {
            listener.onReasoningDelta("  ");
            listener.onReasoningDelta("\n");
            listener.onContentDelta("答案");
        });
        SubAgent worker = new SubAgent("test-worker", AgentRole.WORKER, llm, new ToolRegistry());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        worker.execute(AgentMessage.task("orchestrator", "随便任务"), ps);
        ps.flush();

        String output = baos.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("执行思考"),
                "whitespace-only reasoning should not produce an empty reasoning heading: " + output);
        assertTrue(output.contains("执行输出"), "content heading should still appear");
        assertTrue(output.contains("答案"), "content should still appear");
    }

    @Test
    void shouldStopBeforeToolsWhenCancelledAfterLlmToolCallResponse() {
        CancellationToken token = CancellationContext.startRun();
        CancelThenToolCallClient llm = new CancelThenToolCallClient();
        CountingToolRegistry tools = new CountingToolRegistry();
        SubAgent worker = new SubAgent("cancel-worker", AgentRole.WORKER, llm, tools);

        try {
            AgentMessage result = worker.execute(AgentMessage.task("orchestrator", "cancel after llm"));

            assertEquals(AgentMessage.Type.ERROR, result.type());
            assertTrue(result.content().contains("取消"), "SubAgent should return a cancellation error");
            assertEquals(0, tools.executeToolsCalls.get(), "Cancelled SubAgent must not execute tool calls");
            assertEquals(1, llm.chatCalls.get(), "Cancelled SubAgent must not enter a second LLM round");
        } finally {
            CancellationContext.clear(token);
        }
    }

    private boolean invokeShouldUseTools(SubAgent agent) throws Exception {
        Method method = SubAgent.class.getDeclaredMethod("shouldUseTools");
        method.setAccessible(true);
        return (boolean) method.invoke(agent);
    }

    private static final class CapturingSystemPromptClient extends GLMClient {
        private String systemPrompt = "";
        private List<Tool> capturedTools = List.of();

        private CapturingSystemPromptClient() {
            super("test-key");
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            capturedTools = tools;
            systemPrompt = messages.stream()
                    .filter(message -> "system".equals(message.role()))
                    .findFirst()
                    .map(Message::content)
                    .orElse("");
            listener.onContentDelta("done");
            return new ChatResponse("assistant", "done", null, null, 10, 5);
        }
    }

    /**
     * 可编排 delta 下发顺序的 stub GLM 客户端，用于测试流式渲染在异常顺序下的行为。
     */
    private static final class ScriptedStreamClient extends GLMClient {
        private final Consumer<StreamListener> script;

        private ScriptedStreamClient(Consumer<StreamListener> script) {
            super("test-key");
            this.script = script;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            script.accept(listener);
            // 返回空 toolCalls 让 SubAgent 作为最终结果返回
            return new ChatResponse("assistant", "最终答案内容", "这段思考在答案之后才到", null, 10, 5);
        }
    }

    /**
     * 多轮次脚本：每次 chat() 调用按顺序消费一条 CallScript，支持测试 tool-call 分支的后续迭代。
     */
    private record CallScript(Consumer<LlmClient.StreamListener> streamScript, LlmClient.ChatResponse response) {}

    private static final class MultiCallStreamClient extends GLMClient {
        private final java.util.Iterator<CallScript> iter;

        private MultiCallStreamClient(List<CallScript> scripts) {
            super("test-key");
            this.iter = scripts.iterator();
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            if (!iter.hasNext()) {
                throw new IOException("脚本已耗尽，未预设第 N 次调用");
            }
            CallScript next = iter.next();
            next.streamScript().accept(listener);
            return next.response();
        }
    }

    private static final class CancelThenToolCallClient extends GLMClient {
        private final AtomicInteger chatCalls = new AtomicInteger();

        private CancelThenToolCallClient() {
            super("test-key");
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) throws IOException {
            int call = chatCalls.incrementAndGet();
            if (call > 1) {
                throw new IOException("SubAgent should not call LLM again after cancellation");
            }
            CancellationContext.current().cancel();
            return new ChatResponse(
                    "assistant",
                    "will call tool",
                    null,
                    List.of(new ToolCall("call_1", new ToolCall.Function("list_dir", "{\"path\":\".\"}"))),
                    10,
                    5
            );
        }
    }

    private static final class UnauthorizedThenFinalClient extends GLMClient {
        private final AtomicInteger chatCalls = new AtomicInteger();
        private String denialToolMessage = "";

        private UnauthorizedThenFinalClient() {
            super("test-key");
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            return chat(messages, tools, StreamListener.NO_OP);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            int call = chatCalls.incrementAndGet();
            if (call == 1) {
                return new ChatResponse(
                        "assistant",
                        "will call unauthorized tool",
                        null,
                        List.of(new ToolCall("call_1", new ToolCall.Function(
                                "execute_command", "{\"command\":\"echo denied\"}"))),
                        10,
                        5
                );
            }

            denialToolMessage = messages.stream()
                    .filter(message -> "tool".equals(message.role()))
                    .findFirst()
                    .map(Message::content)
                    .orElse("");
            listener.onContentDelta("done");
            return new ChatResponse("assistant", "done", null, null, 10, 5);
        }
    }

    private static final class CountingToolRegistry extends ToolRegistry {
        private final AtomicInteger executeToolsCalls = new AtomicInteger();

        @Override
        public List<ToolExecutionResult> executeTools(List<ToolInvocation> invocations) {
            executeToolsCalls.incrementAndGet();
            return List.of();
        }
    }
}
