# 🚀 一键发布指南

## 第一步：安装 Git

### 下载 Git
访问官网下载：https://git-scm.com/download/win

### 安装
- 双击下载的安装程序
- 一路点击 Next 使用默认设置
- 完成后重启 PowerShell 或命令行

---

## 第二步：在 GitHub 创建仓库

1. 登录您的 GitHub 账号
2. 访问：https://github.com/new
3. 填写：
   - Repository name: `res-downloader-android`
   - Description: `基于 putyy/res-downloader 的 Android 版本，支持多平台资源下载`
   - 选择 **Public**
   - **不要勾选** Initialize this repository 下面的任何选项
4. 点击 **Create repository**

---

## 第三步：上传代码到 GitHub

### 打开 PowerShell
按 `Win + X`，选择 **Windows PowerShell** 或 **终端**

### 执行以下命令（逐行复制执行）

```powershell
cd E:\res-download\res-downloader-android

# 初始化 Git
git init

# 添加所有文件
git add .

# 提交代码
git commit -m "Initial commit: res-downloader Android version"

# 设置分支名
git branch -M main
```

### ⚠️ 重要：修改仓库地址

请将下面命令中的 `YOUR-USERNAME` 替换为您的 **GitHub 用户名**，然后执行：

```powershell
# 关联远程仓库（替换 YOUR-USERNAME 为您的用户名）
git remote add origin https://github.com/YOUR-USERNAME/res-downloader-android.git
```

### 推送到 GitHub

```powershell
# 上传代码（第一次可能需要按提示登录 GitHub）
git push -u origin main
```

---

## 第四步：修改应用内更新地址

### 编辑文件
打开这个文件：
```
E:\res-download\res-downloader-android\app\src\main\java\com\resdownloader\data\repository\UpdateRepository.kt
```

### 修改第 27-28 行

找到这两行：
```kotlin
private const val GITHUB_RELEASES_API = "https://api.github.com/repos/putyy/res-downloader/releases/latest"
private const val GITHUB_REPO = "https://github.com/putyy/res-downloader"
```

替换为（注意替换 `YOUR-USERNAME`）：
```kotlin
private const val GITHUB_RELEASES_API = "https://api.github.com/repos/YOUR-USERNAME/res-downloader-android/releases/latest"
private const val GITHUB_REPO = "https://github.com/YOUR-USERNAME/res-downloader-android"
```

### 保存并提交修改

在 PowerShell 中继续执行：
```powershell
# 再次添加修改的文件
git add app/src/main/java/com/resdownloader/data/repository/UpdateRepository.kt

# 提交
git commit -m "Update repository URL to my GitHub"

# 推送
git push
```

---

## 第五步：发布第一个版本 APK

### 编译 APK
在 Android Studio 中或命令行：
```powershell
cd E:\res-download\res-downloader-android
./gradlew assembleDebug
```

APK 位置：
```
E:\res-download\res-downloader-android\app\build\outputs\apk\debug\app-debug.apk
```

### 在 GitHub 上发布

1. 打开您的仓库页面
2. 点击右侧 **Releases** → **Draft a new release**
3. 填写：
   - Tag: `v1.0.0`
   - Title: `1.0.0`
   - Description:
     ```
     ## 1.0.0

     第一个版本发布！

     主要功能：
     - 代理抓包
     - 多平台资源下载
     - 应用内自动更新
     ```
4. 将 APK 文件重命名为 `res-downloader_v1.0.0_android.apk`
5. 上传到 Attach binaries
6. 点击 **Publish release**

---

## ✅ 完成！

现在用户可以：
1. 从您的 GitHub Releases 下载第一个版本
2. 安装后在设置里点击"检查更新"
3. 以后您发布新版本时，用户会自动收到更新提示！

---

## 📖 参考文档

如需更多帮助，请查看：
- [QUICKSTART.md](QUICKSTART.md) - 快速开始指南
- [docs/GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) - GitHub 发布详细说明
- [docs/SYNC_FLOW.md](docs/SYNC_FLOW.md) - 以后同步原项目更新的流程
