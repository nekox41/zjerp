@echo off
echo.
echo [信息] 打包 Web 工程，生成 dist 文件。
echo.

cd /d "%~dp0.."
call npm run build:prod
if errorlevel 1 (
  echo [错误] 构建失败。
  pause
  exit /b 1
)
pause
