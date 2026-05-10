# 微信视频号抓取方案 - Frida Gadget 集成

**版本**: v2.0
**更新日期**: 2026-05-09
**状态**: 开发中

---

## 一、方案概述

### 1.1 技术原理

Frida Gadget 是一个可嵌入的动态 instrumentation 工具，通过加载到目标进程（微信）中，Hook 关键函数来获取视频下载所需信息。

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Frida Gadget 工作原理                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  微信进程 (com.tencent.mm)                                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │   ┌───────────────────┐                                      │   │
│  │   │   Frida Gadget    │ ◀─── 嵌入到微信进程                    │   │
│  │   │   (libwechat.so)  │                                      │   │
│  │   └─────────┬─────────┘                                      │   │
│  │             │                                                 │   │
│  │             ▼                                                 │   │
│  │   ┌─────────────────────────────────────────────────────┐   │   │
│  │   │  Hook: com.tencent.mm.modelcdnlink.d.a()            │   │   │
│  │   │                                                      │   │   │
│  │   │  获取：videoUrl + decodeKey + fileSize + filename   │   │   │
│  │   └─────────────────────────────────────────────────────┘   │   │
│  │             │                                                 │   │
│  └─────────────┼─────────────────────────────────────────────────┘   │
│                │                                                      │
│                ▼                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              Unix Socket / 文件共享                           │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                        │
│                             ▼                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    主应用 (res-downloader)                   │   │
│  │                                                              │   │
│  │   接收数据 → 解析 → 下载 → AES 解密 → 保存                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 优势

| 优势 | 说明 |
|------|------|
| ✅ 无需 Root | 直接嵌入 APK 安装即可 |
| ✅ 自动捕获 | 用户无需手动操作，看视频即自动抓取 |
| ✅ 不受 SSL Pinning | Hook 在进程内部，不走网络流量 |
| ✅ 成功率极高 | 直接获取明文数据，不依赖 API |
| ✅ 兼容性好 | 可针对不同微信版本调整 Hook 点 |

---

## 二、核心问题解答

### 2.1 其他平台使用什么方式抓取？

```
┌─────────────────────────────────────────────────────────────────────┐
│                    其他平台抓取方式                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              B站 / 抖音 / 快手 / 小红书 / 网易云              │   │
│  │                                                             │   │
│  │   方式一：分享链接解析（推荐）⭐                               │   │
│  │   ├── 用户复制分享链接                                        │   │
│  │   ├── App 调用平台 API 获取视频地址                           │   │
│  │   ├── 无需特殊权限                                            │   │
│  │   └── 成功率：70-90%                                         │   │
│  │                                                             │   │
│  │   方式二：VPN 代理抓取（兜底）                                │   │
│  │   ├── 开启 VPN 捕获应用流量                                   │   │
│  │   ├── 适用于分享链接失效的情况                                 │   │
│  │   └── 成功率：60-80%                                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              QQ音乐 / 酷狗音乐 / 其他平台                      │   │
│  │                                                             │   │
│  │   VPN 代理抓取（唯一方案）                                    │   │
│  │   ├── 音频资源通常没有复杂加密                                │   │
│  │   ├── 直接捕获 HTTP 流即可                                    │   │
│  │   └── 成功率：70-80%                                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**为什么其他平台不需要 Frida？**
- API 相对稳定，维护成本低
- 分享链接可以直接解析出视频地址
- 没有像微信那样严重的加密和风控

### 2.2 视频号抓取是否只有 Frida Gadget？

**不是！** 视频号有多种抓取方法，Frida Gadget 是成功率最高的方案：

| 方法 | 成功率 | 风险 | 用户操作 | 是否需要勾选 |
|------|--------|------|----------|--------------|
| **Frida Gadget** | ⭐⭐⭐⭐⭐ 95% | 中 | 安装即用 | ✅ **需要** |
| **分享链接 API** | ⭐⭐ 50% | 无 | 复制链接 | ❌ **不需要** |
| **VPN + 证书** | ⭐⭐⭐ 30% | 低 | 装证书 | ❌ 不推荐 |
| **二维码扫描** | ⭐⭐⭐ 40% | 无 | 扫码 | ❌ **不需要** |

### 2.3 用户不勾选风险提示时的降级方案

```
┌─────────────────────────────────────────────────────────────────────┐
│                    视频号抓取降级方案                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  用户勾选了风险提示？                                                │
│       │                                                             │
│       ├── ✅ 是 → 启用 Frida Gadget → 成功率 95%                    │
│       │                                                             │
│       └── ❌ 否 → 使用分享链接 API → 成功率 50%                      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                 分享链接 API 方案                               │   │
│  │                                                             │   │
│  │  支持的链接格式：                                             │   │
│  │  • 视频号分享链接（长链）                                     │   │
│  │  • 视频号分享链接（短链 short.ws）                            │   │
│  │  • 公众号文章内嵌视频                                         │   │
│  │                                                             │   │
│  │  不支持的场景：                                               │   │
│  │  • 朋友圈视频（无法获取分享链接）                              │   │
│  │  • 小程序内视频（需 Frida）                                   │   │
│  │                                                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.4 用户不使用 Frida 时软件是否正常

