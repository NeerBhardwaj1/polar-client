@echo off
rem Downloads the Minecraft files (1.21.11, 1.21.10, 1.16.5, 1.8.9) used to check the Polar Client ports.
rem They are saved in tools\mcref. Safe to run again: files already there are skipped.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\fetch-refs.ps1"
if errorlevel 1 (
  echo.
  echo Something went wrong. Check your internet connection and run this again.
)
echo.
pause
