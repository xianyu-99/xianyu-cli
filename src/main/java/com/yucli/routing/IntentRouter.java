package com.yucli.routing;

import java.util.Optional;

public interface IntentRouter {
    Optional<IntentDecision> route(String prompt);
}
