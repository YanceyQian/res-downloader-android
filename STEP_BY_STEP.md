
# 操作步骤说明

## 第一步：安装 Git
如果还没有安装，请先下载安装：
https://git-scm.com/download/win
安装完成后，打开新的 PowerShell 窗口

---

## 第二步：执行以下命令（按顺序复制粘贴）

打开 PowerShell（按 Win+X，选择"终端"或"Windows PowerShell"），然后执行：

```powershell
cd E:\res-download\res-downloader-android

# 初始化 Git
git init

# 添加所有文件
git add .

# 提交代码
git commit -m "Initial commit: res-downloader Android version"

# 设置分支
git branch -M main

# 关联远程仓库
git remote add origin https://github.com/YanceyQian/res-downloader-android.git

# 推送到 GitHub
git push -u origin main
```

---

## 第三步：编译 APK（可选，需要 Android Studio）

在 Android Studio 中打开项目，然后构建 APK，或者执行：
```powershell
./gradlew assembleDebug
```

APK 文件位置：
```
E:\res-download\res-downloader-android\app\build\outputs\apk\debug\app-debug.apk
```

---

## 第四步：在 GitHub 发布 APK

1. 打开：https://github.com/YanceyQian/res-downloader-android
2. 点击右侧 "Releases" → "Create a new release"
3. 填写：
   - Tag: `v1.0.0`
   - Title: `1.0.0`
4. 上传 APK（可以重命名为 res-downloader_v1.0.0_android.apk）
5. 点击 "Publish release"

---

完成！现在用户可以下载安装，应用内检查更新功能也已经配置好指向您的仓库了！
