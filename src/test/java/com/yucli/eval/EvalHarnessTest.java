package com.yucli.eval;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalHarnessTest {

    @Test
    void evalHarnessIsDisabledByDefault() {
        assertTrue(EvalHarness.class.isAnnotationPresent(Disabled.class),
                "EvalHarness calls real LLMs and must be disabled unless explicitly enabled");
    }
}
