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
- Android 下载管理系统
- GitHub Releases 自动更新机制
- 多语言支持（中文/英文）

### 架构调整
- 将 Go 核心代码包装为 Android 可调用的模块
- 使用 Kotlin 重写了业务逻辑层
- 实现了 Android 特有的生命周期管理

## 原始项目版权

Copyright (c) 2023-2025 putyy

## 使用声明

本项目保留原项目的所有版权声明。根据 Apache License 2.0 许可证的要求，我们明确说明对原代码的修改。

任何使用本项目的用户都应同时遵守原项目的许可条款。
