package com.yucli.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CommandProcessSpec(
        List<String> commandLine,
        Path workingDirectory,
        List<String> cleanupCommand,
        String label
) {
    public CommandProcessSpec {
        Objects.requireNonNull(commandLine, "commandLine");
        if (commandLine.isEmpty()) {
            throw new IllegalArgumentException("commandLine must not be empty");
        }
        commandLine = List.copyOf(commandLine);
        cleanupCommand = cleanupCommand == null ? List.of() : List.copyOf(cleanupCommand);
        label = label == null || label.isBlank() ? "command" : label.trim();
    }
}
