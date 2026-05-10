@echo off
chcp 65001 >nul
echo =============================================
echo    ResDownloader 一键发布到 GitHub
echo =============================================
echo.

echo [1/3] 检查 APK 文件...
if not exist "res-downloader_v1.0.0_android.apk" (
    echo [错误] 未找到 APK 文件！
    echo 请确保 res-downloader_v1.0.0_android.apk 在当前目录。
    pause
    exit /b 1
)
echo [OK] APK 文件已就绪
echo.

echo [2/3] 复制发布说明到剪贴板...
set "RELEASE_TEXT=## 1.0.0 (2026-05-08)\n\n### ✨ 初始发布\n- 基于 putyy/res-downloader 的 Android 移植版本\n- 支持视频号、抖音、快手、小红书等平台资源下载\n- 内置代理抓包功能\n- 支持自动更新检测\n\n### 📱 功能特点\n- 🔒 VPN 代理抓包\n- 📦 M3U8 流媒体下载\n- 🔄 GitHub Releases 自动更新\n- 🌐 中英文双语支持\n\n### 📦 下载\n- Android APK: res-downloader_v1.0.0_android.apk"
echo %RELEASE_TEXT% | clip
echo [OK] 发布说明已复制到剪贴板
echo.

echo [3/3] 打开 GitHub 发布页面...
start https://github.com/YanceyQian/res-downloader-android/releases/new
echo.

echo =============================================
echo [提示] 请按以下步骤操作：
echo.
echo 1. 在 "Choose a tag" 输入框输入：v1.0.0
echo    然后点击 "Create new tag: v1.0.0 on publish"
echo.
echo 2. 在 "Release title" 输入框输入：1.0.0
echo.
echo 3. 在描述区域按 Ctrl+V 粘贴内容
echo.
echo 4. 拖拽文件到 "Attach binaries" 区域：
echo    res-downloader_v1.0.0_android.apk
echo.
echo 5. 点击 "Publish release" 完成发布！
echo =============================================
echo.
echo APK 文件位置：%CD%\res-downloader_v1.0.0_android.apk
echo.
explorer /select,"%CD%\res-downloader_v1.0.0_android.apk"
echo.
pause
