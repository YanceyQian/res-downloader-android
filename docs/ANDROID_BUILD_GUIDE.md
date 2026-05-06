# Android 编译指南

本文档详细说明如何将 res-downloader 项目编译为 Android APK 应用。

## 环境要求

### 1. Go 语言环境

- **Go 版本**: 1.22 或更高版本
- **安装方式**: 访问 https://golang.org/dl/ 下载安装

```bash
# 验证 Go 安装
go version
# 输出应类似: go version go1.22.0 linux/amd64
```

### 2. Go Mobile 工具链

Go Mobile 是 Go 语言官方提供的移动端开发工具，用于将 Go 代码编译为 Android 和 iOS 原生库。

```bash
# 安装 Go Mobile
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

# 初始化 Go Mobile（首次使用需要）
gomobile init
```

注意：初始化过程可能需要几分钟，因为它需要下载 Android NDK 和其他依赖。

### 3. Android Studio

- **最低版本**: Android Studio Hedgehog (2023.1.1) 或更高
- **下载地址**: https://developer.android.com/studio

安装后，确保以下组件已安装：
- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0
- Android NDK (r25b 或更高)
- CMake (用于编译 native 代码)

### 4. Android SDK 配置

在 Android Studio 中配置 SDK：

1. 打开 File → Settings → Appearance & Behavior → System Settings → Android SDK
2. 选择 SDK Platforms 选项卡，勾选 Android 14 (API 34)
3. 选择 SDK Tools 选项卡，安装：
   - Android SDK Platform-Tools
   - Android SDK Build-Tools (34.0.0)
   - NDK (25.2.x 或更高)
   - CMake

### 5. 环境变量配置

确保以下环境变量已正确配置：

```bash
# 添加到 ~/.bashrc 或 ~/.zshrc

# Go 路径
export GOPATH=$HOME/go
export PATH=$PATH:$GOPATH/bin:$GOROOT/bin

# Android SDK 路径
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools

# NDK 路径（如果使用独立 NDK）
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.2.xxxxxxx
```

## 项目结构

```
res-downloader-android/
├── app/                          # Android 应用主模块
│   ├── build.gradle.kts         # 应用构建配置
│   └── src/main/
│       ├── java/com/resdownloader/
│       │   ├── ResDownloaderApp.kt      # Application 类
│       │   ├── data/                     # 数据层
│       │   │   ├── model/                # 数据模型
│       │   │   ├── preferences/          # 偏好设置
│       │   │   └── repository/           # 数据仓库
│       │   ├── di/                       # 依赖注入
│       │   ├── service/                  # Android 服务
│       │   │   ├── ProxyVpnService.kt    # VPN 代理服务
│       │   │   └── DownloadService.kt    # 下载服务
│       │   └── ui/                       # UI 层
│       │       ├── MainActivity.kt       # 主 Activity
│       │       ├── screen/               # 界面
│       │       ├── viewmodel/            # ViewModel
│       │       └── theme/                # 主题
│       ├── res/                          # 资源文件
│       └── AndroidManifest.xml           # 应用清单
├── core/                          # Go 核心库
│   ├── go.mod                     # Go 模块配置
│   └── resdownloader.go           # 核心实现
├── build.gradle.kts               # 根构建配置
└── settings.gradle.kts            # 项目设置
```

## 编译步骤

### 方式一：使用 Android Studio 编译（推荐）

#### 1. 打开项目

1. 启动 Android Studio
2. 选择 File → Open
3. 选择 `res-downloader-android` 文件夹
4. 等待 Gradle 同步完成

#### 2. 配置 Gradle

如果首次打开，Android Studio 会提示配置 Gradle：
- 选择 Use Gradle from: `gradle-wrapper.properties file`
- 选择 JDK 17 或更高版本

#### 3. 同步项目

1. 点击工具栏的 File → Sync Project with Gradle Files
2. 等待同步完成（底部状态栏显示进度）

#### 4. 构建 Debug APK

1. 在左侧项目结构中，右键点击 `app` 模块
2. 选择 Build → Build Bundle(s) / APK(s) → Build APK(s)
3. 等待构建完成
4. 点击 `locate` 查看生成的 APK 文件

#### 5. 构建 Release APK

1. 选择 Build → Generate Signed Bundle / APK
2. 选择 APK → Next
3. 选择或创建签名配置：
   - 如果没有签名文件，点击 "Create new"
   - 填写签名信息：
     ```
     Key store path: /path/to/keystore.jks
     Password: ********
     Confirm: ********
     Key alias: res-downloader
     Key password: ********
     Validity (years): 25
     Certificate:
       First and Last Name: Your Name
       Organization: Your Organization
       Country: CN
     ```
4. 选择 Build Type: release
5. 点击 Finish 生成 APK

### 方式二：使用命令行编译

#### 1. 进入项目目录

