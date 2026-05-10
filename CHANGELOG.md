# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.3.0...v2.0.0) - 2026-05-10

### 新增功能

- ✨ **平台下载器架构完成** - 新增统一 `PlatformDownloader` 接口，实现下载器工厂模式，支持多平台自动识别和灵活扩展
- ✨ **抖音下载器** - `DouyinDownloader` 实现分享链接解析，支持视频/图集下载
- ✨ **快手下载器** - `KuaishouDownloader` 实现分享链接解析
- ✨ **小红书下载器** - `XiaohongshuDownloader` 实现图文/视频笔记解析，支持批量下载
- ✨ **B站下载器** - `BilibiliDownloader` 实现分享链接解析，含弹幕/字幕下载支持
- ✨ **网易云下载器** - `NeteaseDownloader` 实现音频/歌词解析
- ✨ **通用下载器** - `GeneralDownloader` 实现直链/M3U8 下载兜底支持
- ✨ **微信视频号 Frida Gadget 集成方案** - 提供非 Root 用户的高成功率抓取方案
- ✨ **错误分类体系** - 新增 `WechatErrorType`、`PlatformErrorType`、`PlatformErrorDetail` 枚举
- ✨ **公告通知系统** - 通过 `announcement.json` 配置文件实现应用内公告横幅
- ✨ **连续失败检测** - 连续 3 次 API 错误自动判定为接口失效，触发用户提醒
- ✨ **UI 提醒组件** - 新增警告条、更新对话框、公告横幅等交互组件

### 功能优化

- 🔧 **MainViewModel 重构** - 集成下载器工厂和错误处理逻辑
- 🔧 **平台检测增强** - `PlatformDownloader` 自动检测 URL 所属平台类型
- 🔧 **资源类型规范化** - 复用 `ResourceType` 枚举统一资源类型管理
- 🔧 **微信下载器优化** - 添加错误回调机制，完善异常处理
- 🔧 **语言国际化完善** - 优化中英文切换功能
- 🔧 **M3U8 下载增强** - 完善分片下载和合并逻辑

### 问题修复

- 🐛 **修复 ResourceType.kt 语法错误** - 移除 enum 条目尾随逗号并添加分号
- 🐛 **文档平台描述统一** - 修复 README.md 和 USER_GUIDE.md 中关于 QQ音乐、酷狗音乐状态的描述不一致
- 🐛 **版本号同步** - 修复 USER_GUIDE.md 中版本号显示为旧版本的问题

### 文档更新

- 📝 **移植报告** - 新增非 Root 用户完整解决方案章节
- 📝 **WECHAT_HOOK_INTEGRATION.md** - 微信 Frida Gadget 详细集成方案
- 📝 **WECHAT_UPDATE_STRATEGY.md** - 微信版本更新应对策略
- 📝 **README.md** - 添加平台支持说明、与桌面版差异说明
- 📝 **USER_GUIDE.md** - 完善平台支持列表和常见问题解答
- 📝 **OPENSOURCE.md** - 更新开源声明和架构差异说明
- 📝 **APP_STORE_RELEASE.md** - 完善应用商店发布指南

## [1.3.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.2.1...v1.3.0) - 2026-05-09

### 新增功能

- ✨ **完成视频号下载功能完整迁移** - 从原项目 (Go + Wails) 移植到 Android 端
- ✨ **新增 WechatVideoDownloader.kt** - 微信视频号专用下载器

### 功能优化

- 🔧 **VPN 流量转发已修复** - 优化 ProxyVpnService.kt ByteBuffer 流量处理逻辑
- 🔧 新增熵值计算 (`calculateEntropy`) - 检测数据是否为加密内容
- 🔧 新增 MP4 文件头修复 (`fixMp4Header`) - 确保解密后的视频可正常播放
- 🔧 新增密钥哈希函数 (`hashKey`) - 用于解密结果缓存和验证
- 🔧 增强平台检测 - 新增 `toPlatform()` 扩展函数支持更多平台

### 问题修复

- 🐛 修复 ProxyVpnService.kt：`limit()` 函数调用语法修复
- 🐛 修复 ProxyVpnService.kt：添加 Platform 枚举类型导入
- 🐛 修复 WechatVideoDownloader.kt：OkHttp MediaType API 兼容性问题
- 🐛 修复 ProxyRepository.kt：方法名错误修复
- 🐛 修复 VideoDecryptor.kt：DecryptResult 参数类型不匹配问题

