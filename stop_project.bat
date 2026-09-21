@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop_project.ps1" %*
if %ERRORLEVEL% neq 0 pause