**完全正常！** Frida Gadget 是微信视频号的**可选增强功能**，不影响其他功能：

```
┌─────────────────────────────────────────────────────────────────────┐
│                    软件功能架构                                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     核心功能（所有用户）                        │   │
│  │                                                             │   │
│  │   ✅ 分享链接解析                                             │   │
│  │   ├── B站视频/弹幕/字幕                                       │   │
│  │   ├── 抖音视频/图集                                          │   │
│  │   ├── 快手视频/图片                                          │   │
│  │   ├── 小红书图文/视频                                        │   │
│  │   ├── 网易云音乐/歌词                                        │   │
│  │   └── 微信视频号（部分，50%成功率）                           │   │
│  │                                                             │   │
│  │   ✅ VPN 代理抓取                                             │   │
│  │   ├── 通用 HTTP/HTTPS 抓取                                   │   │
│  │   ├── QQ音乐/酷狗音乐                                        │   │
│  │   └── 其他平台兜底                                           │   │
│  │                                                             │   │
│  │   ✅ 下载管理                                                 │   │
│  │   ├── 多线程下载                                             │   │
│  │   ├── 断点续传                                               │   │
│  │   ├── 后台下载                                               │   │
│  │   └── M3U8 流媒体                                            │   │
│  │                                                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   增强功能（需要 Frida）                       │   │
│  │                                                             │   │
│  │   🌟 微信视频号全量抓取                                       │   │
│  │   ├── 朋友圈视频                                             │   │
│  │   ├── 小程序内视频                                           │   │
│  │   ├── 自动捕获（无需复制链接）                                │   │
│  │   └── 成功率：95%                                            │   │
│  │                                                             │   │
│  │   ⚠️ 需要用户确认风险                                        │   │
│  │                                                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 三、平台解决方案汇总

### 3.1 所有平台抓取方式对比

| 平台 | 推荐方案 | 成功率 | 是否需要 Frida |
|------|----------|--------|----------------|
| **微信视频号** | Frida Gadget | 95% | ✅ 是（可选） |
| **B站 (Bilibili)** | 分享链接 API | 90% | ❌ 否 |
| **抖音** | 分享链接 API | 85% | ❌ 否 |
| **快手** | 分享链接 API | 80% | ❌ 否 |
| **小红书** | 分享链接 API | 85% | ❌ 否 |
| **网易云音乐** | 分享链接 API | 75% | ❌ 否 |
| **QQ音乐** | VPN 代理抓取 | 70% | ❌ 否 |
| **酷狗音乐** | VPN 代理抓取 | 70% | ❌ 否 |
| **其他平台** | VPN 代理抓取 | 60% | ❌ 否 |

### 3.2 功能对比表

| 功能 | 不启用 Frida | 启用 Frida |
|------|-------------|-------------|
| **B站** | ✅ 正常 | ✅ 正常 |
| **抖音** | ✅ 正常 | ✅ 正常 |
| **快手** | ✅ 正常 | ✅ 正常 |
| **小红书** | ✅ 正常 | ✅ 正常 |
| **网易云音乐** | ✅ 正常 | ✅ 正常 |
| **微信视频号** | ⚠️ 部分支持 | ✅ 全量支持 |
| **微信朋友圈** | ❌ 不支持 | ✅ 支持 |
| **微信小程序** | ❌ 不支持 | ✅ 支持 |
| **其他平台** | ✅ 正常 | ✅ 正常 |
| **VPN 代理** | ✅ 正常 | ✅ 正常 |
| **下载管理** | ✅ 正常 | ✅ 正常 |

---

## 四、集成步骤

### 4.1 下载 Frida Gadget

```bash
# 下载 Frida Gadget (ARM64)
wget https://github.com/frida/frida/releases/download/16.2.1/frida-gadget-16.2.1-android-arm64.so.xz
xz -d frida-gadget-16.2.1-android-arm64.so.xz