### 文档更新

- 📝 新增 MIGRATION_REPORT.md 全量功能移植详细报告

## [1.2.1](https://github.com/YanceyQian/res-downloader-android/compare/v1.2.0...v1.2.1) - 2026-05-08

### 新增功能

- ✨ **新增 MIME 类型拦截规则配置界面** - JSON 编辑器支持编辑模式和预览模式切换
- ✨ **新增恢复默认设置功能** - 快速恢复所有设置或单项设置

### 功能优化

- 🔧 **优化域名规则设置** - 修复对话框打开时的初始化时序问题
- 🔧 **统一默认域名规则** - 与 PreferencesManager 保持一致
- 🔧 域名规则对话框支持立即同步最新值

### 问题修复

- 🐛 **修复域名规则点击闪退问题** - 使用 DisposableEffect 确保对话框初始化

## [1.2.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.1.1...v1.2.0) - 2026-05-08

### 新增功能

- ✨ **新增上游代理支持** - 支持配置 HTTP/SOCKS5 代理
- ✨ **新增下载代理开关** - 可选择下载时是否使用上游代理
- ✨ **新增自动代理功能** - 应用启动时自动开启 VPN 抓包
- ✨ **新增文件名长度限制** - 可配置下载文件名的最大长度
- ✨ **新增文件名时间戳** - 可选择为下载文件添加时间戳后缀
- ✨ **新增自定义 Headers 配置** - 支持配置 User-Agent、Referer、Cookie
- ✨ **新增域名规则系统** - 支持自定义拦截规则
- ✨ **新增 MIME 类型映射配置** - 自定义文件类型与后缀映射
- ✨ **新增主题切换功能** - 支持亮色/暗色主题
- ✨ **新增微信操作配置** - 可配置微信相关操作行为
- ✨ **新增移植报告文档** - MIGRATION_REPORT.md

### 功能优化

- 🔧 **优化多线程下载逻辑** - 完善 OkHttpClient 代理配置
- 🔧 **优化文件名处理流程** - 应用 filenameLen 和 filenameTime 配置
- 🔧 **优化 HTTP 请求 Headers** - 支持根据配置添加不同 Headers
- 🔧 **VPN 核心逻辑全面排查** - 修复 ProxyVpnService.kt 多处潜在问题
- 🔧 **解密流程重构** - 使用 VideoDecryptor 替代错误的 AesUtils 方法
- 🔧 **B站弹幕/字幕下载支持**

### 问题修复

- 🐛 修复多文件编译错误

## [1.1.1](https://github.com/YanceyQian/res-downloader-android/compare/v1.1.0...v1.1.1) - 2026-05-08

### 功能优化

- 🔧 优化手动输入链接对话框
- 🔧 添加 B站视频需使用代理抓取的提示说明

### 问题修复

- 🐛 修复域名规则点击闪退问题

## [1.1.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.0.1...v1.1.0) - 2026-05-08

### 新增功能

- ✨ 实现 CA 证书直接下载功能
- ✨ 证书下载后自动打开系统安装界面
- ✨ 更新日志链接指向项目 CHANGELOG.md

### 功能优化

- 🔧 优化证书安装对话框
- 🔧 为域名规则编辑对话框添加异常处理

### 问题修复

- 🐛 修复 SettingsScreen.kt 中的语法错误
- 🐛 修复证书下载导航功能异常

### 文档更新

- 📝 创建独立的 CHANGELOG.md 文件

## [1.0.1](https://github.com/YanceyQian/res-downloader-android/compare/v1.0.0...v1.0.1) - 2026-05-08

### 问题修复

- 🐛 修复构建错误
- 🐛 修复 SettingsScreen.kt 中的语法问题

### 功能优化

- 🔧 优化项目配置

## [1.0.0](https://github.com/YanceyQian/res-downloader-android/releases/tag/v1.0.0) - 2026-05-06

### 新增功能

- ✨ 初始发布
- ✨ 支持多平台资源下载（微信视频号、抖音、快手、小红书等）
- ✨ 实现代理抓包功能
- ✨ 完整的 Android 应用界面
- ✨ 支持 m3u8 流媒体下载
- ✨ 自动更新功能
- ✨ 多语言支持（简体中文、英文）
