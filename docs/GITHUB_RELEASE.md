# 📦 GitHub 仓库创建和发布指南

本文档指导您如何创建自己的 GitHub 仓库并发布 Android 版本。

## 第一步：创建 GitHub 仓库

1. 访问 [https://github.com/new](https://github.com/new)
2. 填写信息：
   - **Repository name**: `res-downloader-android`（或您喜欢的名称）
   - **Description**: `基于 putyy/res-downloader 的 Android 版本，支持多平台资源下载`
   - **Public/Private**: 选择 Public（推荐，便于用户下载）
   - **Initialize repository**: 不需要勾选，我们有完整代码
3. 点击 **Create repository**

## 第二步：上传代码

### 方法一：使用命令行

```bash
# 1. 初始化 Git 仓库
cd E:\res-download\res-downloader-android
git init

# 2. 创建 .gitignore（如果没有的话）
cat > .gitignore << 'EOF'
# Android
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
*.apk
*.ap_
*.aab
*.keystore
*.jks
*.p12
*.pem

# Android Studio
*.iml
.idea/
*.swp
*.swo
*~

# Build
build/

# Logs
*.log

# Go
*.exe
*.exe~
*.dll
*.so
*.dylib
*.test
*.out
go.work
EOF

# 3. 提交代码
git add .
git commit -m "Initial commit: res-downloader Android version"

# 4. 关联远程仓库（替换 YOUR-USERNAME 为您的用户名）
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/res-downloader-android.git
git push -u origin main
```

### 方法二：使用 GitHub Desktop（图形界面）

1. 下载 [GitHub Desktop](https://desktop.github.com/)
2. 将文件夹拖入 GitHub Desktop 窗口
3. 填写提交信息并发布仓库

## 第三步：创建发布（Release）

### 编译 APK

```bash
cd E:\res-download\res-downloader-android

# Debug 版本（用于测试）
./gradlew assembleDebug

# Release 版本（用于发布，需要配置签名）
./gradlew assembleRelease
```

输出位置：
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### 在 GitHub 发布版本

1. 进入您的仓库页面
2. 点击右侧 **Releases** → **Draft a new release**
3. 填写信息：

| 字段 | 内容 | 示例 |
|------|------|------|
| **Choose a tag** | 点击 Create new tag on publish | `v1.0.0` |
| **Release title** | 版本标题 | `1.0.0` |
| **Describe this release** | 更新说明（支持 Markdown） | 见下文示例 |
| **Attach binaries** | 拖放编译好的 APK | `app-release.apk` |

### 建议的更新说明格式

```markdown
## 1.0.0 (2026-05-06)

### 🚀 新功能
- 支持多平台资源下载
- VPN 代理抓包
- M3U8 流媒体下载
- 自动更新机制

### 📱 支持的平台
- 微信视频号
- 抖音
- 快手
- 小红书
- 酷狗音乐
- QQ 音乐

### 🔧 改进
- 优化 UI 体验
- 增加多语言支持

### 📦 下载
- Android APK: [res-downloader_v1.0.0_android.apk](链接)

---
基于原项目 putyy/res-downloader 开发
```

4. 点击 **Publish release**（发布）

### 重要：APK 文件命名

推荐命名格式：`res-downloader_v{VERSION}_android.apk`

例如：`res-downloader_v1.0.0_android.apk`

这样应用会自动识别并下载最新版本！

## 第四步：修改代码中的仓库地址

打开文件：`app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt`

修改这两行：

```kotlin
// 第 27 行，替换为您的仓库
private const val GITHUB_RELEASES_API = 
    "https://api.github.com/repos/YOUR-USERNAME/res-downloader-android/releases/latest"

// 第 28 行，替换为您的仓库
private const val GITHUB_REPO = 
    "https://github.com/YOUR-USERNAME/res-downloader-android"
```

替换后重新编译并发布即可！

## 📋 仓库发布检查清单

发布前检查：

- [ ] 仓库已创建并上传代码
- [ ] LICENSE 文件已上传（Apache 2.0）
- [ ] NOTICE 文件已上传
- [ ] README 中有详细说明
- [ ] APK 已编译并签名（Release 版本）
- [ ] APK 已上传到 Releases
- [ ] UpdateRepository.kt 中的地址已修改
- [ ] 版本号已正确设置

## 🔒 配置应用签名（重要！）

### 创建签名密钥

1. 在 Android Studio 中：
   - Build → Generate Signed Bundle / APK
   - Choose **APK**
   - Create new... 创建新密钥
2. 或者使用命令行：
   ```bash
   keytool -genkey -v -keystore res-downloader.jks -keyalg RSA -keysize 2048 -validity 10000 -alias res-downloader
   ```

### 配置签名到项目

在 `app/build.gradle.kts` 中添加：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("res-downloader.jks")
            storePassword = "YOUR-PASSWORD"
            keyAlias = "res-downloader"
            keyPassword = "YOUR-PASSWORD"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**重要：** 不要将密钥文件（.jks/.keystore）提交到公开仓库！

## 下一步

仓库创建好后，查看 [SYNC_FLOW.md](SYNC_FLOW.md) 了解如何跟踪和同步原项目更新。
