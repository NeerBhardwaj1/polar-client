@echo off
setlocal
title Polar Client - release an update
cd /d "%~dp0"
where node >nul 2>&1 || (echo Node.js is not installed: https://nodejs.org & pause & exit /b 1)
node tools\github.js release %*
set "CODE=%ERRORLEVEL%"
echo.
pause
exit /b %CODE%
