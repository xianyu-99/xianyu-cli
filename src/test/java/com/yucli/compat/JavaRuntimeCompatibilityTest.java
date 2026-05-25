package com.yucli.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JavaRuntimeCompatibilityTest {

    @Test
    void productionCodeDoesNotUseJavaApisNewerThanDocumentedRuntime() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/yucli/mcp/McpServerManager.java"));

        assertFalse(source.contains("Thread.ofVirtual()"),
                "README and installers document Java 17+, so production code must not call Java 21 Thread.ofVirtual()");
    }
}
