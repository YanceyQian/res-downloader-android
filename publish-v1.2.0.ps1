# GitHub Release 发布脚本 (PowerShell)

param(
    [Parameter(Mandatory=$false)]
    [string]$Token
)

$REPO = "YanceyQian/res-downloader-android"
$VERSION = "1.2.0"
$TAG = "v${VERSION}"
$APK_FILE = "app/release/ResDownloader-v${VERSION}-release.apk"

# 如果没有传入 Token，提示用户输入
if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "请输入 GitHub Personal Access Token (需要 repo 权限):" -ForegroundColor Yellow
    $Token = Read-Host -AsSecureString
    $Token = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Token))
}

# 检查 APK 文件
if (-not (Test-Path $APK_FILE)) {
    Write-Host "错误: 找不到 APK 文件: $APK_FILE" -ForegroundColor Red
    exit 1
}

# 创建 Release Body
$body = @"
## v${VERSION} 更新日志

### 新增功能
- ✨ 完成视频号下载功能完整迁移
- ✨ 新增 WechatVideoDownloader.kt 微信视频号专用下载器

### 功能优化
- 🔧 VPN 流量转发已修复
- 🔧 新增熵值计算、MP4文件头修复、密钥哈希函数
- 🔧 增强平台检测

### 问题修复
- 🐛 修复 5 项编译错误

### 文档更新
- 📝 新增 MIGRATION_REPORT.md 移植详细报告

---

### 安装说明
1. 下载下方 APK 文件
2. 在设备上安装（可能需要开启"未知来源"权限）
3. 如需抓取 HTTPS 流量，请参考证书安装指南

### 下载链接
- [GitHub Release 下载](https://github.com/${REPO}/releases/latest)
"@

Write-Host "正在创建 GitHub Release..." -ForegroundColor Cyan

# 创建 Draft Release
$headers = @{
    "Authorization" = "token $Token"
    "Accept" = "application/vnd.github.v3+json"
    "Content-Type" = "application/json"
}

$releaseData = @{
    "tag_name" = $TAG
    "name" = "Release v${VERSION}"
    "body" = $body
    "draft" = $true
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "https://api.github.com/repos/${REPO}/releases" `
    -Method POST `
    -Headers $headers `
    -Body $releaseData

$uploadUrl = $response.upload_url -replace '\{\?name,label\}', "?name=ResDownloader-v${VERSION}-release.apk"

Write-Host "正在上传 APK 文件..." -ForegroundColor Cyan

# 上传 APK
$headers["Content-Type"] = "application/vnd.android.package-archive"
$apkBytes = [System.IO.File]::ReadAllBytes($APK_FILE)

Invoke-RestMethod -Uri $uploadUrl `
    -Method POST `
    -Headers $headers `
    -Body $apkBytes

Write-Host ""
Write-Host "Release 创建成功！" -ForegroundColor Green
Write-Host "请访问以下链接确认并发布: https://github.com/${REPO}/releases" -ForegroundColor Yellow
