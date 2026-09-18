@echo off
chcp 65001 >nul
cd /d "%~dp0.."
title Face Register Camera - InsightFace
echo ============================================================
echo   具身机器人智能课堂分析系统 - 摄像头人脸录入
echo ============================================================
echo [1/2] 正在加载 InsightFace 模型与硬件加速驱动...
echo [2/2] 正在启动摄像头画面窗口，请稍候...
echo.
".venv1\Scripts\python.exe" "vision\face_register.py" %*
echo.
echo [提示] 录入窗口已关闭。

