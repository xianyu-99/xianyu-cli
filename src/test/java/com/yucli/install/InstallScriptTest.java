package com.yucli.install;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallScriptTest {

    @Test
    void unixInstallerMarksWrapperExecutable() throws Exception {
        String script = Files.readString(Path.of("install.sh"));

        assertTrue(script.contains("chmod +x \"$INSTALL_DIR/$SCRIPT_NAME\""));
    }

    @Test
    void windowsInstallerReadsJavaVersionThroughCmdStdoutRedirect() throws Exception {
        String script = Files.readString(Path.of("install.ps1"));

        assertTrue(script.contains("cmd /c \"java -version 2>&1\""),
                "java -version writes to stderr, which PowerShell treats as an error when ErrorActionPreference=Stop");
    }
}
