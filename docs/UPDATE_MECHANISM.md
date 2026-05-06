# 🔄 更新机制说明

本文档说明应用的自动更新功能。

## 概述

应用内置自动更新功能，通过 GitHub Releases 实现：

1. **版本检测**：在设置中点击"检查更新"，或应用启动时自动检测
2. **信息获取**：通过 GitHub API 获取最新版本信息
3. **更新提示**：显示更新内容
4. **APK 下载**：自动下载最新 APK
5. **安装更新**：调用系统安装程序完成更新

## 使用方法

### 手动检查更新

1. 打开应用，进入"设置"页面
2. 点击"检查更新"
3. 如果有新版本，会显示更新内容和下载选项
4. 下载完成后点击安装

### 自动更新说明

- 应用会定期检查更新（目前需要手动触发）
- 更新源：https://github.com/YanceyQian/res-downloader-android/releases
- 建议从 GitHub Releases 下载最新版本安装

## APK 文件格式

应用会自动识别以下格式的 APK：

- `res-downloader_v{VERSION}_android.apk`
- 例如：`res-downloader_v1.0.0_android.apk`

## 开发者说明

如果需要修改更新源，请编辑：

`app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt`
