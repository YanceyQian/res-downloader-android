@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
echo =============================================
echo    ResDownloader Debug 构建脚本
echo =============================================
echo.

:: 查找Java路径 - 按优先级查找
set JAVA_HOME=
if exist "C:\Program Files\Android\Android Studio1\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr"
    goto found_java
)
if exist "C:\Program Files\Android\Android Studio1\jre" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jre"
    goto found_java
)
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    goto found_java
)
if exist "C:\Program Files\Android\Android Studio\jre" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
    goto found_java
)
for /d %%i in ("C:\Program Files\Java\jdk*") do (
    set "JAVA_HOME=%%i"
    goto found_java
)
for /d %%i in ("C:\Program Files (x86)\Java\jdk*") do (
    set "JAVA_HOME=%%i"
    goto found_java
)

:found_java
if not defined JAVA_HOME (
    echo [错误] 未找到 Java JDK！
    echo 请确保已安装 Android Studio。
    echo.
    echo 提示：Android Studio 自带 JDK，路径通常是：
    echo   C:\Program Files\Android\Android Studio1\jbr
    echo.
    pause
    exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%

echo [信息] 使用 Java: %JAVA_HOME%
echo.

echo [步骤 1/2] 清理旧构建...
if exist "app\build" rd /s /q "app\build"
if exist "app\outputs" rd /s /q "app\outputs"

echo.
echo [步骤 2/2] 构建 Debug APK...
echo =============================================

:: 切换到项目目录执行gradlew
cd /d "%~dp0"
gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% neq 0 (
    echo.
    echo [错误] 构建失败！
    echo.
    pause
    exit /b 1
)

echo.
echo =============================================
echo [成功] 构建完成！
echo =============================================
echo.
echo Debug APK位置:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo =============================================
echo.
pause
