# 爱享素材下载器 - Android

基于 [putyy/res-downloader](https://github.com/putyy/res-downloader) 项目移植的 Android 应用。

## ⚠️ 与桌面版的差异说明

本应用是针对 Android 平台特点重新设计的，**工作方式与桌面版不同**：

| 功能 | 桌面版 | Android 版（本应用） |
|------|--------|---------------------|
| 下载方式 | 代理拦截（透明抓取） | 分享链接解析 + VPN 代理抓取 |
| 平台支持 | 自动支持所有平台 | 仅支持已实现的平台 |
| 操作方式 | 在目标 APP 操作即可自动抓取 | 复制分享链接到本应用 |
| HTTPS 拦截 | 需要安装证书/代理转发 | VPN Service 可直接拦截 |

> **详细说明**：Android 版通过 VPN Service 实现代理抓取，可以拦截所有 HTTPS 流量。但与桌面版不同，Android 版无法自动识别和抓取所有平台资源。需要为每个平台单独实现 API 解析逻辑。目前已支持：
> - **分享链接解析**：抖音、快手、小红书、B站、网易云音乐、微信视频号
> - **代理抓取还支持**：QQ音乐、酷狗音乐

## 🎉 爱享素材下载器

一款简洁易用的网络资源下载工具，支持分享链接解析和代理抓取两种方式获取视频、音频、图片等资源。

## ✨ 功能特色

- 🚀 **简单易用**：复制分享链接即可下载，无需复杂设置
- 📱 **多平台支持**：抖音、快手、小红书、B站、网易云音乐、微信视频号、QQ音乐、酷狗音乐等
- 🌐 **多资源类型支持**：视频 / 音频 / 图片 / m3u8 / 直播流等
- 🔒 **VPN 代理抓取**：开启代理后自动捕获应用内资源
- 🔗 **分享链接解析**：粘贴分享链接直接解析下载
- 🎬 **M3U8 支持**：自动解析并下载 m3u8 流媒体视频
- 📥 **下载管理**：支持下载队列、进度显示、断点续传
- 🔄 **自动更新**：集成 GitHub Releases 自动检测和安装更新
- 🌍 **多语言**：支持简体中文和英文
- ⚙️ **动态规则系统**：远程配置更新，平台解析逻辑热更新

## 📚 文档 & 版本

- 📘 [用户指南](docs/USER_GUIDE.md)
- 🏗️ [编译指南](docs/ANDROID_BUILD_GUIDE.md)
- 📦 [发布指南](docs/GITHUB_RELEASE.md)
- 🧩 [最新版](https://github.com/YanceyQian/res-downloader-android/releases)

## 🧩 下载地址

- 🆕 [GitHub 下载](https://github.com/YanceyQian/res-downloader-android/releases)

## 📖 快速开始

### 方式一：分享链接解析（推荐）

1. 在目标应用中复制分享链接
2. 打开本应用，链接会自动粘贴
3. 点击解析并下载

### 方式二：代理抓取

1. 打开应用，点击代理开关开启代理
2. 在其他应用中访问目标资源
3. 返回应用，在资源列表中找到目标项
4. 点击下载按钮开始下载

详细使用说明请参考 [用户指南](docs/USER_GUIDE.md)。

## 📚 文档速查

| 文档 | 说明 |
|------|------|
| [CHANGELOG.md](CHANGELOG.md) | 📝 更新日志 |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | 📖 用户使用指南 |
| [UPDATE_MECHANISM.md](docs/UPDATE_MECHANISM.md) | 🔄 更新机制说明 |
| [ANDROID_BUILD_GUIDE.md](docs/ANDROID_BUILD_GUIDE.md) | 🏗️ 开发者编译指南 |
| [APP_STORE_RELEASE.md](docs/APP_STORE_RELEASE.md) | 📱 应用商店发布指南 |
| [SYNC_FLOW.md](docs/SYNC_FLOW.md) | 🔄 同步原项目更新流程 |
| [GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) | 📦 GitHub Releases 发布指南 |

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
├── core/                         # Go 核心代码（暂未集成）
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

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM + Clean Architecture
- **依赖注入**：Hilt
- **网络**：OkHttp
- **状态管理**：StateFlow
- **存储**：DataStore Preferences
- **移动端代理**：Android VpnService
- **平台解析**：各平台独立 Downloader

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34 (API Level 34)
- JDK 17+（Android Studio 自带）

详细的环境配置请参考 [编译指南](docs/ANDROID_BUILD_GUIDE.md)。

## 💡 实现原理

### Android 版工作原理

本应用采用两种方式获取资源：

**方式一：分享链接解析**
```
用户复制分享链接 → 应用解析链接 → 获取资源地址 → 下载
```

通过为每个平台实现独立的解析逻辑，根据分享链接获取真实的资源下载地址。

**方式二：VPN 代理抓取**
```
开启代理 → 应用拦截流量 → 筛选资源 → 展示下载
```

通过 Android VpnService 实现本地代理，可以拦截本机所有应用的 HTTPS 流量。

### Android 版 vs 桌面版

| 对比项 | 桌面版 | Android 版 |
|--------|--------|------------|
| 代理方式 | MITM 代理 + 插件系统 | VPN Service + API 解析 + 远程配置 |
| 平台支持 | 通过域名规则自动支持 | 需要为每个平台单独实现（支持远程配置热更新） |
| 资源获取 | 自动捕获所有流量 | 需手动复制分享链接 或 开启代理 |
| 证书安装 | 需要安装证书 | VPN Service 不受证书限制 |
| 使用场景 | 电脑端操作 | 移动端随时随地使用 |

Android 版保留了桌面版的核心功能，但在实现上针对移动端特点进行了优化。

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

## 🧠 更多问题

- [GitHub Issues](https://github.com/YanceyQian/res-downloader-android/issues)
- [常见问题 - 用户指南](docs/USER_GUIDE.md#常见问题)

## ⚖️ 许可证和版权

本项目基于 **Apache License 2.0** 开源。

### 版权声明

```
Copyright (c) 2025 putyy (Original Project)
Copyright (c) 2025 YanceyQian (Android Port)
```

### 开源声明

本项目基于 [putyy/res-downloader](https://github.com/putyy/res-downloader) 开发。
- 遵循 Apache License 2.0 许可证
- 保留原许可证和版权声明

详细说明请见：
- [LICENSE](LICENSE)
- [NOTICE](NOTICE)
- [OPENSOURCE.md](OPENSOURCE.md)

## 致谢

- 原始项目 [putyy/res-downloader](https://github.com/putyy/res-downloader)
- [Wails](https://wails.io/) 框架
- [Jetpack Compose](https://developer.android.com/compose)
- 所有开源贡献者

---

如有任何问题或建议，欢迎反馈！
