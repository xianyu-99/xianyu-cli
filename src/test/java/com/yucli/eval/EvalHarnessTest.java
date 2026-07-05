package com.yucli.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalHarnessTest {
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Set<String> SUPPORTED_MODES = Set.of("react", "plan", "team");
    private static final List<Pattern> DANGEROUS_SCRIPT_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(^|[\\s;&|])sudo([\\s;&|]|$)"),
            Pattern.compile("(?i)\\brm\\s+-[^\\r\\n;|&]*r[^\\r\\n;|&]*f[^\\r\\n;|&]*\\s+/([\\s;&|]|$)"),
            Pattern.compile("(?i)\\bmkfs(?:\\.|\\s|$)"),
            Pattern.compile("(?i)\\bdd\\b[^\\r\\n;|&]*\\bof=/dev/"),
            Pattern.compile("(?i)(?:curl|wget)\\b[^\\r\\n]*(?:\\||>)\\s*(?:sh|bash|powershell|pwsh)\\b"),
            Pattern.compile("(?i)\\b(?:shutdown|reboot)\\b"),
            Pattern.compile("(?i)\\bchmod\\s+777\\s+/"),
            Pattern.compile(":\\s*\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*}\\s*;")
    );

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
        Set<String> ids = new HashSet<>();
        for (EvalTestCase testCase : cases) {
            assertNotNull(testCase.getId());
            assertFalse(testCase.getId().isBlank());
            assertTrue(SAFE_ID.matcher(testCase.getId()).matches(),
                    "Eval case id should be lowercase slug-like text: " + testCase.getId());
            assertTrue(ids.add(testCase.getId()), "Duplicate eval case id: " + testCase.getId());

            assertNotNull(testCase.getInstruction());
            assertFalse(testCase.getInstruction().isBlank());

            assertTrue(SUPPORTED_MODES.contains(normalizeMode(testCase.getMode())),
                    "Eval case mode must be one of " + SUPPORTED_MODES + ": " + testCase.getId());

            assertTrue(hasVerifier(testCase),
                    "Eval case must define verifyScript, or both verifyScriptWindows and verifyScriptUnix: "
                            + testCase.getId());

            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "setupScript", testCase.getSetupScript());
            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "setupScriptWindows", testCase.getSetupScriptWindows());
            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "setupScriptUnix", testCase.getSetupScriptUnix());
            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "verifyScript", testCase.getVerifyScript());
            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "verifyScriptWindows", testCase.getVerifyScriptWindows());
            assertScriptDoesNotContainDangerousFragment(testCase.getId(), "verifyScriptUnix", testCase.getVerifyScriptUnix());
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "react";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasVerifier(EvalTestCase testCase) {
        return hasText(testCase.getVerifyScript())
                || (hasText(testCase.getVerifyScriptWindows()) && hasText(testCase.getVerifyScriptUnix()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void assertScriptDoesNotContainDangerousFragment(String caseId, String fieldName, String script) {
        if (script == null || script.isBlank()) {
            return;
        }

        for (Pattern pattern : DANGEROUS_SCRIPT_PATTERNS) {
            assertFalse(pattern.matcher(script).find(),
                    "Eval case " + caseId + " " + fieldName
                            + " contains a dangerous command fragment matching: " + pattern.pattern());
        }
    }
}
