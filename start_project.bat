@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start_project.ps1" %*
if %ERRORLEVEL% neq 0 (
    echo.
    echo ==================================================
    echo [ERROR] Startup failed. Check runtime/logs/
    echo ==================================================
    pause
)
