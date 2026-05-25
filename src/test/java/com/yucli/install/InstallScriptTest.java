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
}
