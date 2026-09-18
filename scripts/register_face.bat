@echo off
chcp 65001 >nul
cd /d "%~dp0.."
echo ============================================================
echo   具身机器人智能课堂分析系统 - 人脸特征录入工具
echo ============================================================

set /p STU_ID="请输入学生学号 (默认: STU2026001): "
if "%STU_ID%"=="" set STU_ID=STU2026001

set /p STU_NAME="请输入学生姓名 (默认: 张三): "
if "%STU_NAME%"=="" set STU_NAME=张三

set /p STU_CLASS="请输入所属班级 (默认: 高一(1)班): "
if "%STU_CLASS%"=="" set STU_CLASS=高一(1)班

echo.
echo [提示] 正在启动摄像头与 InsightFace 模型，请正对摄像头...
echo [提示] 出现画面框选人脸后，按下键盘上的 'S' 键保存录入！
echo.

".venv1\Scripts\python.exe" "vision\face_register.py" --id %STU_ID% --name "%STU_NAME%" --class-name "%STU_CLASS%"

echo.
pause

