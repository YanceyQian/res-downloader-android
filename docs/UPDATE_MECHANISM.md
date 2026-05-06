# 自动更新机制说明文档

本文档详细说明爱享素材下载器 Android 应用自动更新机制的实现原理和使用方法。

## 概述

应用内置的自动更新机制通过以下方式实现：

1. **版本检测**：应用启动时或用户手动触发检查最新版本
2. **信息获取**：通过 GitHub Releases API 获取最新版本信息
3. **更新提示**：向用户展示更新内容和下载选项
4. **APK 下载**：下载最新版本的 APK 文件
5. **安装更新**：调用系统安装程序完成更新

## ⚠️ 重要提示

原项目 `putyy/res-downloader` 目前**只发布桌面版本**（Windows/macOS/Linux），不会发布 Android APK。因此，您需要：

### 方案一：维护自己的 Android 版本仓库

1. Fork 或创建自己的 GitHub 仓库
2. 发布 Android 版本到该仓库的 Releases
3. 修改代码中的仓库地址（见下面的配置说明）

### 方案二：每次编译后手动发布

每次更新原项目后：
1. 下载并集成原项目最新代码
2. 编译 Android APK
3. 手动发布到自己的 GitHub Releases

### 方案三：直接在应用内提供下载入口

目前代码已支持：如果找不到 APK 文件，会提供按钮让用户跳转到 GitHub Releases 页面手动下载。

## 配置说明

### 修改更新源仓库

