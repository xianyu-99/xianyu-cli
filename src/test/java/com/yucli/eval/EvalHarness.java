package com.yucli.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.agent.Agent;
import com.yucli.agent.AgentOrchestrator;
import com.yucli.agent.PlanExecuteAgent;
import com.yucli.config.YuCLIConfig;
import com.yucli.hook.HookConfigLoader;
import com.yucli.hook.HookManager;
import com.yucli.llm.LlmClient;
import com.yucli.llm.LlmClientFactory;
import com.yucli.hitl.HitlHandler;
import com.yucli.hitl.ApprovalResult;
import com.yucli.hitl.ApprovalRequest;
import com.yucli.hitl.HitlToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "YuCLI.eval.enabled", matches = "true")
public class EvalHarness {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void runEvaluation() throws Exception {
        // Load test cases
        File casesFile = new File("src/test/resources/eval/cases.json");
        assertTrue(casesFile.exists(), "Test cases file not found");
        List<EvalTestCase> testCases = mapper.readValue(casesFile, new TypeReference<List<EvalTestCase>>() {});

        // Init LlmClient
        YuCLIConfig config = YuCLIConfig.load();
        LlmClient llmClient = LlmClientFactory.createFromConfig(config);
        if (llmClient == null) {
            throw new IllegalStateException("LlmClient initialization failed. Please check your API key.");
        }

        List<EvalReport> reports = new ArrayList<>();

        for (EvalTestCase testCase : testCases) {
            System.out.println("Running eval case: " + testCase.getId());
            EvalReport report = new EvalReport();
            report.setId(testCase.getId());
            long startMs = System.currentTimeMillis();

            Path tempDir = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), "yucli-eval-");
            String previousAuditDir = System.getProperty("YuCLI.audit.dir");
            try {
                System.setProperty("YuCLI.audit.dir", tempDir.resolve("audit").toString());

                // Setup
                String setupScript = selectScript(
                        testCase.getSetupScript(),
                        testCase.getSetupScriptWindows(),
                        testCase.getSetupScriptUnix());
                if (setupScript != null && !setupScript.isBlank()) {
                    int setupExitCode = runScript(tempDir, setupScript);
                    if (setupExitCode != 0) {
                        throw new IllegalStateException("Setup script returned exit code " + setupExitCode);
                    }
                }

                HookManager hookManager = new HookConfigLoader().load(
                        tempDir,
                        tempDir.resolve(".YuCLI").resolve("hooks.json"));

                // Mock HITL Handler that always approves
                HitlHandler mockHitl = new HitlHandler() {
                    @Override
                    public boolean isEnabled() { return true; }

                    @Override
                    public void setEnabled(boolean enabled) {}

                    @Override
                    public ApprovalResult requestApproval(ApprovalRequest request) {
                        return ApprovalResult.approve();
                    }
                };

                // Run agent
                HitlToolRegistry toolRegistry = new HitlToolRegistry(mockHitl, hookManager);
                toolRegistry.setProjectPath(tempDir.toString());

                String result = runAgent(testCase, llmClient, toolRegistry);
                Files.writeString(tempDir.resolve("agent-result.txt"), result == null ? "" : result);
                System.out.println("Agent Result for " + testCase.getId() + ":\n" + result);

                // Verify
                String verifyScript = selectScript(
                        testCase.getVerifyScript(),
                        testCase.getVerifyScriptWindows(),
                        testCase.getVerifyScriptUnix());
                if (verifyScript != null && !verifyScript.isBlank()) {
                    int exitCode = runScript(tempDir, verifyScript);
                    report.setSuccess(exitCode == 0);
                    if (exitCode != 0) {
                        report.setError("Verify script returned exit code " + exitCode);
                    }
                } else {
                    report.setSuccess(true);
                }

            } catch (Exception e) {
                e.printStackTrace();
                report.setSuccess(false);
                report.setError(e.getMessage());
            } finally {
                report.setDurationMs(System.currentTimeMillis() - startMs);
                reports.add(report);
                System.out.println("Case " + testCase.getId() + " success: " + report.isSuccess());
                try {
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception ignore) {}
                restoreSystemProperty("YuCLI.audit.dir", previousAuditDir);
            }
        }

        // Assert all passed
        long successCount = reports.stream().filter(EvalReport::isSuccess).count();
        System.out.println("Eval completed: " + successCount + "/" + reports.size() + " passed.");
        assertEquals(reports.size(), successCount, "Not all evaluation cases passed.");
    }

    private String runAgent(EvalTestCase testCase, LlmClient llmClient, HitlToolRegistry toolRegistry) {
        String mode = normalizeMode(testCase.getMode());
        return switch (mode) {
            case "plan" -> new PlanExecuteAgent(
                    llmClient,
                    toolRegistry,
                    null,
                    (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.execute())
                    .run(testCase.getInstruction());
            case "team" -> new AgentOrchestrator(llmClient, toolRegistry).run(testCase.getInstruction());
            case "react" -> new Agent(llmClient, toolRegistry).run(testCase.getInstruction());
            default -> throw new IllegalArgumentException("Unsupported eval mode: " + mode);
        };
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "react";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private String selectScript(String genericScript, String windowsScript, String unixScript) {
        boolean isWindows = isWindows();
        String platformScript = isWindows ? windowsScript : unixScript;
        if (platformScript != null && !platformScript.isBlank()) {
            return platformScript;
        }
        return genericScript;
    }

    private int runScript(Path dir, String script) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            Path scriptFile = dir.resolve("script.ps1");
            Files.writeString(scriptFile, script);
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptFile.toAbsolutePath().toString());
            pb.directory(dir.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return process.waitFor();
        } else {
            Path scriptFile = dir.resolve("script.sh");
            Files.writeString(scriptFile, script);
            ProcessBuilder pb = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString());
            pb.directory(dir.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return process.waitFor();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
