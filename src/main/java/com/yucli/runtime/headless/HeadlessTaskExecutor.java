package com.yucli.runtime.headless;

@FunctionalInterface
public interface HeadlessTaskExecutor {

    String run(String task, HeadlessRunMode mode) throws Exception;
}
