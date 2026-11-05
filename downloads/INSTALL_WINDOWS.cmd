@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "ADB=adb.exe"
if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
"%ADB%" version >nul 2>&1
if errorlevel 1 (
 echo ADB не найден. Распакуйте официальные Android Platform-Tools и скопируйте туда этот сценарий и APK.
 pause
 exit /b 1
)
echo Подключённые устройства:
"%ADB%" devices
echo Устанавливаем MetoxTV 4.0.2 без удаления настроек...
"%ADB%" install -r "MetoxTV.apk"
if errorlevel 1 (
 echo Установка не выполнена. Смотрите INSTALL_RU.txt.
 echo Прежняя версия лаунчера не удалена.
 pause
 exit /b 1
)
"%ADB%" shell am start -n com.gathin.tvhome/.MainActivity
if errorlevel 1 (
 echo Приложение установлено. Откройте MetoxTV из списка приложений телевизора.
 pause
 exit /b 1
)
echo Установка завершена.
pause
