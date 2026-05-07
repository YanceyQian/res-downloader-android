# 爱享素材下载器 - Android

基于 [putyy/res-downloader](https://github.com/putyy/res-downloader) 项目移植的 Android 应用，支持视频号、小程序、抖音、快手、小红书等常见网络资源下载。

## 🎉 爱享素材下载器

一款专业的网络资源下载工具，简洁易用，支持多种资源嗅探与下载。

## ✨ 功能特色

- 🚀 **简单易用**：操作简单，界面清晰美观
- 📱 **多平台支持**：微信视频号、抖音、快手、小红书、酷狗音乐、QQ 音乐等
- 🌐 **多资源类型支持**：视频 / 音频 / 图片 / m3u8 / 直播流等
- 🔒 **代理抓包**：通过 VPN Service 实现本地代理流量拦截
- 🎬 **M3U8 支持**：自动解析并下载 m3u8 流媒体视频
- 📥 **下载管理**：支持下载队列、进度显示、断点续传
- 🔄 **自动更新**：集成 GitHub Releases 自动检测和安装更新
- 🌍 **多语言**：支持简体中文和英文

## 📚 文档 & 版本

- 📘 [用户指南](docs/USER_GUIDE.md)
- 🏗️ [编译指南](docs/ANDROID_BUILD_GUIDE.md)
- 📦 [发布指南](docs/GITHUB_RELEASE.md)
- 🧩 [最新版](https://github.com/YanceyQian/res-downloader-android/releases)

## 🧩 下载地址

- 🆕 [GitHub 下载](https://github.com/YanceyQian/res-downloader-android/releases)

## 📖 快速开始

### 启动代理

1. 打开应用，进入「主页」标签
2. 点击代理开关开启代理
3. 首次使用需要授予 VPN 权限

### 下载资源

1. 开启代理后，在其他应用中访问目标资源
2. 返回应用，在资源列表中找到目标项
3. 点击下载按钮开始下载

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

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34 (API Level 34)
- JDK 17+（Android Studio 自带）

详细的环境配置请参考 [编译指南](docs/ANDROID_BUILD_GUIDE.md)。

## 💡 实现原理 & 初衷

本工具通过代理方式实现网络抓包，并筛选可用资源。与 Fiddler、Charles、浏览器 DevTools 原理类似，但对资源进行了更友好的筛选、展示和处理，大幅度降低了使用门槛，更适合大众用户使用。

### 关于安卓版工作原理

**Q: 安卓 7.0+ 系统还能拦截 HTTPS 流量吗？**

**A: 可以！** 🎉 本安卓版应用与原桌面版的使用方式不同：

- **原桌面版**：电脑运行软件 → 手机设置代理连接电脑（受限于安卓系统证书信任机制）
- **本安卓版**：直接在手机上通过 **VPN Service** 实现本地代理拦截，不受系统证书限制

即使安卓 7.0+ 不再信任用户证书，本应用依然可以正常工作！

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
