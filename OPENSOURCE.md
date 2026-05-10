# 开源声明

## 本项目基于

[res-downloader](https://github.com/putyy/res-downloader) 项目，由 putyy 开发。

## 许可证

本项目使用 **Apache License 2.0** 许可证。

## 修改说明

本项目对原始代码进行了以下修改：

### 新增功能
- Android 原生 UI（基于 Jetpack Compose）
- VPN Service 实现本地代理
- 分享链接解析功能（各平台独立 Downloader）
- Android 下载管理系统
- GitHub Releases 自动更新机制
- 多语言支持（中文/英文）

### 架构调整
- 采用分享链接解析 + 代理抓取双模式
- 为每个平台实现独立的 Downloader
- 使用 Kotlin 重写了业务逻辑层
- 实现了 Android 特有的生命周期管理

### 与桌面版的差异

| 对比项 | 桌面版 | Android 版（本项目） |
|--------|--------|---------------------|
| 平台支持 | 自动支持所有平台 | 仅支持已实现的平台 |
| 操作方式 | 在目标 APP 操作即可自动抓取 | 复制分享链接 或 开启代理 |
| 技术实现 | MITM 代理 + 插件系统 | VPN Service + API 解析 |

**说明**：Android 版针对移动端特点进行了优化，采用分享链接解析作为主要方式，代理抓取作为补充。

## 原始项目版权

Copyright (c) 2023-2025 putyy

## 使用声明

本项目保留原项目的所有版权声明。根据 Apache License 2.0 许可证的要求，我们明确说明对原代码的修改。

任何使用本项目的用户都应同时遵守原项目的许可条款。
