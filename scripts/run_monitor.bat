@echo off
chcp 65001 >nul
cd /d "%~dp0.."
title Classroom AI Robot Vision Monitor
echo ============================================================
echo   Classroom AI Realtime Vision Monitor
echo ============================================================
echo [1/2] Loading AI inference models and database...
echo [2/2] Connecting camera and streaming to backend...
echo.
".venv1\Scripts\python.exe" "vision\classroom_monitor.py" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Monitor stopped with error code: %ERRORLEVEL%
    pause
)

