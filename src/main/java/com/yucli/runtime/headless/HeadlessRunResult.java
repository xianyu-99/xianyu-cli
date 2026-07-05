package com.yucli.runtime.headless;

public record HeadlessRunResult(
        String task,
        String mode,
        boolean success,
        String result,
        String error,
        long durationMs
) {

    public HeadlessRunResult {
        task = task == null ? "" : task;
        mode = mode == null || mode.isBlank() ? HeadlessRunMode.REACT.value() : mode;
        result = result == null ? "" : result;
        error = error == null ? "" : error;
        durationMs = Math.max(0, durationMs);
    }

    public static HeadlessRunResult success(String task, HeadlessRunMode mode, String result, long durationMs) {
        return new HeadlessRunResult(task, mode.value(), true, result, null, durationMs);
    }

    public static HeadlessRunResult failure(String task, HeadlessRunMode mode, Throwable error, long durationMs) {
        return failure(task, mode == null ? null : mode.value(), error, durationMs);
    }

    public static HeadlessRunResult failure(String task, String mode, Throwable error, long durationMs) {
        String message = error == null ? "Unknown headless run error" : error.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "Unknown headless run error" : error.getClass().getSimpleName();
        }
        return new HeadlessRunResult(task, mode, false, "", message, durationMs);
    }
}
