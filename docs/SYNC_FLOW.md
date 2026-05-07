# 🔄 原项目更新同步流程

本文档详细说明当原项目 putyy/res-downloader 更新后，如何同步更新并发布 Android 版本。

## 📅 定期检查更新

建议的检查频率：每周一次，或关注原项目 Releases。

### 检查原项目更新

1. 访问：https://github.com/putyy/res-downloader/releases
2. 查看是否有新版本发布
3. 阅读更新说明，了解改动内容

## 🔄 完整同步流程

### 第一步：准备环境

确保项目已正确设置：
- 已建立 GitHub 仓库
- 已配置签名
- UpdateRepository.kt 中的仓库地址已正确配置

### 第二步：下载原项目最新代码

#### 方法一：使用 Git（推荐）

```bash
# 添加原项目为远程源（首次需要）
git remote add upstream https://github.com/putyy/res-downloader.git

# 以后每次同步时
git fetch upstream
git checkout main
git merge upstream/master

# 如果有冲突，手动解决
# 然后提交更改
git add .
git commit -m "Sync with putyy/res-downloader v3.x.x"
```

#### 方法二：直接下载

1. 访问：https://github.com/putyy/res-downloader/releases/latest
2. 下载 Source code（zip 或 tar.gz）
3. 解压到临时目录
4. 对比并更新核心文件

### 第三步：识别需要同步的文件

原项目关键文件：

| 文件/目录 | 说明 | 是否需要同步 |
|----------|------|-------------|
| `core/` | Go 核心代码 | 需要仔细同步 |
| `go.mod` | Go 依赖 | 需要检查更新 |
| `README.md` | 文档 | 参考即可 |
| 其他 UI 文件 | 原项目桌面 UI | 不需要（Android 有自己的 UI） |

**重点同步**：Go 核心功能部分！

### 第四步：同步 Go 核心代码

原项目的 Go 代码主要在 `core/` 目录，需要：

1. 对比原项目和我们的 `core/` 目录差异
2. 合并更新的函数、逻辑
3. 测试是否有破坏现有功能
4. 运行 Go 模块依赖更新：
   ```bash
   cd core
   go mod tidy
   ```

### 第五步：更新 Android 版本号

修改 `app/build.gradle.kts`：

```kotlin
defaultConfig {
    versionCode = 2      // 每次发布 +1（重要！）
    versionName = "1.1.0"  // 对应原项目或自定义版本
}
```

建议版本号策略：
- 如果主要同步原项目：`原版本号.小版本`（如 `3.1.0.1`）
- 如果自己的改动多：`1.1.0`（从 1.0.0 开始）

### 第六步：测试应用

1. 运行单元测试：
   ```bash
   ./gradlew test
   ```

2. 在真机/模拟器上测试：
   - 代理启动是否正常
   - 资源抓取是否正常
   - 下载功能是否正常
   - 更新功能是否正常

### 第七步：编译新的 APK

```bash
# Debug 版本（快速测试）
./gradlew assembleDebug

# Release 版本（发布用）
./gradlew assembleRelease
```

### 第八步：提交到自己的仓库

```bash
git add .
git commit -m "Update to sync with putyy/res-downloader v3.x.x

- Sync core functionality
- Update version to 1.1.0
- Fixes and improvements"
git push origin main
```

### 第九步：发布到 GitHub Releases

参考 [GITHUB_RELEASE.md](GITHUB_RELEASE.md)：

1. 在 GitHub 创建新 Release
2. 填写版本号（Tag：如 v1.1.0）
3. 撰写更新说明
4. 上传新 APK
5. 发布！

### 第十步：用户自动更新

发布后，用户可以：
1. 打开应用设置
2. 点击"检查更新"
3. 自动下载并安装新版本

## 📋 同步检查清单

每次同步时完成以下检查：

- [ ] 已检查原项目 Releases 和更新说明
- [ ] 已下载/拉取最新代码
- [ ] 已同步 Go 核心逻辑
- [ ] 已更新 go.mod 依赖
- [ ] 已更新版本号（versionCode 和 versionName）
- [ ] 已在设备上测试功能正常
- [ ] 已编译 APK
- [ ] 已签名 APK
- [ ] 已提交到 Git 仓库
- [ ] 已推送到 GitHub
- [ ] 已发布到 GitHub Releases
- [ ] 已测试应用内更新功能

## 🚨 常见问题

### 问题1：合并代码时冲突

**解决方法**：
- 仔细阅读冲突标记（<<<<<<<, =======, >>>>>>>）
- 保留双方的重要功能
- 测试冲突解决后的代码

### 问题2：原项目有大重构

**解决方法**：
- 可以选择暂时不更新，等稳定后再同步
- 或者完全重写相关部分，保留 Android 适配层

### 问题3：更新后功能不工作

**解决方法**：
- 检查 Go 代码导出的函数是否变化
- 检查 Android 和 Go 之间的 API 调用
- 回退到上一个版本，逐步调试

### 问题4：找不到 UpdateRepository.kt

**文件位置**：
```
E:\res-download\res-downloader-android\app\src\main\java\com\resdownloader\data\repository\UpdateRepository.kt
```

## 📝 示例更新日志

每次同步后，在 GitHub Release 填写类似更新说明：

```markdown
## 1.1.0 (2026-05-06)

### 🔄 同步更新
- 同步原项目 putyy/res-downloader v3.1.3
- 核心功能更新和优化

### ✨ 改进
- 优化资源识别准确率
- 提高下载稳定性

### 📦 下载
Android APK: res-downloader_v1.1.0_android.apk
```

## 💡 最佳实践

1. **保持小步快跑**：原项目小版本更新时也及时同步
2. **记录每次同步**：在 Git 提交信息中说明同步了什么
3. **备份密钥**：将签名密钥存放在安全的地方
4. **准备回退方案**：发布前保留上一个版本的 APK
5. **渐进式更新**：先自己测试，再发布给用户

## 📞 更多帮助

- 原项目地址：https://github.com/putyy/res-downloader
- 原项目文档：https://res.putyy.com/
- 本项目文档：查看 docs/ 目录
