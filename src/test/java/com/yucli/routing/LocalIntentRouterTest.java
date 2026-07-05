package com.yucli.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIntentRouterTest {

    @Test
    void parseDecisionAcceptsCompactJson() throws Exception {
        IntentDecision decision = LocalIntentRouter.parseDecision("""
                {"needs_write":true,"needs_command":true,"needs_web":false,
                 "needs_browser":false,"risk":"medium","confidence":0.92,
                 "tools":["write_file"]}
                """);

        assertTrue(decision.needsWrite());
        assertTrue(decision.needsCommand());
        assertEquals("medium", decision.risk());
        assertEquals(0.92, decision.confidence(), 0.0001);
        assertTrue(decision.suggestedTools().contains("write_file"));
    }

    @Test
    void parseDecisionExtractsJsonFromFencedOutput() throws Exception {
        IntentDecision decision = LocalIntentRouter.parseDecision("""
                ```json
                {"needs_web":"true","needs_browser":true,"risk":"low","confidence":"0.8"}
                ```
                """);

        assertTrue(decision.needsWeb());
        assertTrue(decision.needsBrowser());
        assertEquals("low", decision.risk());
        assertEquals(0.8, decision.confidence(), 0.0001);
    }
}
