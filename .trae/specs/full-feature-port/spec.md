# 资源下载器安卓端全量功能移植 - 产品需求文档

## Overview
- **Summary**: 将GitHub项目`res-downloader`的桌面端功能完整移植到安卓平台，确保功能一致、体验相近
- **Purpose**: 让安卓用户也能享受网络资源嗅探和高速下载功能
- **Target Users**: 需要下载网络资源（视频号、抖音、快手、小红书等平台）的安卓用户

## Goals
- [x] 完整移植桌面端所有设置项功能
- [x] 实现平台支持的全量移植（微信视频号、抖音、快手、小红书、B站、酷狗、QQ音乐）
- [x] 修复界面显示问题，提升用户体验
- [x] 确保软件稳定性和兼容性

## Non-Goals (Out of Scope)
- 桌面端特有功能（如系统代理设置、管理员授权等）
- 复杂的窗口管理功能
- macOS/Linux特定的系统集成

## Background & Context
- 原项目是基于Wails框架的桌面应用
- 安卓端使用Jetpack Compose构建
- 核心功能：VPN代理抓包、资源识别、文件下载

## Functional Requirements
- **FR-1**: 基础设置功能（主题、语言、下载路径、画质、文件命名规则）
- **FR-2**: 网络设置功能（Host、端口、代理配置）
- **FR-3**: 高级设置功能（连接数、下载数、UserAgent、Headers、域名规则、MIME映射）
- **FR-4**: 关于界面功能（证书下载、源码链接、更新日志）
- **FR-5**: 平台支持（微信视频号、抖音、快手、小红书、B站、酷狗、QQ音乐）

## Non-Functional Requirements
- **NFR-1**: 界面响应式设计，适配不同屏幕尺寸
- **NFR-2**: 流畅的用户体验，无明显卡顿
- **NFR-3**: 符合安卓设计规范

## Constraints
- **Technical**: 安卓SDK 24+，Jetpack Compose
- **Business**: 保持与原项目功能一致

## Assumptions
- 用户已了解VPN代理的使用方式
- 用户接受安卓端与桌面端在操作方式上的差异

## Acceptance Criteria

### AC-1: 域名规则显示正确
- **Given**: 用户打开设置界面
- **When**: 点击域名规则设置项
- **Then**: 显示完整的默认规则列表（包含视频号、抖音、快手、小红书、B站等域名）
- **Verification**: `human-judgment`

### AC-2: 规则对话框界面正常
- **Given**: 用户打开域名规则对话框
- **When**: 查看对话框内容
- **Then**: 对话框标题与内容不重叠，布局正常
- **Verification**: `human-judgment`

### AC-3: 平台识别功能正常
- **Given**: 用户输入不同平台的链接
- **When**: 点击添加按钮
- **Then**: 正确识别平台类型（微信、抖音、快手、小红书、B站等）
- **Verification**: `programmatic`

### AC-4: 关于界面功能完整
- **Given**: 用户打开关于对话框
- **When**: 点击各个功能按钮
- **Then**: 证书下载、源码链接、更新日志功能正常触发
- **Verification**: `human-judgment`

### AC-5: 编译成功
- **Given**: 执行编译命令
- **When**: 编译过程完成
- **Then**: 生成APK文件，无编译错误
- **Verification**: `programmatic`

## Open Questions
- [ ] 是否需要添加微博等其他平台支持？
- [ ] 是否需要添加深色/浅色主题切换动画？