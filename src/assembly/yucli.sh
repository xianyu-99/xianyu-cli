#!/bin/sh

# YuCLI Launcher Script for Unix/Linux/macOS
# https://github.com/yucli/paicli

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="${APP_DIR}/lib/YuCLI.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: YuCLI.jar not found at ${JAR_PATH}"
    echo "Please ensure the application is properly installed."
    exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

exec "$JAVA_CMD" -Dfile.encoding=UTF-8 -jar "$JAR_PATH" "$@"
