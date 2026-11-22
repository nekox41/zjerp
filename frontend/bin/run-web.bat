@echo off
echo.
echo [信息] 使用 Vite 命令运行 Web 工程。
echo.

cd /d "%~dp0.."
call npm run dev
if errorlevel 1 (
  echo [错误] 开发服务器启动失败。
  pause
  exit /b 1
)
pause
