@echo off
setlocal enabledelayedexpansion

REM YuCLI Launcher Script for Windows
REM https://github.com/yucli/paicli

set "APP_DIR=%~dp0.."
set "JAR_PATH=%APP_DIR%\lib\YuCLI.jar"

if not exist "%JAR_PATH%" (
    echo ERROR: YuCLI.jar not found at %JAR_PATH%
    echo Please ensure the application is properly installed.
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java.exe"
)

"%JAVA_CMD%" -Dfile.encoding=UTF-8 -jar "%JAR_PATH%" %*
