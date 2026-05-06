package com.resdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.resdownloader.service.DownloadService
import com.resdownloader.service.ProxyVpnService
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

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun requestVpnPermission() {
        val intent = ProxyVpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        mainViewModel.toggleProxy()
    }

    private fun stopVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
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
