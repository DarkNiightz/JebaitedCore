@REM -------------------------------------------------------------------
@REM Maven Wrapper (Windows)
@REM Downloads Apache Maven on first use and runs it.
@REM Cached under %USERPROFILE%\.m2\wrapper so it's a one-time download.
@REM -------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "MVN_VERSION=3.9.9"
set "MVN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MVN_VERSION%"
set "MVN_DIR=%MVN_HOME%\apache-maven-%MVN_VERSION%"
set "MVN_BIN=%MVN_DIR%\bin\mvn.cmd"

@REM ── 1. Find Java ──────────────────────────────────────────
set "JAVACMD=java"
if defined JAVA_HOME (
    set "JAVACMD=%JAVA_HOME%\bin\java"
)
"%JAVACMD%" -version >nul 2>&1
if errorlevel 1 (
    echo [mvnw] ERROR: Java not found. Set JAVA_HOME or add java to PATH.
    exit /b 1
)

@REM ── 2. Download Maven if missing ──────────────────────────
if not exist "%MVN_BIN%" (
    echo [mvnw] Maven %MVN_VERSION% not found. Downloading...
    if not exist "%MVN_HOME%" mkdir "%MVN_HOME%"
    set "MVN_ZIP=%MVN_HOME%\apache-maven-%MVN_VERSION%-bin.zip"
    powershell -NoProfile -Command ^
        "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip' -OutFile '!MVN_ZIP!'"
    if errorlevel 1 (
        echo [mvnw] ERROR: Failed to download Maven. Check internet connection.
        exit /b 1
    )
    echo [mvnw] Extracting...
    powershell -NoProfile -Command ^
        "Expand-Archive -Path '!MVN_ZIP!' -DestinationPath '%MVN_HOME%' -Force"
    if errorlevel 1 (
        echo [mvnw] ERROR: Failed to extract Maven archive.
        exit /b 1
    )
    del "!MVN_ZIP!" 2>nul
    echo [mvnw] Maven %MVN_VERSION% ready at %MVN_DIR%
)

@REM ── 3. Run Maven ──────────────────────────────────────────
"%MVN_BIN%" %*
exit /b %ERRORLEVEL%
