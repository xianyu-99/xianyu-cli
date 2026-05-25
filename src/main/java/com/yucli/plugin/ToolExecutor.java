package com.yucli.plugin;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ToolExecutor {
    String execute(JsonNode arguments) throws Exception;
}