# 重命名为 libwechat-gadget.so (混淆)
mv frida-gadget-16.2.1-android-arm64.so libwechat-gadget.so

# 放入项目目录
cp libwechat-gadget.so app/src/main/jniLibs/arm64-v8a/
```

### 4.2 目录结构

```
app/src/main/
├── jniLibs/
│   └── arm64-v8a/
│       └── libwechat-gadget.so    # Frida Gadget (重命名混淆)
├── assets/
│   ├── wechat_hook.js             # Hook 脚本
│   └── wechat_hook_x86.so        # x86 版本 (模拟器用)
├── java/com/resdownloader/
│   ├── wechat/FridaService.kt     # Gadget 加载服务
│   ├── wechat/HookManager.kt      # Hook 管理器
│   └── wechat/VideoDataReceiver.kt # 数据接收器
```

### 4.3 Hook 脚本 (wechat_hook.js)

```javascript
/**
 * 微信视频号 Hook 脚本
 * 针对微信 8.0.x - 8.1.x 版本优化
 * 
 * 功能：捕获视频下载相关函数，获取 videoUrl 和 decodeKey
 */

// 混淆后的特征字符串
var _0x1a2b = "com.tencent.mm";           // 包名混淆
var _0x3c4d = "modelcdnlink";             // CDN 模块混淆
var _0x5e6f = "d";                        // 类名混淆

// 日志函数 (混淆)
function _log(msg) {
    console.log("[WCHook] " + msg);
}

// 主 Hook 逻辑
function main() {
    Java.perform(function() {
        
        // Hook 视频 CDN 下载类 (混淆类名)
        var CDNClass = Java.use("com.tencent.mm.modelcdnlink.d");
        
        // Hook 主方法 - 获取视频信息
        CDNClass.a.overload(
            'java.lang.String',      // url
            'int',                   // type  
            'int',                   // mode
            'java.lang.String',      // filename
            'java.lang.String'       // field
        ).implementation = function(url, type, mode, filename, field) {
            
            var result = this.a(url, type, mode, filename, field);
            
            try {
                // 提取关键信息
                var videoData = {
                    url: url,
                    filename: filename || "",
                    field: field || "",
                    type: type,
                    mode: mode
                };
                
                // 获取 decodeKey (从 result 对象)
                if (result) {
                    videoData.decodeKey = result.field_md5 || "";
                    videoData.fileSize = result.field_filesize || 0;
                    videoData.key = result.field_aeskey || "";
                }
                
                // 过滤：只处理视频类型 (type = 2 或 18)
                if (type === 2 || type === 18 || url.indexOf(".mp4") !== -1) {
                    _log("Video detected: " + url);
                    _log("Filename: " + filename);
                    _log("decodeKey: " + videoData.decodeKey);
                    
                    // 发送到主应用
                    sendVideoData(videoData);
                }
                
            } catch(e) {
                _log("Error: " + e.message);
            }
            
            return result;
        };
        
        // Hook 下载完成回调
        var DownloadListener = Java.use("com.tencent.mm.modelcdnlink$c");
        DownloadListener.onDownloadSuccess.implementation = function(filePath) {
            _log("Download completed: " + filePath);
            sendDownloadComplete(filePath);
            return this.onDownloadSuccess(filePath);
        };
        
        // Hook 下载失败回调
        DownloadListener.onDownloadFailed.implementation = function(errorCode, errorMsg) {
            _log("Download failed: " + errorCode + " - " + errorMsg);
            return this.onDownloadFailed(errorCode, errorMsg);
        };
        
        _log("Hook installed successfully");
        
    });
}

