@echo off
setlocal
title Polar Client - build the Windows installer
cd /d "%~dp0"

echo.
echo [1/4] Checking tools...
where java >nul 2>&1 || (echo Java 25 is not installed: https://www.oracle.com/java/technologies/downloads/ & goto fail)
where npm >nul 2>&1 || (echo Node.js is not installed: https://nodejs.org & goto fail)

echo.
echo [2/4] Building the Polar Client mod for every Minecraft version (the first time downloads each version, so it takes a while)...
for %%V in (26.3 1.21.11 1.21.10 1.16.5 1.8.9) do (
  echo.
  echo --- Minecraft %%V
  pushd "mod\%%V"
  call gradlew.bat build
  if errorlevel 1 (popd & goto fail)
  popd
)

echo.
echo [3/4] Installing the launcher's build tools (the first time takes a few minutes)...
pushd launcher
rem GitHub's download servers are blocked on some networks, so Electron and its packaging tools come from the npmmirror CDN.
set "ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/"
set "ELECTRON_CUSTOM_DIR={{ version }}"
set "ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/"
set "CSC_IDENTITY_AUTO_DISCOVERY=false"
call npm install --no-audit --no-fund
if errorlevel 1 (popd & goto fail)
rem Electron 44 no longer downloads itself during npm install, so fetch it here.
echo Downloading Electron...
call node node_modules\electron\install.js
if errorlevel 1 (popd & goto fail)
if not exist node_modules\electron\dist\electron.exe (echo Electron did not download. & popd & goto fail)
call npm run bundle-mod
if errorlevel 1 (popd & goto fail)
echo Running launcher tests...
call npm test
if errorlevel 1 (popd & goto fail)

echo.
echo [4/4] Packing the installer...
rem If a file in the last build is still open (a copy of the launcher running from it, an antivirus scan, ...),
rem electron-builder can't replace it, so this build is packed into a fresh folder instead of failing.
set "OUT=dist"
if exist "dist\win-unpacked" rmdir /s /q "dist\win-unpacked" >nul 2>&1
if exist "dist\win-unpacked" set "OUT=dist-%RANDOM%"
if not "%OUT%"=="dist" echo Note: the last build's folder is still in use, so this one goes to launcher\%OUT%.
set "EXTRA="
mklink "%TEMP%\polar-link-test" "%~f0" >nul 2>&1
if errorlevel 1 (
  echo Note: Windows Developer Mode is off, so the app keeps the default Electron icon.
  echo       For the polar bear icon, turn on Settings ^> System ^> For developers ^> Developer Mode and run this again.
  set "EXTRA=-c.win.signAndEditExecutable=false"
) else (
  del "%TEMP%\polar-link-test" >nul 2>&1
)
call npx electron-builder --win nsis --publish never -c.electronDist=node_modules/electron/dist -c.directories.output=%OUT% %EXTRA%
if errorlevel 1 (popd & goto fail)
for %%F in ("%OUT%\*Setup*.exe") do (
  copy /y "%%~fF" "%USERPROFILE%\Desktop\" >nul
  echo Copied "%%~nxF" to your Desktop.
)
popd
echo.
echo Done!
pause
exit /b 0

:fail
echo.
echo The build stopped. Copy the messages above and send them to Claude.
pause
exit /b 1
