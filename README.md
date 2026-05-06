# res-downloader Android

基于 [putyy/res-downloader](https://github.com/putyy/res-downloader) 项目移植的 Android 应用，支持视频号、小程序、抖音、快手、小红书等常见网络资源下载。

## 功能特性

- 🔒 **代理抓包**：通过 VPN Service 实现本地代理流量拦截
- 📱 **多平台支持**：微信视频号、抖音、快手、小红书、酷狗音乐、QQ 音乐等
- 🎬 **M3U8 支持**：自动解析并下载 m3u8 流媒体视频
- 📥 **下载管理**：支持下载队列、进度显示、断点续传
- 🔄 **自动更新**：集成 GitHub Releases 自动检测和安装更新
- 🌐 **多语言**：支持简体中文和英文

## ⚠️ 关于更新

**重要提示**：原项目 `putyy/res-downloader` 只发布桌面版本，不会发布 Android APK。

- 您需要自己维护 Android 版本的更新
- 可以在设置中点击"检查更新"
- 如果有 APK 会自动下载安装，否则会跳转到 GitHub Releases 页面
- 详细说明见 [更新机制文档](docs/UPDATE_MECHANISM.md)

## 📚 文档速查

| 文档 | 说明 |
|------|------|
| [QUICKSTART.md](QUICKSTART.md) | 🌟 快速开始（新手必读） |
| [GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) | 📦 创建自己的仓库并发布 |
| [SYNC_FLOW.md](docs/SYNC_FLOW.md) | 🔄 原项目更新同步流程 |
| [APP_STORE_RELEASE.md](docs/APP_STORE_RELEASE.md) | 📱 应用商店发布指南 |
| [UPDATE_MECHANISM.md](docs/UPDATE_MECHANISM.md) | 🔄 更新机制说明 |
| [ANDROID_BUILD_GUIDE.md](docs/ANDROID_BUILD_GUIDE.md) | 🏗️ Android 编译指南 |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | 📖 用户使用指南 |

## 项目结构

```
res-downloader-android/
├── app/                          # Android 应用模块
│   ├── src/main/
│   │   ├── java/com/resdownloader/
│   │   │   ├── data/             # 数据层
│   │   │   │   ├── model/         # 数据模型
│   │   │   │   ├── preferences/   # 偏好设置
│   │   │   │   └── repository/    # 数据仓库
│   │   │   ├── di/               # 依赖注入 (Hilt)
│   │   │   ├── service/          # Android 服务
│   │   │   │   ├── ProxyVpnService.kt  # VPN 代理服务
│   │   │   │   └── DownloadService.kt   # 下载服务
│   │   │   └── ui/               # UI 层 (Compose)
│   │   │       ├── screen/       # 界面
│   │   │       ├── viewmodel/    # ViewModel
│   │   │       └── theme/        # 主题
│   │   ├── res/                  # 资源文件
│   │   └── AndroidManifest.xml   # 应用清单
│   └── build.gradle.kts
├── core/                         # Go 核心库
│   ├── go.mod
│   └── resdownloader.go
├── docs/                         # 文档
│   ├── ANDROID_BUILD_GUIDE.md    # 编译指南
│   ├── UPDATE_MECHANISM.md       # 更新机制说明
│   └── USER_GUIDE.md             # 用户指南
├── build.gradle.kts
└── settings.gradle.kts
```

## 技术栈

- **语言**：Kotlin + Go
- **UI 框架**：Jetpack Compose
- **架构**：MVVM + Clean Architecture
- **依赖注入**：Hilt
- **网络**：OkHttp
- **状态管理**：StateFlow
- **存储**：DataStore Preferences
- **移动端代理**：Android VpnService

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34 (API Level 34)
- JDK 17+
- Go 1.22+ (用于编译 Go 核心库)
- Go Mobile 工具链

详细的环境配置请参考 [编译指南](docs/ANDROID_BUILD_GUIDE.md)。

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/res-downloader-android.git
cd res-downloader-android
```

### 2. 配置环境

确保已安装：
- Android Studio
- Android SDK 34
- Go 1.22+
- Go Mobile

### 3. 构建项目

#### 使用 Android Studio

1. 打开 Android Studio
2. 选择 File → Open → 选择项目目录
3. 等待 Gradle 同步完成
4. 构建 → Build APK

#### 使用命令行

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

### 4. 安装 APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

### 启动代理

1. 打开应用，进入"主页"标签
2. 点击代理开关开启代理
3. 首次使用需要授予 VPN 权限

### 下载资源

1. 开启代理后，在其他应用中访问目标资源
2. 返回应用，在资源列表中找到目标项
3. 点击下载按钮开始下载

### 配置更新

应用的自动更新功能默认检测 `putyy/res-downloader` 仓库的 Releases。
如需使用自己的更新源，修改 `UpdateRepository.kt` 中的 API 地址。

详细使用说明请参考 [用户指南](docs/USER_GUIDE.md)。

## 更新机制

应用内置自动更新功能，通过以下方式工作：

1. **版本检测**：启动时或手动检查 GitHub Releases API
2. **更新提示**：显示新版本信息和更新内容
3. **APK 下载**：下载最新 APK 到缓存目录
4. **安装更新**：调用系统安装程序

详细说明请参考 [更新机制文档](docs/UPDATE_MECHANISM.md)。

## 编译 Go 核心库（可选）

如需修改 Go 核心代码并重新编译：

```bash
cd core

# 获取依赖
go mod tidy

# 编译为 Android AAR
gomobile bind -target=android -o ../app/libs/core.aar .
```

## 权限说明

应用请求以下权限：

| 权限 | 用途 |
|------|------|
| INTERNET | 网络访问 |
| FOREGROUND_SERVICE | 前台服务（代理/下载） |
| POST_NOTIFICATIONS | 下载进度通知 |
| READ/WRITE_EXTERNAL_STORAGE | 存储文件读写 |
| READ_MEDIA_VIDEO/AUDIO | Android 13+ 媒体访问 |
| VPN_SERVICE | VPN 代理服务 |
| REQUEST_INSTALL_PACKAGES | APK 安装 |

## 常见问题

### Q: 编译失败？

- 确保 Android Studio 和 SDK 版本正确
- 检查 Gradle 和依赖版本兼容性
- 尝试 Clean Project 后重新构建

### Q: VPN 无法启动？

- 检查是否授予了 VPN 权限
- 部分设备需要额外开启"VPN 始终开启"权限

### Q: 无法拦截 HTTPS？

- 需要安装 CA 证书才能解密 HTTPS 流量
- 在设置页面按照指引安装证书

更多问题请参考 [用户指南](docs/USER_GUIDE.md)。

## 开发指南

### 添加新平台支持

在 `ProxyRepository.kt` 的 `detectPlatform` 方法中添加新的平台检测逻辑：

```kotlin
private fun detectPlatform(url: String): Platform {
    val lowerUrl = url.lowercase()
    return when {
        // 添加新平台检测
        lowerUrl.contains("your-platform.com") -> Platform.YOUR_PLATFORM
        // ...
        else -> Platform.OTHER
    }
}
```

### 添加新功能

1. 在 `data/model/` 添加数据模型
2. 在 `data/repository/` 添加数据仓库
3. 在 `ui/viewmodel/` 添加 ViewModel
4. 在 `ui/screen/` 添加界面

## 贡献

欢迎提交 Issue 和 Pull Request！

## ⚖️ 许可证和版权

本项目基于 **Apache License 2.0** 开源。

### 版权声明

```
Copyright (c) 2025 putyy (Original Project)
Copyright (c) 2025 Your Name (Android Port)
```

### 开源声明

本项目基于 [putyy/res-downloader](https://github.com/putyy/res-downloader) 开发。
- 已保留原许可证和版权声明
- 已在 NOTICE 文件中说明修改内容

详细说明请见：
- [LICENSE](LICENSE)
- [NOTICE](NOTICE)
- [OPENSOURCE.md](OPENSOURCE.md)

## 致谢

- 原始项目 [putyy/res-downloader](https://github.com/putyy/res-downloader)
- [Wails](https://wails.io/) 框架
- [Jetpack Compose](https://developer.android.com/compose)
- 所有开源贡献者
