@echo off
chcp 65001 >nul
echo =============================================
echo    ResDownloader 签名密钥生成脚本
echo =============================================
echo.

:: 查找Java路径
set JAVA_HOME=
for /d %%i in ("C:\Program Files\Java\jdk*") do set "JAVA_HOME=%%i"
for /d %%i in ("C:\Program Files (x86)\Java\jdk*") do set "JAVA_HOME=%%i"
if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%\jdk" set "JAVA_HOME=%ANDROID_HOME%\jdk"
)

if not defined JAVA_HOME (
    echo [错误] 未找到 Java JDK！
    echo 请确保已安装 JDK 或配置 ANDROID_HOME 环境变量。
    echo.
    echo 提示：Android Studio 自带 JDK，路径通常是：
    echo   C:\Program Files\Android\Android Studio\jbr
    echo.
    pause
    exit /b 1
)

set KEYTOOL=%JAVA_HOME%\bin\keytool.exe

if not exist "%KEYTOOL%" (
    echo [错误] 未找到 keytool.exe！
    echo 路径: %KEYTOOL%
    echo.
    pause
    exit /b 1
)

echo [信息] 找到 Java: %JAVA_HOME%
echo [信息] 使用 keytool: %KEYTOOL%
echo.

:: 生成签名密钥
echo [步骤 1/2] 正在生成签名密钥...
"%KEYTOOL%" -genkey -v ^
    -keystore "res-downloader.keystore" ^
    -keyalg RSA ^
    -keysize 2048 ^
    -validity 10000 ^
    -alias resdownloader ^
    -storepass ResDown2026! ^
    -keypass ResDown2026! ^
    -dname "CN=YanceyQian, OU=ResDownloader, O=ResDownloader, L=Beijing, ST=Beijing, C=CN"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [错误] 密钥生成失败！
    pause
    exit /b 1
)

echo.
echo [步骤 2/2] 验证密钥文件...
if exist "res-downloader.keystore" (
    echo [成功] 密钥文件已生成: res-downloader.keystore
    "%KEYTOOL%" -list -v -keystore "res-downloader.keystore" -storepass ResDown2026!
) else (
    echo [错误] 密钥文件未找到！
    pause
    exit /b 1
)

echo.
echo =============================================
echo    签名密钥生成完成！
echo =============================================
echo.
echo [重要] 请备份密钥文件:
echo   res-downloader.keystore
echo.
echo   密钥密码: ResDown2026!
echo   别名: resdownloader
echo.
echo   丢失此文件将无法更新已发布的应用！
echo =============================================
echo.
pause