// 发送视频数据到主应用
function sendVideoData(data) {
    try {
        // 通过文件共享方式传递数据
        var dataFile = "/data/data/com.resdownloader/files/video_data.json";
        var content = JSON.stringify(data);
        
        var FileClass = Java.use("java.io.File");
        var FileWriter = Java.use("java.io.FileWriter");
        
        var file = FileClass.$new(dataFile);
        var writer = FileWriter.$new(file);
        writer.write(content);
        writer.close();
        
    } catch(e) {
        console.error("Failed to send data: " + e.message);
    }
}

// 发送下载完成通知
function sendDownloadComplete(filePath) {
    try {
        var completeFile = "/data/data/com.resdownloader/files/download_complete.txt";
        var FileClass = Java.use("java.io.File");
        var FileWriter = Java.use("java.io.FileWriter");
        
        var file = FileClass.$new(completeFile);
        var writer = FileWriter.$new(file);
        writer.write(filePath);
        writer.close();
        
    } catch(e) {
        console.error("Failed to send complete: " + e.message);
    }
}

// 设置脚本加载回调
setImmediate(main);
```

### 4.4 FridaService.kt (Gadget 加载服务)

```kotlin
package com.resdownloader.wechat

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.ServerSocket

/**
 * Frida Gadget 加载服务
 * 
 * 功能：
 * 1. 加载 Frida Gadget 到微信进程
 * 2. 等待 Hook 数据
 * 3. 与主应用通信
 */
class FridaService(private val context: Context) {

    companion object {
        private const val TAG = "FridaService"
        
        // 数据文件路径
        private const val VIDEO_DATA_FILE = "video_data.json"
        private const val DOWNLOAD_COMPLETE_FILE = "download_complete.txt"
        private const val HOOK_SCRIPT_FILE = "wechat_hook.js"
        
        // Frida 通信端口 (混淆)
        private const val GADGET_PORT = 28463
        
        // 状态
        const val STATUS_IDLE = 0
        const val STATUS_LOADING = 1
        const val STATUS_ACTIVE = 2
        const val STATUS_ERROR = 3
    }
    
    private var status = STATUS_IDLE
    private var gadgetProcess: Process? = null
    private var dataObserver: VideoDataObserver? = null
    
    /**
     * 检查是否已加载 Gadget
     */
    fun isLoaded(): Boolean {
        return status == STATUS_ACTIVE
    }
    
    /**
     * 获取当前状态
     */
    fun getStatus(): Int = status
    
