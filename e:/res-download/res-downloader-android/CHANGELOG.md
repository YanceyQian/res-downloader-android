# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [待发布版本]

### 规划中

- 🔮 **动态规则系统** - 平台规则远程配置系统可行性分析完成，详见 [docs/PLUGIN_SYSTEM_FEASIBILITY.md](docs/PLUGIN_SYSTEM_FEASIBILITY.md)
- 🔮 **配置同步机制** - 与原项目同步流程增强，新增远程配置同步章节，详见 [docs/SYNC_FLOW.md](docs/SYNC_FLOW.md)

### 文档更新

- 📝 新增 [PLUGIN_SYSTEM_FEASIBILITY.md](docs/PLUGIN_SYSTEM_FEASIBILITY.md) - 动态规则系统可行性分析报告
- 📝 更新 [SYNC_FLOW.md](docs/SYNC_FLOW.md) - 新增平台规则远程配置同步章节
- 📝 更新 [README.md](README.md) - 添加动态规则系统功能介绍
- 📝 更新 [UPDATE_MECHANISM.md](docs/UPDATE_MECHANISM.md) - 新增远程配置更新说明
- 📝 更新 [OPENSOURCE.md](OPENSOURCE.md) - 更新技术架构说明
- 📝 更新 [MIGRATION_REPORT.md](MIGRATION_REPORT.md) - 添加未来规划章节
- 📝 更新 [USER_GUIDE.md](docs/USER_GUIDE.md) - 添加配置更新说明

## [2.0.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.3.0...v2.0.0) - 2026-05-10

### 新增功能

- ✨ **统一下载器架构** - 新增统一下载接口，支持多平台自动识别和扩展
- ✨ **抖音下载** - 支持分享链接解析，视频/图集下载
- ✨ **快手下载** - 支持分享链接解析
- ✨ **小红书下载** - 支持图文/视频笔记解析，批量下载
- ✨ **B站下载** - 支持分享链接解析，弹幕/字幕下载
- ✨ **网易云音乐下载** - 支持音频/歌词解析
- ✨ **通用下载** - 支持直链/M3U8 下载
- ✨ **错误分类体系** - 新增错误类型枚举，错误处理更精准
- ✨ **公告通知系统** - 应用内公告横幅通知
- ✨ **连续失败检测** - API 错误自动检测并提醒
- ✨ **UI 提醒组件** - 新增警告条、更新对话框等交互组件

### 功能优化

- 🔧 主界面和下载流程重构
- 🔧 平台检测增强，自动识别 URL 所属平台
- 🔧 资源类型管理规范化
- 🔧 国际化完善，优化中英文切换
- 🔧 下载功能增强，优化分片和合并逻辑

### 问题修复

- 🐛 修复数据模型语法错误
- 🐛 文档描述不一致修复
- 🐛 版本号同步修复

### 文档更新

- 📝 新增移植报告文档
- 📝 新增集成方案文档
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
