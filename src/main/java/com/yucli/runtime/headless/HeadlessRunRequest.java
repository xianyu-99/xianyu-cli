package com.yucli.runtime.headless;

public record HeadlessRunRequest(String task, HeadlessRunMode mode) {

    public HeadlessRunRequest {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("Headless task must not be blank");
        }
        if (mode == null) {
            mode = HeadlessRunMode.REACT;
        }
    }

    public static HeadlessRunRequest react(String task) {
        return new HeadlessRunRequest(task, HeadlessRunMode.REACT);
    }

    public static HeadlessRunRequest of(String task, String mode) {
        return new HeadlessRunRequest(task, HeadlessRunMode.parse(mode));
    }
}
