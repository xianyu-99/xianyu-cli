$ErrorActionPreference = "Stop"

$JAR_NAME = "yucli-19.0.0.jar"
$INSTALL_DIR = "$env:USERPROFILE\.YuCLI\bin"
$SCRIPT_NAME = "yucli.cmd"

Write-Host "=== YuCLI Installer ===" -ForegroundColor Cyan

# 1. Check JDK 17+
try {
    $javaVersion = cmd /c "java -version 2>&1" |
        Select-String -Pattern '"(\d+)' |
        Select-Object -First 1 |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
    if ([int]$javaVersion -lt 17) {
        Write-Host "Error: JDK 17+ required, found version $javaVersion" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error: java not found. Please install JDK 17+ first." -ForegroundColor Red
    exit 1
}

# 2. Build if jar not found
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR_PATH = "$SCRIPT_DIR\target\$JAR_NAME"

if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Jar not found, building with Maven..."
    try {
        $mvnCheck = Get-Command mvn -ErrorAction Stop
    } catch {
        Write-Host "Error: mvn not found. Please install Maven or build manually: mvn clean package -DskipTests" -ForegroundColor Red
        exit 1
    }
    Push-Location $SCRIPT_DIR
    mvn clean package -DskipTests -q
    Pop-Location
    Write-Host "Build complete."
}

# 3. Install
New-Item -ItemType Directory -Force -Path $INSTALL_DIR | Out-Null
Copy-Item $JAR_PATH "$INSTALL_DIR\$JAR_NAME" -Force

# 4. Create wrapper cmd
$cmdContent = "@echo off`r`njava -jar `"$INSTALL_DIR\$JAR_NAME`" %*"
Set-Content -Path "$INSTALL_DIR\$SCRIPT_NAME" -Value $cmdContent

# 5. Add to PATH if needed
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*$INSTALL_DIR*") {
    [Environment]::SetEnvironmentVariable("Path", "$INSTALL_DIR;$currentPath", "User")
    $env:Path = "$INSTALL_DIR;$env:Path"
    Write-Host "Added $INSTALL_DIR to user PATH." -ForegroundColor Green
    Write-Host "You may need to restart your terminal for PATH changes to take effect."
}

Write-Host ""
Write-Host "=== Installation complete ===" -ForegroundColor Green
Write-Host "Run: yucli"
Write-Host "Install location: $INSTALL_DIR"
