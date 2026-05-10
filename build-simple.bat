@echo off
echo Building ResDownloader...
echo.

set JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

echo Using Java: %JAVA_HOME%
echo.

cd /d "%~dp0"

echo Cleaning build...
if exist app\build rmdir /s /q app\build

echo.
echo Building Debug APK...
call gradlew.bat assembleDebug

echo.
echo ========================================
if %ERRORLEVEL% equ 0 (
    echo Build SUCCESS!
    echo APK: app\build\outputs\apk\debug\app-debug.apk
) else (
    echo Build FAILED!
)
echo ========================================
echo.
pause
