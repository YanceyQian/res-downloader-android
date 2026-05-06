package com.resdownloader.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resdownloader.service.DownloadService
import com.resdownloader.ui.screen.DownloadScreen
import com.resdownloader.ui.screen.MainScreen
import com.resdownloader.ui.screen.SettingsScreen
import com.resdownloader.ui.theme.ResDownloaderTheme
import com.resdownloader.ui.viewmodel.DownloadViewModel
import com.resdownloader.ui.viewmodel.MainViewModel
import com.resdownloader.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by hiltViewModel()
    private val downloadViewModel: DownloadViewModel by hiltViewModel()
    private val settingsViewModel: SettingsViewModel by hiltViewModel()

    private var downloadReceiver: BroadcastReceiver? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVpnService()
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupDownloadReceiver()

        setContent {
            ResDownloaderTheme {
                MainApp(
                    mainViewModel = mainViewModel,
                    downloadViewModel = downloadViewModel,
                    settingsViewModel = settingsViewModel,
                    onStartVpn = { requestVpnPermission() },
                    onStopVpn = { stopVpnService() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            unregisterReceiver(it)
        }
    }

    private fun setupDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.resdownloader.DOWNLOAD_COMPLETE" -> {
                        val taskId = intent.getStringExtra(DownloadService.EXTRA_TASK_ID)
                        val filePath = intent.getStringExtra("file_path")
                        if (taskId != null && filePath != null) {
                            downloadViewModel.updateDownloadState(
                                taskId,
                                DownloadService.DownloadState.Completed(filePath)
                            )
                        }
                    }
                    "com.resdownloader.RESOURCE_INTERCEPTED" -> {
                        val url = intent.getStringExtra("url") ?: return
                        mainViewModel.addManualResource(url)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.resdownloader.DOWNLOAD_COMPLETE")
            addAction("com.resdownloader.RESOURCE_INTERCEPTED")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun requestVpnPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, com.resdownloader.service.ProxyVpnService::class.java).apply {
            action = com.resdownloader.service.ProxyVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        mainViewModel.toggleProxy()
    }

    private fun stopVpnService() {
        val intent = Intent(this, com.resdownloader.service.ProxyVpnService::class.java).apply {
            action = com.resdownloader.service.ProxyVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    mainViewModel: MainViewModel,
    downloadViewModel: DownloadViewModel,
    settingsViewModel: SettingsViewModel,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

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
                    onStartProxy = onStartVpn,
                    onStopProxy = onStopVpn,
                    onDownload = { resourceInfo ->
                        downloadViewModel.startDownload(resourceInfo)
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
                    onRequestCertificateInstall = {
                        settingsViewModel.setCertificateInstalled(false)
                    }
                )
            }
        }
    }
}
