@echo off
chcp 65001 >nul
echo =============================================
echo    ResDownloader Release 构建脚本
echo =============================================
echo.

:: 查找Java路径
set JAVA_HOME=
if exist "C:\Program Files\Android\Android Studio\jbr" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if exist "C:\Program Files\Android\Android Studio\jre" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"

if not defined JAVA_HOME (
    echo [错误] 未找到 Java JDK！
    echo 请确保已安装 Android Studio。
    pause
    exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%

echo [信息] 使用 Java: %JAVA_HOME%
echo.

:: 检查密钥文件
if not exist "res-downloader.keystore" (
    echo [错误] 未找到密钥文件 res-downloader.keystore
    echo 请先运行 generate-key.bat 生成密钥！
    pause
    exit /b 1
)

echo [步骤 1/3] 检查密钥文件...
"%JAVA_HOME%\bin\keytool.exe" -list -keystore "res-downloader.keystore" -storepass ResDown2026! >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 密钥文件无效或密码错误！
    pause
    exit /b 1
)
echo [OK] 密钥文件检查通过

echo.
echo [步骤 2/3] 清理旧构建...
if exist "app\build" rd /s /q "app\build"
if exist "app\outputs" rd /s /q "app\outputs"

echo.
echo [步骤 3/3] 构建 Release APK...
echo =============================================

:: 切换到项目目录执行gradlew
cd /d "%~dp0"
gradlew.bat assembleRelease --no-daemon

if %ERRORLEVEL% neq 0 (
    echo.
    echo [错误] 构建失败！
    pause
    exit /b 1
)

echo.
echo =============================================
echo [成功] 构建完成！
echo =============================================
echo.
echo 签名APK位置:
echo   app\build\outputs\apk\release\app-release.apk
echo.
echo 签名信息:
echo   别名: resdownloader
echo   SHA1: 03:6C:D0:23:FE:EF:05:66:A9:9F:15:44:56:E7:EA:50:78:FB:74:1F
echo   SHA256: 52:7C:76:31:1C:9A:9D:21:15:BC:CB:0C:5D:E7:09:9A:3C:B3:94:1C
echo.
echo =============================================
echo.
echo 提示: 
echo   应用市场发布时需要提供 SHA1 或 SHA256 指纹。
echo   以上签名信息可以直接使用。
echo =============================================
echo.
pause
