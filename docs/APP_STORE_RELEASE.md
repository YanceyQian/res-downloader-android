# 📱 应用商店发布指南

## ⚠️ 重要提示：代理应用的限制

**请注意**：由于此应用使用 VPN 代理和抓包功能，可能会被部分应用商店拒绝或限制。

### 推荐发布渠道

| 平台 | 推荐度 | 说明 |
|------|--------|------|
| GitHub Releases | ⭐⭐⭐⭐⭐ | 最简单，无审核 |
| 酷安 / 应用汇 | ⭐⭐⭐⭐ | 国内第三方商店 |
| APK 分享网站 | ⭐⭐⭐ | 如 APKPure, APKCombo 等 |
| Google Play | ⭐⭐ | 可能需要审核，建议用自己的签名 |
| 国内厂商商店 | ⭐ | 审核可能严格 |

## 发布前准备

### 1. 配置应用信息

修改 `app/build.gradle.kts`：

```kotlin
defaultConfig {
    applicationId = "com.resdownloader"  // 或您的包名
    versionCode = 1                      // 每次发布 +1
    versionName = "1.0.0"                // 语义化版本
}
```

### 2. 签名 APK

参考 [GITHUB_RELEASE.md](GITHUB_RELEASE.md) 中的签名配置。

### 3. 准备应用图标和截图

#### 图标尺寸要求
| 尺寸 | 用途 |
|------|------|
| 512x512 | Google Play 商店图标 |
| 192x192 | 自适应图标 |
| 48x48, 72x72, 96x96, 144x144, 192x192 | mipmap |

#### 截图尺寸建议
- 手机：1080x1920（9:16）
- 平板：2048x1536（4:3）

截图内容：
- 首页（代理开关界面）
- 资源列表
- 下载管理
- 设置页面

### 4. 准备应用描述

#### 中文（大陆应用商店）

```
【应用名称】爱享素材下载器

【应用介绍】
一款专业的网络资源下载工具，支持多种平台资源抓取和下载。

【主要功能】
✓ 代理抓包：通过本地代理捕获网络资源
✓ 多平台支持：视频号、抖音、快手、小红书等
✓ M3U8下载：支持流媒体下载
✓ 下载管理：支持后台下载、进度显示
✓ 自动更新：第一时间获取新版本

【温馨提示】
1. 本工具仅用于下载您拥有合法使用权的资源
2. 使用时请遵守相关平台服务条款
3. 请妥善保管证书，安装证书时请确认来源

【开源声明】
本应用基于 putyy/res-downloader 开发，遵循 Apache 2.0 开源协议。
```

#### 英文（Google Play）

```
【App Name】Res Downloader

【Description】
A professional network resource download tool, supporting multiple platforms.

【Features】
✓ Proxy capture: Capture network resources through local proxy
✓ Multi-platform support: Multiple mainstream platforms
✓ M3U8 download: Streaming media support
✓ Download management: Background download with progress
✓ Auto update: Get latest version quickly

【Notice】
1. This tool is only for downloading resources you have legal rights to use
2. Please comply with relevant platform terms of service
3. Please verify certificate sources when installing

【Open Source】
Based on putyy/res-downloader, Apache 2.0 License.
```

## GitHub Releases 发布（最简单）

这是最简单且最稳定的发布方式！

详细步骤见 [GITHUB_RELEASE.md](GITHUB_RELEASE.md)。

### 优势
- ✅ 无审核，立即发布
- ✅ 支持自动更新
- ✅ 用户下载方便
- ✅ 可以直接在应用内更新

### 使用自动更新
用户可以在应用内：
1. 打开设置
2. 点击"检查更新"
3. 自动下载安装（如果有 APK）

## Google Play 发布

### 前置条件
- 拥有 Google 开发者账号（25美元注册费）
- 应用已签名
- 准备好图标和截图

### 发布步骤

1. 访问 [Google Play Console](https://play.google.com/console)
2. 创建应用
3. 填写信息（名称、描述、分类等）
4. 上传 APK/AAB
5. 填写内容分级和隐私政策
6. 提交审核

### 注意事项
- 可能需要隐私政策 URL
- 可能需要说明 VPN 用途
- 可能需要功能演示视频

## 国内应用商店发布

### 通用要求
- 企业资质（大部分国内商店需要）
- 软件著作权（可选，建议申请）
- 隐私政策
- 用户协议

### 隐私政策模板

```
隐私政策

1. 数据收集
本应用不收集用户的个人隐私数据。

2. 网络访问
本应用使用网络访问功能用于：
- 资源下载
- 更新检查
- 本地代理服务

3. 本地存储
本应用仅在本地存储下载的文件，不会上传到任何服务器。

4. 隐私安全
您的所有隐私数据都在您的设备本地处理。

如有疑问，请联系我们。
```

### 快速发布（推荐方案）

**最适合个人开发者的方案：**

1. 将 APK 发布到 GitHub Releases
2. 分享下载链接给用户
3. 用户通过 GitHub Releases 更新

优点：
- 不需要申请企业资质
- 不需要复杂的审核流程
- 用户可以直接在应用内更新
- 完全免费！

## 📋 发布检查清单

发布前检查：

- [ ] 应用图标已准备（512x512）
- [ ] 截图已准备（至少 4 张）
- [ ] APK 已签名
- [ ] 版本号已更新
- [ ] 描述文案已准备
- [ ] 隐私政策已准备（如需）
- [ ] GitHub Releases 已发布（如需）
- [ ] 应用内更新地址已配置
- [ ] LICENSE 和 NOTICE 文件已包含

## 📞 获取帮助

如遇到问题：
1. 查看原项目 Issues
2. 在自己的仓库提 Issue
3. 查看本文档的其他部分
