package com.yucli.hook;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class HookCommandExecutor {
    private static final int MAX_OUTPUT_CHARS = 8_000;

    HookCommandResult execute(String command, String inputJson, Path workDir, int timeoutSeconds) {
        Process process = null;
        ExecutorService outputReader = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "YuCLI-hook-output");
            thread.setDaemon(true);
            return thread;
        });

        try {
            ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
            if (workDir != null) {
                builder.directory(workDir.toFile());
            }
            builder.redirectErrorStream(true);
            process = builder.start();

            Process runningProcess = process;
            Future<String> outputFuture = outputReader.submit(() -> readOutput(runningProcess));
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(inputJson == null ? "{}" : inputJson);
                writer.write(System.lineSeparator());
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                outputFuture.cancel(true);
                return new HookCommandResult(-1, "", true, "hook command timed out after " + timeoutSeconds + "s");
            }

            return new HookCommandResult(process.exitValue(), outputFuture.get(2, TimeUnit.SECONDS), false, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new HookCommandResult(-1, "", false, "hook command interrupted");
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new HookCommandResult(-1, "", false, e.getMessage());
        } finally {
            outputReader.shutdownNow();
        }
    }

    static List<String> shellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("cmd.exe", "/c", command);
        }
        return List.of("bash", "-c", command);
    }

    private static String readOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    int remaining = MAX_OUTPUT_CHARS - output.length();
                    if (line.length() > remaining) {
                        output.append(line, 0, remaining);
                    } else {
                        output.append(line);
                    }
                    output.append(System.lineSeparator());
                }
            }
        }
        if (output.length() >= MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + System.lineSeparator() + "...(hook output truncated)";
        }
        return output.toString();
    }

    record HookCommandResult(int exitCode, String output, boolean timedOut, String error) {
        boolean success() {
            return exitCode == 0 && !timedOut && error == null;
        }

        String failureMessage() {
            if (timedOut || error != null) {
                return error == null ? "hook command failed" : error;
            }
            String trimmed = output == null ? "" : output.trim();
            return trimmed.isBlank()
                    ? "hook command exited with code " + exitCode
                    : trimmed;
        }
    }
}
