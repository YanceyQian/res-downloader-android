# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0](https://github.com/YanceyQian/res-downloader-android/compare/v1.0.1...v1.1.0) - 2026-05-08

### 新增功能

- ✨ 实现 CA 证书直接下载功能（内置证书资源，无需跳转浏览器）
- ✨ 证书下载后自动打开系统安装界面
- ✨ 更新日志链接指向项目 CHANGELOG.md 文档

### 功能优化

- 🔧 优化证书安装对话框，提供"直接下载"和"GitHub备用下载"两种方式
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
