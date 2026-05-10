# 📦 GitHub Releases 发布指南

本文档说明如何发布新版本到 GitHub Releases。

## 发布新版本流程

### 1. 更新版本号

打开 `app/build.gradle.kts`，更新版本信息：

```kotlin
defaultConfig {
    versionCode = 2      // 每次发布 +1（重要！用于更新检测）
    versionName = "1.1.0"  // 显示给用户的版本号
}
```

### 2. 编译 APK

```bash
# Debug 版本（用于测试）
./gradlew assembleDebug

# Release 版本（用于正式发布，建议配置签名）
./gradlew assembleRelease
```

输出位置：
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### 3. 在 GitHub 创建 Release

1. 进入仓库页面：https://github.com/YanceyQian/res-downloader-android
2. 点击右侧 **Releases** → **Draft a new release**
3. 填写信息：

| 字段 | 内容 | 示例 |
|------|------|------|
| **Choose a tag** | 点击 Create new tag on publish | `v1.1.0` |
| **Release title** | 版本标题 | `1.1.0` |
| **Describe this release** | 更新说明（支持 Markdown） | 见下文示例 |
| **Attach binaries** | 拖放编译好的 APK | `res-downloader_v1.1.0_android.apk` |

### 4. 建议的更新说明格式

```markdown
## 1.1.0 (2026-05-06)

### 🔄 同步更新
- 同步原项目 putyy/res-downloader v3.x.x
- 核心功能更新和优化

### ✨ 改进
- 优化资源识别准确率
- 提高下载稳定性

### 📦 下载
- Android APK: [res-downloader_v1.1.0_android.apk](链接)
```

### 5. APK 文件命名

**重要**：推荐使用格式：`res-downloader_v{VERSION}_android.apk`

例如：`res-downloader_v1.1.0_android.apk`

这样应用才能自动识别并下载最新版本！

### 6. 发布

点击 **Publish release** 完成发布。

发布后，用户可以在应用设置中点击"检查更新"来获取新版本。

## 🔒 关于应用签名（可选）

为了确保安全性和应用商店兼容性，建议配置签名密钥后发布 Release 版本。

详细说明见 [ANDROID_BUILD_GUIDE.md](ANDROID_BUILD_GUIDE.md)。