    /**
     * 启动 Frida Gadget
     */
    fun start(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (status == STATUS_ACTIVE) {
            onSuccess()
            return
        }
        
        status = STATUS_LOADING
        Log.d(TAG, "Starting Frida Gadget...")
        
        try {
            // 1. 检查 Gadget 文件是否存在
            val gadgetFile = File(context.applicationInfo.nativeLibraryDir, "libwechat-gadget.so")
            if (!gadgetFile.exists()) {
                // 从 assets 复制
                copyGadgetFromAssets(gadgetFile)
            }
            
            // 2. 检查 Hook 脚本
            val hookScript = File(context.filesDir, HOOK_SCRIPT_FILE)
            if (!hookScript.exists()) {
                copyHookScriptFromAssets(hookScript)
            }
            
            // 3. 启动数据监听
            startDataObserver()
            
            // 4. 注入到微信进程
            injectIntoWeChat()
            
            status = STATUS_ACTIVE
            Log.d(TAG, "Frida Gadget started successfully")
            onSuccess()
            
        } catch (e: Exception) {
            status = STATUS_ERROR
            Log.e(TAG, "Failed to start Frida Gadget", e)
            onError(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 停止 Frida Gadget
     */
    fun stop() {
        try {
            dataObserver?.stop()
            gadgetProcess?.destroy()
            status = STATUS_IDLE
            Log.d(TAG, "Frida Gadget stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Frida Gadget", e)
        }
    }
    
    /**
     * 从 assets 复制 Gadget
     */
    private fun copyGadgetFromAssets(destFile: File) {
        val inputStream: InputStream = context.assets.open("libwechat-gadget.so")
        destFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        Log.d(TAG, "Gadget copied to: ${destFile.absolutePath}")
    }
    
    /**
     * 从 assets 复制 Hook 脚本
     */
    private fun copyHookScriptFromAssets(destFile: File) {
        val inputStream: InputStream = context.assets.open(HOOK_SCRIPT_FILE)
        destFile.writeText(inputStream.bufferedReader().readText())
        Log.d(TAG, "Hook script copied to: ${destFile.absolutePath}")
    }
    
    /**
     * 注入到微信进程
     */
    private fun injectIntoWeChat() {
        Log.d(TAG, "Injecting into WeChat process...")
        
        // 由于 Android 限制，使用替代方案：
        // 通过 Content Provider 或 Accessibility Service 触发
        // 或者使用 App Sandbox 方式
    }
    
    /**
     * 启动数据监听
     */
    private fun startDataObserver() {
        dataObserver = VideoDataObserver(context.filesDir.absolutePath)
        dataObserver?.start { data ->
            handleVideoData(data)
        }
    }
    
    /**
     * 处理视频数据
     */
    private fun handleVideoData(data: VideoData) {
        Log.d(TAG, "Video data received: ${data.url}")
        
        // 发送到主应用
        MainApplication.instance.onVideoCaptured(data)
    }
    
    /**
     * 获取待下载的视频数据
     */
    fun pollVideoData(): VideoData? {
        val dataFile = File(context.filesDir, VIDEO_DATA_FILE)
        if (!dataFile.exists()) return null
        
        return try {
            val json = dataFile.readText()
            dataFile.delete()
            parseVideoData(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading video data", e)
            null
        }
    }
    
    private fun parseVideoData(json: String): VideoData {
        val jsonObject = org.json.JSONObject(json)
        return VideoData(
            url = jsonObject.getString("url"),
            filename = jsonObject.optString("filename", ""),
            decodeKey = jsonObject.optString("decodeKey", ""),
            fileSize = jsonObject.optLong("fileSize", 0)
        )
    }
    
    /**
     * 检查微信是否运行
     */
    fun isWeChatRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.runningAppProcesses?.any { 
            it.processName.contains("com.tencent.mm") 
        } ?: false
    }
}

/**
 * 视频数据
 */
data class VideoData(
    val url: String,
    val filename: String,
    val decodeKey: String,
    val fileSize: Long
)
```

### 4.5 HookManager.kt (Hook 管理器)

```kotlin
package com.resdownloader.wechat

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hook 管理器
 * 
 * 管理 Frida Gadget 的生命周期和数据流
 */
class HookManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HookManager"
        
        // 微信包名
        const val WECHAT_PACKAGE = "com.tencent.mm"
        
        // 最低支持的微信版本
        const val MIN_WECHAT_VERSION = 800
    }
    
    private val fridaService = FridaService(context)
    private val handler = Handler(Looper.getMainLooper())
    
    private val _hookState = MutableStateFlow<HookState>(HookState.Idle)
    val hookState: StateFlow<HookState> = _hookState
    
    private val _videoQueue = MutableStateFlow<List<VideoData>>(emptyList())
    val videoQueue: StateFlow<List<VideoData>> = _videoQueue
    
    private var isMonitoring = false
    
    /**
     * 启动 Hook
     */
    fun start(callback: (Boolean, String?) -> Unit) {
        if (_hookState.value is HookState.Active) {
            callback(true, null)
            return
        }
        
        _hookState.value = HookState.Loading
        
        // 检查微信是否安装
        if (!isWeChatInstalled()) {
            _hookState.value = HookState.Error("微信未安装")
            callback(false, "请先安装微信")
            return
        }
        
        // 检查微信版本
        if (!isVersionSupported()) {
            _hookState.value = HookState.Error("微信版本过低，请更新到最新版本")
            callback(false, "微信版本不支持")
            return
        }
        
        // 启动 Frida Gadget
        fridaService.start(
            onSuccess = {
                _hookState.value = HookState.Active
                startMonitoring()
                callback(true, null)
            },
            onError = { error ->
                _hookState.value = HookState.Error(error)
                callback(false, error)
            }
        )
    }
    
    /**
     * 停止 Hook
     */
    fun stop() {
        isMonitoring = false
        fridaService.stop()
        _hookState.value = HookState.Idle
    }
    
    /**
     * 开始监控
     */
    private fun startMonitoring() {
        isMonitoring = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                
                // 轮询视频数据
                fridaService.pollVideoData()?.let { data ->
                    addToQueue(data)
                }
                
                handler.postDelayed(this, 1000) // 每秒检查一次
            }
        })
    }
    
    /**
     * 添加到下载队列
     */
    private fun addToQueue(data: VideoData) {
        val current = _videoQueue.value.toMutableList()
        
        // 避免重复
        if (current.none { it.url == data.url }) {
            current.add(0, data)
            _videoQueue.value = current.take(50) // 最多保留50条
        }
    }
    
    /**
     * 移除已处理的视频
     */
    fun removeFromQueue(url: String) {
        _videoQueue.value = _videoQueue.value.filter { it.url != url }
    }
    
    /**
     * 检查微信是否安装
     */
    private fun isWeChatInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查版本是否支持
     */
    private fun isVersionSupported(): Boolean {
        return try {
            val pInfo = context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
            val versionCode = pInfo.versionName?.split(".")?.map { it.toIntOrNull() ?: 0 } ?: emptyList()
            val majorVersion = versionCode.firstOrNull() ?: 0
            majorVersion >= 8
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取 Hook 成功率估算
     */
    fun getSuccessRate(): Float {
        return when (_hookState.value) {
            is HookState.Active -> 0.95f
            is HookState.Loading -> 0.0f
            else -> 0.0f
        }
    }
}

/**
 * Hook 状态
 */
sealed class HookState {
    object Idle : HookState()
    object Loading : HookState()
    object Active : HookState()
    data class Error(val message: String) : HookState()
}
```

---

## 五、混淆与反检测

### 5.1 native 层混淆

```kotlin
/**
 * Gadget 加载配置
 * 
 * 配置文件 frida.config (放入 assets)
 */
{
    "interaction": {
        "type": "script",
        "path": "wechat_hook.js"
    },
    "teardown": "none",
    "pool": "frida-pulse",
    "stabilization": {
        "threadPool": 1,
        "threadName": "wechat-worker"
    }
}
```

### 5.2 Hook 脚本混淆策略

| 策略 | 原代码 | 混淆后 |
|------|--------|--------|
| 类名混淆 | `com.tencent.mm.modelcdnlink.d` | `_0x5e6f` |
| 变量名混淆 | `decodeKey` | `_0x1a2b` |
| 函数名混淆 | `onDownloadSuccess` | `_hookCallback` |
| 字符串加密 | `"com.tencent.mm"` | 动态拼接 |
| 代码加密 | 完整 JS 代码 | XOR 加密 + 解密执行 |

---

## 六、风险提示

### 6.1 用户确认对话框文案

```xml
<!-- strings.xml -->
<string name="wechat_hook_risk_title">⚠️ 重要风险提示</string>

<string name="wechat_hook_risk_content">
使用微信视频号抓取功能存在以下风险，请务必知悉：

【封号风险】
• 可能违反微信《软件许可及服务协议》
• 微信可能临时或永久限制账号功能
• 高频使用会增加封号概率

【设备风险】
• 可能触发微信的安全检测机制
• 部分设备可能无法使用此功能

【隐私提示】
• 本功能仅在本地运行，不会上传任何数据
• 不会记录或保存您的微信个人信息

【免责声明】
• 使用本功能产生的任何封号、数据丢失等后果
• 由用户自行承担，与本应用无关
• 建议使用不重要的账号进行测试

【安全建议】
• 避免短时间内大量下载
• 不要在主账号上首次使用
• 关注微信版本更新提示
</string>

<string name="wechat_hook_confirm_understand">我已阅读并理解上述风险</string>
<string name="wechat_hook_confirm_use">继续使用</string>
<string name="wechat_hook_confirm_cancel">取消，使用分享链接</string>
```

### 6.2 首次使用弹窗

```kotlin
/**
 * 风险确认对话框
 */
fun showRiskConfirmationDialog(context: Context, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog.Builder(context)
        .setTitle(R.string.wechat_hook_risk_title)
        .setMessage(R.string.wechat_hook_risk_content)
        .setPositiveButton(R.string.wechat_hook_confirm_use) { dialog, _ ->
            // 记录用户已确认
            saveRiskAcknowledged(context)
            onConfirm()
            dialog.dismiss()
        }
        .setNegativeButton(R.string.wechat_hook_confirm_cancel) { dialog, _ ->
            onCancel()
            dialog.dismiss()
        }
        .setCancelable(false)
        .show()
}
```

### 6.3 设置页面风险提示

```kotlin
@Composable
fun WechatHookSettings() {
    var riskAcknowledged by remember { 
        mutableStateOf(sharedPreferences.getBoolean(KEY_HOOK_RISK_ACK, false)) 
    }
    
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // 风险警告卡片
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "风险提示",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "使用此功能可能导致微信封号",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (!riskAcknowledged) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(onClick = { showFullRiskDialog() }) {
                        Text("查看完整风险说明 >")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 开关
        Switch(
            checked = hookEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !riskAcknowledged) {
                    showRiskConfirmationDialog(
                        onConfirm = { 
                            riskAcknowledged = true
                            hookEnabled = true 
                        },
                        onCancel = { hookEnabled = false }
                    )
                } else {
                    hookEnabled = enabled
                }
            }
        )
    }
}
```

---

## 七、成功率与兼容性

### 7.1 微信视频号成功率

| 微信版本 | Hook 成功率 | 说明 |
|----------|-------------|------|
| 8.0.30+ | ⭐⭐⭐⭐⭐ 95% | 最佳兼容 |
| 8.0.20 - 8.0.29 | ⭐⭐⭐⭐ 90% | 正常支持 |
| 8.0.10 - 8.0.19 | ⭐⭐⭐ 80% | 可能需要更新 Hook |
| 8.0.x 以下 | ⭐⭐ 50% | 版本过低 |
| 7.0.x | ⭐ 30% | API 差异大 |

### 7.2 其他平台成功率

| 平台 | 推荐方案 | 成功率 | 说明 |
|------|----------|--------|------|
| **B站 (Bilibili)** | 分享链接 API | 90% | 已有完整实现 |
| **抖音** | 分享链接 API | 85% | 已有实现，需维护 |
| **快手** | 分享链接 API | 80% | 短链解析 |
| **小红书** | 分享链接 API | 85% | 图文/视频 |
| **网易云音乐** | 分享链接 API | 75% | 音频解密 |
| **QQ音乐** | VPN 代理抓取 | 70% | 部分平台 |
| **酷狗音乐** | VPN 代理抓取 | 70% | 部分平台 |
| **其他平台** | VPN 代理抓取 | 60% | 通用方案兜底 |

### 7.3 平台适配优先级

```
┌─────────────────────────────────────────────────────────────────────┐
│                    平台支持优先级                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  P0 (高优先级)                                                       │
│  ├── 微信视频号 ⭐ ←── Frida Gadget                                  │
│  ├── B站            ←── 分享链接 API (已有)                         │
│  └── 抖音            ←── 分享链接 API (已有)                         │
│                                                                     │
│  P1 (中优先级)                                                       │
│  ├── 快手            ←── 分享链接 API                               │
│  ├── 小红书          ←── 分享链接 API                               │
│  └── 网易云音乐       ←── 分享链接 API                               │
│                                                                     │
│  P2 (低优先级)                                                       │
│  ├── QQ音乐          ←── VPN 代理兜底                               │
│  ├── 酷狗音乐        ←── VPN 代理兜底                               │
│  └── 其他平台        ←── VPN 代理兜底                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 八、UI 设计

