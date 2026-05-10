package com.resdownloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resdownloader.data.preferences.LanguageManager
import com.resdownloader.service.ProxyVpnService
import com.resdownloader.ui.screen.DownloadScreen
import com.resdownloader.ui.screen.MainScreen
import com.resdownloader.ui.screen.SettingsScreen
import com.resdownloader.ui.theme.ResDownloaderTheme
import com.resdownloader.ui.viewmodel.DownloadViewModel
import com.resdownloader.ui.viewmodel.MainViewModel
import com.resdownloader.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var languageManager: LanguageManager

    private var pendingStartProxy = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startProxyService()
        } else {
            Toast.makeText(this, "需要 VPN 权限才能使用代理功能", Toast.LENGTH_SHORT).show()
        }
        pendingStartProxy = false
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ResDownloaderApp.applyLang(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResDownloaderApp.applyLang(this)

        // 检查是否启用自动代理
        checkAutoProxy()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val theme by settingsViewModel.theme.collectAsState()
            
            ResDownloaderTheme(theme = theme) {
                MainApp(
                    onStartProxy = { 
                        if (!pendingStartProxy) {
                            requestVpnPermissionAndStart()
                        }
                    },
                    onStopProxy = { stopProxyService() },
                    languageManager = languageManager
                )
            }
        }
    }

    private fun requestVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingStartProxy = true
            vpnPermissionLauncher.launch(intent)
        } else {
            startProxyService()
        }
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        startService(intent)
    }

    /**
     * 检查是否启用自动代理，如果启用则自动开启抓取
     */
    private fun checkAutoProxy() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val autoProxy = prefs.getBoolean("auto_proxy", false)
        
        if (autoProxy && !ProxyVpnService.isRunning) {
            Log.d(TAG, "Auto-proxy enabled, starting VPN service")
            requestVpnPermissionAndStart()
        }
    }
}

// Composable 中使用的证书下载函数
private fun downloadCertificateAndOpenSettings(context: Context) {
    try {
        // 打开 releases 页面下载证书
        val certUrl = "https://github.com/YanceyQian/res-downloader-android/releases/latest"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(certUrl))
        context.startActivity(intent)
        Toast.makeText(context, "请在 releases 页面下载证书压缩包", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开下载页面: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    languageManager: LanguageManager
) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    val mainViewModel: MainViewModel = hiltViewModel()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    
    val isProxyRunning by mainViewModel.proxyState.collectAsState()
    val theme by settingsViewModel.theme.collectAsState()

    val clipboardManager = context.getSystemService(ClipboardManager::class.java)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
                    label = { Text("主页") },
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        navController.navigate("main") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Download, contentDescription = "下载") },
                    label = { Text("下载") },
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        navController.navigate("downloads") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable("main") {
                MainScreen(
                    viewModel = mainViewModel,
                    onStartProxy = onStartProxy,
                    onStopProxy = onStopProxy,
                    isProxyRunning = isProxyRunning,
                    onDownload = { resourceInfo ->
                        downloadViewModel.startDownload(resourceInfo)
                    },
                    onCopyLink = { text ->
                        val clip = ClipData.newPlainText("ResDownloader", text)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            composable("downloads") {
                DownloadScreen(
                    viewModel = downloadViewModel
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    languageManager = languageManager,
                    onRequestCertificateInstall = {
                        // 下载证书并打开安装界面
                        downloadCertificateAndOpenSettings(context)
                    }
                )
            }
        }
    }
}
