@echo off
setlocal
cd /d "%~dp0"
set "ADB=adb.exe"
if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
"%ADB%" version >nul 2>&1
if errorlevel 1 (
 echo ADB not found. Extract official Android Platform-Tools and copy this script and APK there.
 pause
 exit /b 1
)
echo Connected devices:
"%ADB%" devices
echo Installing ErokhinTV Premium 3.3 without deleting settings...
"%ADB%" install -r "ErokhinTV_Premium_Interface.apk"
if errorlevel 1 (
 echo Installation failed. See INSTALL_RU.txt. Signature conflicts require a manual decision.
 echo This script has NOT removed the installed launcher.
 pause
 exit /b 1
)
"%ADB%" shell am start -n com.gathin.tvhome/.MainActivity
if errorlevel 1 (
 echo Installed, but could not launch. Open ErokhinTV from the TV app list.
 pause
 exit /b 1
)
echo Installation complete.
pause
