@echo off
chcp 65001 >nul
echo ========================================
echo   Res Downloader Android Publish Tool
echo ========================================
echo.

REM Check if Git is installed
git --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git not installed!
echo.
echo Please download and install Git from:
echo https://git-scm.com/download/win
echo.
pause
exit /b 1
)

echo [OK] Git installed
echo.

REM Check if we are in the right directory
if not exist "app" (
    echo [ERROR] Please run this script from res-downloader-android directory!
echo.
pause
exit /b 1
)

echo [OK] Project directory correct
echo.
echo Follow these steps:
echo.
echo 1. Open this URL in your browser:
echo    https://github.com/new
echo    Create a repository named res-downloader-android
echo.
echo 2. After creating, press any key to continue...
pause >nul

cls
echo ========================================
echo   Initialize Git
echo ========================================
echo.

git init
git add .
git commit -m "Initial commit: res-downloader Android version"
git branch -M main

echo.
echo ========================================
echo   Link to remote repository
echo ========================================
echo.

set /p USERNAME=Enter your GitHub username:

git remote add origin https://github.com/%USERNAME%/res-downloader-android.git

echo.
echo ========================================
echo   Push to GitHub
echo ========================================
echo.
echo Pushing code to GitHub...
git push -u origin main

echo.
echo ========================================
echo   Done!
echo ========================================
echo.
echo Next steps:
echo 1. Open UpdateRepository.kt to update the repo URL
echo 2. Build APK
echo 3. Release on GitHub Releases
echo.
echo See PUBLISH_GUIDE.md for detailed steps
echo.
pause
