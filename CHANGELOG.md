# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
