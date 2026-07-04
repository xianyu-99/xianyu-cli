package com.yucli.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yucli.agent.Agent;
import com.yucli.config.YuCLIConfig;
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
import java.util.List;

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

            Path tempDir = Files.createTempDirectory(Path.of("."), "yucli-eval-");
            try {
                // Setup
                if (testCase.getSetupScript() != null && !testCase.getSetupScript().isEmpty()) {
                    runScript(tempDir, testCase.getSetupScript());
                }

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
                HitlToolRegistry toolRegistry = new HitlToolRegistry(mockHitl);
                toolRegistry.setProjectPath(tempDir.toString());

                Agent agent = new Agent(llmClient, toolRegistry);
                String result = agent.run(testCase.getInstruction());
                System.out.println("Agent Result for " + testCase.getId() + ":\n" + result);

                // Verify
                if (testCase.getVerifyScript() != null && !testCase.getVerifyScript().isEmpty()) {
                    int exitCode = runScript(tempDir, testCase.getVerifyScript());
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
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception ignore) {}
            }
        }

        // Assert all passed
        long successCount = reports.stream().filter(EvalReport::isSuccess).count();
        System.out.println("Eval completed: " + successCount + "/" + reports.size() + " passed.");
        assertEquals(reports.size(), successCount, "Not all evaluation cases passed.");
    }

    private int runScript(Path dir, String script) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            Path scriptFile = dir.resolve("script.ps1");
            Files.writeString(scriptFile, script);
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptFile.toString());
            pb.directory(dir.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return process.waitFor();
        } else {
            Path scriptFile = dir.resolve("script.sh");
            Files.writeString(scriptFile, script);
            ProcessBuilder pb = new ProcessBuilder("bash", scriptFile.toString());
            pb.directory(dir.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return process.waitFor();
        }
    }
}
