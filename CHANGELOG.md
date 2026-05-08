# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.2.1...v1.3.0) - 2026-05-09

### 新增功能

- ✨ **新增上游代理支持** - 支持配置 HTTP/SOCKS5 代理，可用于网络受限环境
- ✨ **新增下载代理开关** - 可选择下载时是否使用上游代理
- ✨ **新增自动代理功能** - 应用启动时自动开启 VPN 抓包
- ✨ **新增文件名长度限制** - 可配置下载文件名的最大长度
- ✨ **新增文件名时间戳** - 可选择是否为下载文件添加时间戳后缀
- ✨ **新增自定义 Headers 配置** - 支持配置 User-Agent、Referer、Cookie 等请求头
- ✨ **新增域名规则系统** - 支持自定义拦截规则，兼容原项目格式
- ✨ **新增 MIME 类型映射配置** - 可自定义文件类型与后缀的映射关系
- ✨ **新增主题切换功能** - 支持亮色/暗色主题
- ✨ **新增微信操作配置** - 可配置微信相关操作行为

### 功能优化

- 🔧 **优化多线程下载逻辑** - 完善 OkHttpClient 代理配置和动态代理支持
- 🔧 **优化文件名处理流程** - 应用 filenameLen 和 filenameTime 配置
- 🔧 **优化 HTTP 请求 Headers** - 支持根据 useHeaders 配置添加不同 Headers
- 🔧 **统一默认域名规则** - 与原项目保持一致，包含完整的域名列表
- 🔧 **添加规则同步机制** - 对话框支持立即同步最新值

### 问题修复

- 🐛 **修复 RuleSet.kt 编译错误** - 移除重复的 parse 方法定义
- 🐛 **修复 MainActivity.kt 编译错误** - 添加缺失的 TAG 常量定义
- 🐛 **修复 PreferencesManager.kt 编译错误** - 移除重复的方法定义
- 🐛 **修复 MultiThreadDownloadManager.kt 编译错误** - 修复 getDownloadProxySync() 和 SimpleDateFormat 引用
- 🐛 **修复参数未生效问题** - upstreamProxy、filenameLen、filenameTime 等参数现已正确调用

## [1.2.1](https://github.com/YanceyQian/res-downloader-android/compare/v1.2.0...v1.2.1) - 2026-05-08

### 新增功能

- ✨ **新增 MIME 类型拦截规则配置界面** - 与原项目一致的 JSON 编辑器，支持编辑模式和预览模式切换
- ✨ **新增恢复默认设置功能** - 提供快速恢复所有设置或单项设置的选项，防止误操作导致软件不可用

### 功能优化

- 🔧 **优化域名规则设置** - 修复对话框打开时的初始化时序问题，防止闪退
- 🔧 **统一默认域名规则** - 与 PreferencesManager 保持一致，包含完整的域名列表
- 🔧 域名规则对话框支持立即同步最新值，避免状态不一致

### 问题修复

- 🐛 **修复域名规则点击闪退问题** - 使用 DisposableEffect 确保对话框打开时立即初始化 ruleInput
- 🐛 修复 ruleInput 异步初始化导致的时序问题

## [1.2.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.1.1...v1.2.0) - 2026-05-08

### 新增功能

- ✨ **完成视频号下载功能完整迁移** - 从原项目 (Go + Wails) 移植到 Android 端，包括链接解析、视频信息获取、解密下载全流程
- ✨ **新增 WechatVideoDownloader.kt** - 微信视频号专用下载器

### 功能优化

- 🔧 **VPN 流量转发已修复** - 优化 ProxyVpnService.kt ByteBuffer 流量处理逻辑，解决 VPN 流量丢失问题
- 🔧 新增熵值计算 (`calculateEntropy`) - 检测数据是否为加密内容
- 🔧 新增 MP4 文件头修复 (`fixMp4Header`) - 确保解密后的视频可正常播放
- 🔧 新增密钥哈希函数 (`hashKey`) - 用于解密结果缓存和验证
- 🔧 增强平台检测 - 新增 `toPlatform()` 扩展函数支持更多平台
- 🔧 优化 ProxyVpnService.kt ByteBuffer 流量处理逻辑
- 🔧 优化 VideoDecryptor.kt 解密结果处理 - 使用命名参数提高可读性

### 问题修复

- 🐛 修复 ProxyVpnService.kt：`limit()` 函数调用语法修复
- 🐛 修复 ProxyVpnService.kt：添加 Platform 枚举类型导入
- 🐛 修复 WechatVideoDownloader.kt：OkHttp MediaType API 兼容性问题
- 🐛 修复 ProxyRepository.kt：方法名错误修复
- 🐛 修复 VideoDecryptor.kt：DecryptResult 参数类型不匹配问题

### 文档更新

- 📝 新增 MIGRATION_REPORT.md 全量功能移植详细报告

## [1.1.1](https://github.com/YanceyQian/res-downloader-android/compare/v1.1.0...v1.1.1) - 2026-05-08

### 功能优化

- 🔧 优化手动输入链接对话框，添加功能说明区分"手动输入"和"代理抓取"两种方式
- 🔧 添加 B站视频需使用代理抓取的提示说明

### 问题修复

- 🐛 修复域名规则点击闪退问题

## [1.1.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.0.1...v1.1.0) - 2026-05-08

### 新增功能

- ✨ 实现 CA 证书直接下载功能（内置证书资源，无需跳转浏览器）
- ✨ 证书下载后自动打开系统安装界面
- ✨ 更新日志链接指向项目 CHANGELOG.md 文档

### 功能优化

- 🔧 优化证书安装对话框，提供"直接下载"和"百度云网盘备用下载"两种方式
- 🔧 优化关于对话框中的证书下载按钮
- 🔧 为域名规则编辑对话框添加异常处理，防止闪退

### 问题修复

- 🐛 修复 SettingsScreen.kt 中的语法错误
- 🐛 修复证书下载导航功能异常
- 🐛 优化 .gitignore 配置

### 文档更新

- 📝 创建独立的 CHANGELOG.md 文件
- 📝 从 README.md 中移除更新日志，保持文档整洁

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