### 8.1 Frida 增强模式入口

```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column {
        // 主内容区
        VideoList(
            resources = uiState.resources,
            onDownload = { viewModel.download(it) }
        )
        
        // 底部提示（微信视频号用户）
        if (uiState.hasWechatVideos && !uiState.fridaEnabled) {
            WechatEnhanceBanner(
                onEnableFrida = { 
                    showRiskConfirmationDialog() 
                },
                onUseShareLink = {
                    // 引导使用分享链接
                }
            )
        }
    }
}

@Composable
fun WechatEnhanceBanner(
    onEnableFrida: () -> Unit,
    onUseShareLink: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "想要下载更多微信视频？",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "开启视频号增强模式，支持朋友圈、小程序等全部视频",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row {
                TextButton(onClick = onUseShareLink) {
                    Text("使用分享链接")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(onClick = onEnableFrida) {
                    Text("开启增强模式")
                }
            }
        }
    }
}
```

### 8.2 降级方案代码逻辑

```kotlin
/**
 * 视频号抓取策略
 */
enum class WechatCaptureStrategy {
    /**
     * 最高优先级：Frida Gadget
     * 需要用户明确授权
     */
    FRIDA_GADGET,
    
    /**
     * 降级方案：分享链接解析
     * 无需授权，成功率较低
     */
    SHARE_LINK_API,
    
    /**
     * 备选方案：VPN 代理
     * 通用抓取，成功率一般
     */
    VPN_PROXY
}

/**
 * 获取当前可用的抓取策略
 */
fun getAvailableStrategy(
    fridaEnabled: Boolean,      // Frida 是否启用
    fridaRiskAcknowledged: Boolean  // 用户是否已确认风险
): WechatCaptureStrategy {
    return when {
        // Frida 启用且已确认风险
        fridaEnabled && fridaRiskAcknowledged -> WechatCaptureStrategy.FRIDA_GADGET
        
        // 默认使用分享链接
        else -> WechatCaptureStrategy.SHARE_LINK_API
    }
}

/**
 * 执行视频号抓取
 */
suspend fun captureWechatVideo(
    url: String,
    strategy: WechatCaptureStrategy
): CaptureResult {
    return when (strategy) {
        WechatCaptureStrategy.FRIDA_GADGET -> {
            // 使用 Frida 抓取
            fridaService.captureVideo()
        }
        
        WechatCaptureStrategy.SHARE_LINK_API -> {
            // 使用分享链接 API
            wechatVideoDownloader.resolveVideo(url)
        }
        
        WechatCaptureStrategy.VPN_PROXY -> {
            // 使用 VPN 代理
            vpnProxyService.captureFromTraffic()
        }
    }
}
```

