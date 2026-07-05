package com.yucli.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HookConfig {
    private Map<String, List<HookDefinition>> hooks = new LinkedHashMap<>();

    public Map<String, List<HookDefinition>> getHooks() {
        return hooks;
    }

    public void setHooks(Map<String, List<HookDefinition>> hooks) {
        this.hooks = hooks == null ? new LinkedHashMap<>() : hooks;
    }
}
