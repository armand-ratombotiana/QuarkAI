@echo off
REM QuarkAI Build Verification Script (Windows)
REM Purpose: Verify all modules build, tests pass, and generate coverage reports

setlocal enabledelayedexpansion

echo ==================================================
echo QuarkAI Build Verification
echo ==================================================
echo.

set START_TIME=%time%

REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REM Phase 1: Clean Build
REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

echo [Phase 1] Clean Build
echo Running: mvn clean package -DskipTests
echo.

call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Clean build failed!
    exit /b 1
)

echo [SUCCESS] Clean build completed
echo.

REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REM Phase 2: Unit Tests
REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

echo [Phase 2] Running Unit Tests
echo Running: mvn test
echo.

call mvn test
if errorlevel 1 (
    echo [ERROR] Unit tests failed!
    exit /b 1
)

echo [SUCCESS] All unit tests passed
echo.

REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REM Phase 3: Code Coverage Report
REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

echo [Phase 3] Generating Code Coverage Reports
echo Running: mvn jacoco:report
echo.

call mvn jacoco:report
if errorlevel 1 (
    echo [WARNING] Coverage report generation failed (non-critical)
) else (
    echo [SUCCESS] Coverage reports generated
    echo.
    echo Coverage reports available at:
    echo   - quarkai-core\target\site\jacoco\index.html
    echo   - quarkai-openai\target\site\jacoco\index.html
    echo   - quarkai-anthropic\target\site\jacoco\index.html
    echo   - quarkai-vertex\target\site\jacoco\index.html
    echo   - quarkai-ollama\target\site\jacoco\index.html
    echo   - quarkai-rag\target\site\jacoco\index.html
    echo   - quarkai-vertx\target\site\jacoco\index.html
)

echo.

REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REM Phase 4: Module Verification
REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

echo [Phase 4] Verifying Module Artifacts
echo.

set ALL_OK=1

if exist "quarkai-core\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-core
) else (
    echo [ERROR] Module artifact missing: quarkai-core
    set ALL_OK=0
)

if exist "quarkai-openai\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-openai
) else (
    echo [ERROR] Module artifact missing: quarkai-openai
    set ALL_OK=0
)

if exist "quarkai-anthropic\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-anthropic
) else (
    echo [ERROR] Module artifact missing: quarkai-anthropic
    set ALL_OK=0
)

if exist "quarkai-vertex\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-vertex
) else (
    echo [ERROR] Module artifact missing: quarkai-vertex
    set ALL_OK=0
)

if exist "quarkai-ollama\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-ollama
) else (
    echo [ERROR] Module artifact missing: quarkai-ollama
    set ALL_OK=0
)

if exist "quarkai-rag\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-rag
) else (
    echo [ERROR] Module artifact missing: quarkai-rag
    set ALL_OK=0
)

if exist "quarkai-vertx\target\*.jar" (
    echo [SUCCESS] Module artifact exists: quarkai-vertx
) else (
    echo [ERROR] Module artifact missing: quarkai-vertx
    set ALL_OK=0
)

echo.

if %ALL_OK%==0 (
    echo [ERROR] Some module artifacts are missing!
    exit /b 1
)

echo [SUCCESS] All module artifacts verified
echo.

REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REM Final Summary
REM ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

set END_TIME=%time%

echo ==================================================
echo Build Verification Complete!
echo ==================================================
echo.
echo Start time: %START_TIME%
echo End time:   %END_TIME%
echo.
echo [SUCCESS] All checks passed - QuarkAI is production ready!
echo.
echo Next steps:
echo   1. Review coverage reports in target\site\jacoco\index.html
echo   2. Test native image compilation (optional)
echo   3. Deploy to staging environment
echo.

exit /b 0
