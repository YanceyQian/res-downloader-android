# 🚀 快速开始指南

## 一、创建自己的 GitHub 仓库（5 分钟）

### 步骤 1：注册/登录 GitHub
如果没有账号，先访问 https://github.com/join 注册

### 步骤 2：创建仓库
1. 访问：https://github.com/new
2. 填写：
   - Repository name: `res-downloader-android`
   - 保持 Public
   - **不要**勾选任何初始化选项
3. 点击 Create repository

### 步骤 3：上传代码
打开命令行（PowerShell 或 CMD）：
```bash
cd E:\res-download\res-downloader-android
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/你的用户名/res-downloader-android.git
git push -u origin main
```

## 二、配置应用内更新（2 分钟）

### 编辑文件
打开文件：`app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt`

修改第 27-28 行：
```kotlin
private const val GITHUB_RELEASES_API = 
    "https://api.github.com/repos/你的用户名/res-downloader-android/releases/latest"
private const val GITHUB_REPO = 
    "https://github.com/你的用户名/res-downloader-android"
```

保存！

## 三、第一次发布（10 分钟）

### 步骤 1：编译 APK
```bash
cd E:\res-download\res-downloader-android
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

### 步骤 2：在 GitHub 发布
1. 打开你的仓库页面
2. 点击 **Releases** → **Draft a new release**
3. 填写：
   - Tag: `v1.0.0`
   - Title: `1.0.0`
   - Description: 随便写点
4. 上传 APK 文件（重命名为 `res-downloader_v1.0.0_android.apk`）
5. 点击 **Publish release**

完成！🎉

## 四、以后同步更新（原项目更新时）

### 简易流程
1. 原项目发布新版本
2. 下载原项目最新代码
3. 更新你的 core/ 目录
4. 更新版本号
5. 编译 APK
6. 发布到你的仓库

详细步骤见：[SYNC_FLOW.md](SYNC_FLOW.md)

## 📖 更多文档

| 文档 | 说明 |
|------|------|
| [GITHUB_RELEASE.md](GITHUB_RELEASE.md) | GitHub 仓库创建和发布详解 |
| [SYNC_FLOW.md](SYNC_FLOW.md) | 原项目更新同步流程 |
| [APP_STORE_RELEASE.md](APP_STORE_RELEASE.md) | 应用商店发布指南 |
| [UPDATE_MECHANISM.md](UPDATE_MECHANISM.md) | 更新机制说明 |
| [ANDROID_BUILD_GUIDE.md](ANDROID_BUILD_GUIDE.md) | Android 编译指南 |

## 💡 推荐的发布策略

### 简单方案（推荐）
只发布在 GitHub Releases，用户通过应用内更新获取。

优点：
- ✅ 无审核
- ✅ 立即发布
- ✅ 支持自动更新
- ✅ 完全免费！

### 用户使用方式
1. 用户从 GitHub Releases 下载第一个版本
2. 以后在应用内点击"检查更新"
3. 自动下载并安装新版本

## 📞 常见问题

### Q: 需要应用签名吗？
A: Debug APK 不需要，但建议配置签名。详见 [GITHUB_RELEASE.md](GITHUB_RELEASE.md)。

### Q: 可以发布到应用商店吗？
A: 可以，但代理类应用审核比较严。详见 [APP_STORE_RELEASE.md](APP_STORE_RELEASE.md)。

### Q: 原项目更新了怎么办？
A: 参考 [SYNC_FLOW.md](SYNC_FLOW.md) 同步流程。

### Q: License 有问题吗？
A: Apache 2.0 许可证非常宽松，只要保留声明就没问题。我们已创建 LICENSE 和 NOTICE 文件。
