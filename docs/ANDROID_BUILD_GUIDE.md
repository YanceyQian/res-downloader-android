# Android 编译指南

本文档说明如何编译 res-downloader Android APK。

## 环境要求

### 1. Android Studio

- **最低版本**: Android Studio Hedgehog (2023.1.1) 或更高
- **下载地址**: https://developer.android.com/studio

安装后，确保以下组件已安装：
- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0

### 2. JDK

- Android Studio 自带 JDK 17，无需单独安装

## 快速开始

### 使用 Android Studio（推荐）

1. 打开 Android Studio
2. 选择 **Open** → 选择项目文件夹：`E:\res-download\res-downloader-android`
3. 等待 Gradle 同步完成（首次可能需要几分钟下载依赖）
4. 点击菜单：**Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
5. 编译完成后，点击通知中的 **locate** 查看 APK 文件

### 使用命令行

```bash
cd E:\res-download\res-downloader-android

# Debug 版本
./gradlew assembleDebug

# Release 版本（需要配置签名）
./gradlew assembleRelease
```

APK 输出位置：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 项目结构

```
res-downloader-android/
├── app/                          # Android 应用模块
│   ├── src/main/java/com/resdownloader/
│   │   ├── data/               # 数据层
│   │   ├── service/            # Android 服务（VPN、下载）
│   │   └── ui/               # UI 层 (Jetpack Compose)
│   └── build.gradle.kts       # 构建配置
├── core/                        # Go 核心代码（暂未集成）
├── docs/                        # 文档
└── build.gradle.kts             # 项目级构建配置
```

## 配置签名（可选）

### 配置 Release 签名

1. 生成密钥库：
   - Build → Generate Signed Bundle / APK
   - 选择 **APK**
   - 点击 **Create new...** 创建密钥

2. 在 `app/build.gradle.kts` 中配置签名（可选）

## 常见问题

### Gradle 同步慢？

- 首次打开需要下载依赖，请耐心等待
- 或配置 Gradle 镜像源（国内推荐）

### 编译失败？

- 点击 **Build** → **Clean Project**
- 然后点击 **Build** → **Rebuild Project**

### SDK 或模拟器？

- 确保已安装 Android SDK 34
- 在 SDK Manager 检查更新

---

**注意**：目前 Android 版本使用纯 Kotlin 实现，Go 核心代码在 `core/` 目录但尚未集成，后续版本会集成 Go 核心功能。
