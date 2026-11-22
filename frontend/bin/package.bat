@echo off
echo.
echo [信息] 安装 Web 工程依赖。
echo.

cd /d "%~dp0.."
call npm ci
if errorlevel 1 (
  echo [错误] 依赖安装失败。
  pause
  exit /b 1
)
pause
