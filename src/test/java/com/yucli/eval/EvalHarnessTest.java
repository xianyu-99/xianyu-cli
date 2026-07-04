package com.yucli.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalHarnessTest {

    @Test
    void evalHarnessRequiresExplicitSystemProperty() {
        assertTrue(EvalHarness.class.isAnnotationPresent(EnabledIfSystemProperty.class),
                "EvalHarness calls real LLMs and must be gated unless explicitly enabled");

        EnabledIfSystemProperty gate = EvalHarness.class.getAnnotation(EnabledIfSystemProperty.class);
        assertEquals("YuCLI.eval.enabled", gate.named());
        assertEquals("true", gate.matches());
    }

    @Test
    void evalCasesJsonUsesDocumentedFormat() throws Exception {
        File casesFile = new File("src/test/resources/eval/cases.json");
        List<EvalTestCase> cases = new ObjectMapper()
                .readValue(casesFile, new TypeReference<List<EvalTestCase>>() {});

        assertFalse(cases.isEmpty());
        for (EvalTestCase testCase : cases) {
            assertNotNull(testCase.getId());
            assertFalse(testCase.getId().isBlank());
            assertNotNull(testCase.getInstruction());
            assertFalse(testCase.getInstruction().isBlank());
            assertNotNull(testCase.getVerifyScript());
        }
    }
}