在文件 [UpdateRepository.kt](../app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt#L27) 中：

```kotlin
private const val GITHUB_RELEASES_API = 
    "https://api.github.com/repos/your-username/res-downloader-android/releases/latest"
private const val GITHUB_REPO = 
    "https://github.com/your-username/res-downloader-android"
```

将 `your-username` 替换为您的 GitHub 用户名，并确保仓库名称正确。

### 发布 Android 版本

发布新版本到 GitHub Releases 时：

1. 编译 Release APK
2. 进入 GitHub 仓库 → Releases → Draft a new release
3. 填写信息：
   - Tag version: `v1.0.0`（必须遵循语义化版本规范）
   - Release title: `1.0.0`
   - Description: 更新说明（支持 Markdown）
4. 上传 APK 文件，文件命名建议：`res-downloader_v1.0.0_android.apk`
5. 点击 Publish release

### 应用更新逻辑

应用会：
1. 请求 GitHub Releases API
2. 解析版本号，判断是否有更新
3. 查找以 `.apk` 结尾的附件
4. 如果找到，显示"立即更新"按钮；否则提供"前往 GitHub 下载"按钮

## 技术实现

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    Android 应用层                        │
├─────────────────────────────────────────────────────────┤
│  SettingsViewModel                                      │
│  ├── checkForUpdates()  ──→  UpdateRepository           │
│  └── downloadUpdate()    ──→  UpdateRepository           │
├─────────────────────────────────────────────────────────┤
│                    数据层                               │
├─────────────────────────────────────────────────────────┤
│  UpdateRepository                                        │
│  ├── checkForUpdates(): 调用 GitHub API                 │
│  ├── downloadUpdate(): 下载 APK 文件                    │
│  └── 状态管理 (StateFlow)                               │
├─────────────────────────────────────────────────────────┤
│                    网络层                               │
├─────────────────────────────────────────────────────────┤
│  OkHttpClient                                           │
│  └── GitHub Releases API                                │
└─────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. UpdateRepository

`UpdateRepository` 是更新功能的核心类，负责：

- 调用 GitHub Releases API 获取版本信息
- 解析 API 返回的 JSON 数据
- 比较版本号判断是否有新版本
- 下载 APK 文件
- 管理更新状态

关键代码位置：`app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt`

#### 2. UpdateState 状态机

更新流程通过 `UpdateState` 密封类管理：

```kotlin
sealed class UpdateState {
    object Idle : UpdateState()              // 空闲状态
    data class Downloading(val progress: Int) // 下载中
    data class Downloaded(val filePath: String) // 已下载
    data class Error(val message: String)    // 错误状态
}
```

#### 3. SettingsViewModel

`SettingsViewModel` 负责：

- 管理更新相关的 UI 状态
- 处理用户交互
- 触发更新检查和下载
- 发送 UI 事件

### GitHub API 集成

#### API 端点

```
GET https://api.github.com/repos/putyy/res-downloader/releases/latest
```

#### 请求头

```http
Accept: application/vnd.github.v3+json
```

#### 响应格式

```json
{
  "tag_name": "v3.1.2",
  "name": "3.1.2",
  "body": "版本更新说明...",
  "html_url": "https://github.com/putyy/res-downloader/releases/tag/v3.1.2",
  "assets": [
    {
      "name": "res-downloader_v3.1.2_android.apk",
      "browser_download_url": "https://github.com/...",
      "size": 15728640
    }
  ]
}
```

### 版本号比较算法

使用语义化版本（Semantic Versioning）比较：

```kotlin
private fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

    val maxLen = maxOf(parts1.size, parts2.size)

    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }

        if (p1 != p2) {
            return p1.compareTo(p2)
        }
    }

    return 0
}
```

**比较规则**：
- `3.1.2` > `3.1.1` → 返回 1
- `3.2.0` > `3.1.9` → 返回 1
- `3.1.2` = `3.1.2` → 返回 0
- `3.1.2` < `3.1.3` → 返回 -1

### 更新流程

#### 流程图

```
用户触发检查
     ↓
调用 GitHub API
     ↓
获取最新版本信息
     ↓
比较版本号
     ↓
┌─────────────────────────┐
│  发现新版本？            │
└─────────────────────────┘
     ↓              ↓
    是             否
     ↓              ↓
显示更新对话框    提示"已是最新版本"
     ↓
用户点击"立即更新"
     ↓
下载 APK 文件
     ↓
显示下载进度
     ↓
下载完成
     ↓
调用系统安装程序
     ↓
用户安装完成
```

#### 代码实现

```kotlin
// 检查更新
suspend fun checkForUpdates(): Result<VersionInfo> {
    val response = okHttpClient.get(GITHUB_RELEASES_API)
    val versionInfo = parseVersionInfo(response)

    // 比较版本
    val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME)
    val latestVersion = normalizeVersion(versionInfo.tagName)

    return if (compareVersions(currentVersion, latestVersion) < 0) {
        Result.success(versionInfo)
    } else {
        Result.failure(Exception("Already up to date"))
    }
}

// 下载更新
suspend fun downloadUpdate(asset: AssetInfo): Result<String> {
    // 下载 APK 到缓存目录
    val apkFile = File(cacheDir, "update.apk")
    val response = okHttpClient.get(asset.downloadUrl)

    apkFile.writeBytes(response)

    return Result.success(apkFile.absolutePath)
}
```

### APK 安装

使用 Android FileProvider 安装 APK：

```kotlin
private fun installApk(context: Context, filePath: String) {
    val file = File(filePath)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(intent)
}
```

### 文件配置

#### 1. FileProvider 配置

`app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```

#### 2. AndroidManifest.xml

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

#### 3. 权限声明

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

## 使用说明

### 用户视角

1. **自动检查**（可选）：应用启动时自动检查更新
2. **手动检查**：在设置页面点击"检查更新"
3. **查看更新**：弹出对话框显示新版本信息和更新内容
4. **下载安装**：点击"立即更新"，下载完成后自动调用安装程序

### 开发者视角

#### 配置 GitHub 仓库

在 `UpdateRepository` 中修改仓库信息：

```kotlin
companion object {
    private const val GITHUB_RELEASES_API =
        "https://api.github.com/repos/putyy/res-downloader/releases/latest"
}
```

#### 修改仓库所有者

如果需要发布到自己的仓库：

1. Fork 原项目或创建新项目
2. 修改 API 地址为你的仓库
3. 在 GitHub Releases 中发布新版本
4. 构建并发布 Android APK

#### 发布新版本流程

1. **准备 APK**：
   ```bash
   # 构建 Release APK
   ./gradlew assembleRelease
   ```

2. **创建 Release**：
   - 访问 GitHub 仓库页面
   - 点击 "Releases" → "Draft a new release"
   - 填写版本号（如 v3.1.3）
   - 填写更新说明
   - 上传 APK 文件（命名为 `res-downloader_v3.1.3_android.apk`）

3. **发布**：
   - 点击 "Publish release"
   - 用户下次检查更新时会获取到新版本

## 维护指南

### 后续版本更新

当原项目（putyy/res-downloader）发布新版本时：

1. **监听原项目**：关注原项目 Releases 页面
2. **同步构建**：下载原项目新版本的源代码
3. **编译 APK**：按照编译指南重新构建 Android APK
4. **发布 APK**：将 APK 上传到你的 GitHub Release

### 自定义更新源

如果不想依赖 GitHub，可以搭建自己的更新服务器：

1. **创建 API 接口**：
   ```json
   GET https://your-server.com/api/version

   Response:
   {
     "version": "3.1.2",
     "download_url": "https://your-server.com/apk/res-downloader.apk",
     "changelog": "更新说明..."
   }
   ```

2. **修改代码**：
   ```kotlin
   private const val VERSION_API = "https://your-server.com/api/version"
   ```

### 签名管理

重要：确保更新 APK 使用相同的签名，否则安装会失败。

建议：
- 使用独立的签名密钥
- 妥善保管签名文件和密码
- 在多个地方备份签名文件

### 错误处理

更新功能可能遇到的错误：

| 错误 | 原因 | 处理方式 |
|------|------|----------|
| 网络超时 | 网络不稳定 | 显示重试按钮 |
| 404 Not Found | Release 不存在 | 提示用户稍后重试 |
| 签名不匹配 | APK 签名不一致 | 显示错误信息 |
| 安装被阻止 | 系统禁止安装未知来源 | 引导用户开启权限 |

## 安全考虑

1. **HTTPS 传输**：所有网络请求使用 HTTPS
2. **签名验证**：验证 APK 签名的完整性（可选增强功能）
3. **来源验证**：确保下载链接来自可信源
4. **权限最小化**：只申请必要的权限

## 扩展功能

### 可选增强功能

1. **静默更新**：后台下载，下载完成后提示用户安装
2. **差量更新**：只下载变化的部分（需要服务端支持）
3. **强制更新**：某些版本强制用户更新
4. **更新日志**：显示详细的版本更新历史

### 静默更新实现示例

```kotlin
suspend fun silentUpdate() {
    val versionInfo = checkForUpdates().getOrNull() ?: return

    if (shouldForceUpdate(versionInfo)) {
        // 强制更新，下载后自动安装
        val apkPath = downloadUpdate(versionInfo.assets.first()).getOrNull()
        apkPath?.let { installSilently(it) }
    }
}
```

## 相关文件

- `UpdateRepository.kt` - 更新逻辑实现
- `SettingsViewModel.kt` - UI 层更新管理
- `SettingsScreen.kt` - 设置界面更新 UI
- `file_paths.xml` - FileProvider 配置
- `AndroidManifest.xml` - 权限和服务声明