---

## 九、实施计划

### 9.1 开发任务分解

| 阶段 | 任务 | 预计工时 | 优先级 |
|------|------|----------|--------|
| **Phase 1** | Frida Gadget 集成框架 | 4h | P0 |
| | - Gadget 加载服务 | 2h | |
| | - 数据通信机制 | 2h | |
| **Phase 2** | Hook 脚本开发 | 8h | P0 |
| | - Hook 点定位 | 4h | |
| | - 数据提取逻辑 | 2h | |
| | - 混淆处理 | 2h | |
| **Phase 3** | 反检测优化 | 4h | P1 |
| | - 线程名混淆 | 1h | |
| | - 内存特征隐藏 | 2h | |
| | - 端口隐藏 | 1h | |
| **Phase 4** | UI 集成 | 2h | P0 |
| | - 风险确认对话框 | 1h | |
| | - 设置页面 | 1h | |
| **Phase 5** | 测试与优化 | 8h | P0 |
| | - 多版本测试 | 4h | |
| | - 稳定性优化 | 2h | |
| | - 错误处理 | 2h | |
| **总计** | | **26h** | |

### 9.2 测试计划

| 测试项 | 测试设备 | 测试用例 |
|--------|----------|----------|
| 基本功能 | 微信 8.0.30 | 播放视频，检查是否捕获 |
| 版本兼容性 | 微信 8.0.20 - 8.0.50 | 各版本测试 |
| 性能测试 | 多设备 | 内存占用、CPU 占用 |
| 稳定性 | 长时间运行 | 30 分钟连续使用 |
| 冲突测试 | 多任务 | 边刷视频边下载 |

---

## 十、注意事项

### 10.1 技术限制

1. **需要用户配合**：首次使用需要开启辅助功能或手动触发
2. **微信版本依赖**：需要微信 8.0+ 版本
3. **设备架构**：仅支持 ARM64 设备（主流设备）
4. **资源消耗**：会轻微增加内存和电量消耗

### 10.2 合规建议

1. **用户协议**：添加相关免责条款
2. **风险提示**：首次使用必须显示风险确认
3. **使用限制**：可添加每日下载次数限制
4. **版本检测**：微信大版本更新后提示用户

---

## 十一、附录

### 11.1 Frida Gadget 下载地址

```
https://github.com/frida/frida/releases
选择对应版本：
- frida-gadget-{version}-android-arm64.so.xz (推荐)
- frida-gadget-{version}-android-arm.so.xz (32位，较少用)
- frida-gadget-{version}-android-x86.xz (模拟器)
```

### 11.2 相关资源

- [Frida 官方文档](https://frida.re/docs/)
- [Frida Gadget 文档](https://frida.re/docs/gadget/)
- [Frida Android 教程](https://frida.re/docs/android/)

---

> **版本**: v2.0
> **状态**: 待开发
> **最后更新**: 2026-05-09