```bash
cd res-downloader-android
```

#### 2. 构建 Debug APK

```bash
./gradlew assembleDebug
```

或使用 PowerShell（Windows）：

```powershell
.\gradlew.bat assembleDebug
```

#### 3. 构建 Release APK

```bash
./gradlew assembleRelease
```

#### 4. 安装到设备

```bash
# 通过 USB 连接 Android 设备
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/release/app-release.apk
```

### 编译 Go 核心库为 AAR（可选）

如果需要修改 Go 核心代码并重新编译为 Android 库：

```bash
cd core

# 获取依赖
go mod tidy

# 编译为 Android AAR
gomobile bind -target=android -o ../app/libs/core.aar .
```

## 常见问题与解决方案

### 问题 1: Gradle 同步失败

**错误信息**:
```
Connection refused. Unable to resolve dependency ':app:classpath'
```

**解决方案**:
1. 检查网络连接
2. 打开 File → Settings → Build, Execution, Deployment → Gradle
3. 勾选 "Offline work"（如果网络有问题）
4. 点击 File → Invalidate Caches → Invalidate and Restart

### 问题 2: Android SDK 未找到

**错误信息**:
```
SDK location not found. Define location with sdk.dir in the local.properties file
```

**解决方案**:
1. 创建或编辑 `local.properties` 文件
2. 添加 SDK 路径：
   ```properties
   sdk.dir=/Users/username/Library/Android/sdk
   ```

### 问题 3: Go Mobile 初始化失败

**错误信息**:
```
exec: "gcc": executable file not found in $PATH
```

**解决方案**:
- Linux: `sudo apt install build-essential`
- macOS: `xcode-select --install`
- Windows: 安装 MinGW 或使用 WSL

### 问题 4: NDK 未安装

**错误信息**:
```
Could not find ndk.dir in local.properties
```

**解决方案**:
1. 打开 Android Studio Settings → SDK Manager
2. 选择 SDK Tools 选项卡
3. 勾选 NDK (Side by side)
4. 点击 Apply 下载安装

### 问题 5: Java 版本不兼容

**错误信息**:
```
compileDebugJavaWithJavac': JDK 17 is required
```

**解决方案**:
1. 检查 Java 版本：`java -version`
2. 如果版本过低，下载 JDK 17+：https://adoptium.net/
3. 在 Android Studio 中配置：File → Project Structure → SDK Location → JDK Location

### 问题 6: 构建超时

**错误信息**:
```
Timeout waiting for lock
```

**解决方案**:
```bash
# 删除锁文件后重试
rm -rf ~/.gradle/caches/*/gc.properties
rm -rf .gradle/
```

## 构建配置说明

### 应用版本配置

在 `app/build.gradle.kts` 中修改：

```kotlin
defaultConfig {
    applicationId = "com.resdownloader"
    minSdk = 24
    targetSdk = 34
    versionCode = 1           // 每次发布递增
    versionName = "1.0.0"     // 语义化版本号
}
```

### 签名配置

Release 版本需要签名，配置在 `app/build.gradle.kts`：

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("keystore.jks")
        storePassword = "密码"
        keyAlias = "res-downloader"
        keyPassword = "密码"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### 依赖版本

常用依赖版本（与项目兼容）：

| 依赖 | 版本 |
|------|------|
| Kotlin | 1.9.20 |
| Compose BOM | 2023.10.01 |
| Hilt | 2.48.1 |
| OkHttp | 4.12.0 |
| Moshi | 1.15.0 |

## 性能优化

### 1. 启用 R8 混淆

Release 构建默认启用 R8 优化：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true  // 移除未使用资源
        proguardFiles(...)
    }
}
```

### 2. 优化 APK 大小

- 移除调试信息
- 启用资源压缩
- 只包含目标架构（可选）：
  ```kotlin
  ndk {
      abiFilters += listOf("arm64-v8a")  // 只支持 64 位
  }
  ```

### 3. 构建缓存

使用本地 Gradle 缓存加速构建：

```bash
# 配置缓存目录
export GRADLE_USER_HOME=/path/to/gradle-cache
```

## 验证构建

### 1. 检查 APK 信息

```bash
# Windows
.\build-tools\34.0.0\aapt dump badging app\build\outputs\apk\release\app-release.apk

# macOS/Linux
./build-tools/34.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk
```

验证项：
- `package: name='com.resdownloader'`
- `versionCode='1'`
- `versionName='1.0.0'`
- `sdkVersion:'24' targetSdkVersion:'34'`

### 2. 签名验证

```bash
# 提取签名信息
keytool -printcert -jarfile app-release.apk
```

## 下一步

- 安装 APK 到设备进行测试
- 参考[自动更新机制说明文档](UPDATE_MECHANISM.md)配置版本更新
- 参考[用户使用指南](USER_GUIDE.md)了解应用功能
