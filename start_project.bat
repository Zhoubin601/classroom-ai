@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ==================================================
echo   正在启动 Classroom AI 项目，请稍候...
echo ==================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start_project.ps1" -NonInteractive %*
if %ERRORLEVEL% neq 0 (
    echo.
    echo ==================================================
    echo [错误] 启动过程中出现异常，请检查上方日志。
    echo ==================================================
    pause
) else (
    echo.
    echo ==================================================
    echo 服务已在后台成功启动！
    echo 前端地址: http://127.0.0.1:5173
    echo 后端地址: http://127.0.0.1:8080
    echo 若需停止服务，请双击运行 stop_project.bat
    echo ==================================================
    pause
)
