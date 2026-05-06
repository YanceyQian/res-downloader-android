package com.resdownloader.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.resdownloader.R
import com.resdownloader.data.repository.UpdateState
import com.resdownloader.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val GITHUB_REPO = "https://github.com/putyy/res-downloader"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestCertificateInstall: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val downloadPath by viewModel.downloadPath.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val language by viewModel.language.collectAsState()
    val certificateInstalled by viewModel.certificateInstalled.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val latestVersion by viewModel.latestVersion.collectAsState()

    var showPathDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showCertificateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = uri.path?.replace("/tree/primary:", "/storage/emulated/0/") ?: ""
            if (path.isNotEmpty()) {
                viewModel.setDownloadPath(path)
            }
        }
    }

    // 监听更新状态变化
    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateState.Downloading -> {
                isDownloadingUpdate = true
            }
            is UpdateState.Downloaded -> {
                isDownloadingUpdate = false
                val filePath = (updateState as UpdateState.Downloaded).filePath
                installApk(context, filePath)
            }
            is UpdateState.Error -> {
                isDownloadingUpdate = false
                val message = (updateState as UpdateState.Error).message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            else -> {
                isDownloadingUpdate = false
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.UpdateAvailable -> {
                    showUpdateDialog = true
                }
                is SettingsViewModel.UiEvent.UpdateDownloaded -> {
                    // 这里会通过上面的 LaunchedEffect(updateState) 处理
                }
                is SettingsViewModel.UiEvent.NoUpdate -> {
                    Toast.makeText(context, R.string.no_update, Toast.LENGTH_SHORT).show()
                }
                is SettingsViewModel.UiEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is SettingsViewModel.UiEvent.LanguageChanged -> {
                    Toast.makeText(context, "语言已切换", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsSection(title = "存储")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.download_path),
                    subtitle = downloadPath,
                    onClick = { folderPickerLauncher.launch(null) }
                )
            }

            item {
                SettingsSection(title = "网络")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.SettingsEthernet,
                    title = stringResource(R.string.proxy_port),
                    subtitle = proxyPort.toString(),
                    onClick = { showPortDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.install_certificate),
                    subtitle = if (certificateInstalled) "已安装" else "未安装",
                    onClick = { showCertificateDialog = true }
                )
            }

            item {
                SettingsSection(title = "系统")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = if (language == "zh") "简体中文" else "English",
                    onClick = { showLanguageDialog = true }
                )
            }

            item {
                val powerManager = context.getSystemService<PowerManager>()
                val isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(
                    context.packageName
                ) ?: false

                SettingsItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.battery_optimization),
                    subtitle = if (isIgnoringBatteryOptimizations) "已禁用" else "未禁用",
                    onClick = {
                        if (!isIgnoringBatteryOptimizations) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }

            item {
                SettingsSection(title = "关于")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.check_update),
                    subtitle = "${stringResource(R.string.version)}: ${viewModel.currentVersion}",
                    onClick = { viewModel.checkForUpdates() }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = "爱享素材下载器 v${viewModel.currentVersion}",
                    onClick = { }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showPortDialog) {
        PortDialog(
            currentPort = proxyPort,
            onDismiss = { showPortDialog = false },
            onConfirm = { port ->
                viewModel.setProxyPort(port)
                showPortDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = language,
            onDismiss = { showLanguageDialog = false },
            onConfirm = { lang ->
                viewModel.setLanguage(lang)
                showLanguageDialog = false
            }
        )
    }

    if (showCertificateDialog) {
        CertificateDialog(
            installed = certificateInstalled,
            onDismiss = { showCertificateDialog = false },
            onRequestInstall = {
                viewModel.setCertificateInstalled(true)
                showCertificateDialog = false
            }
        )
    }

    // 更新对话框
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) {
                    showUpdateDialog = false
                    viewModel.resetUpdateState()
                }
            },
            title = {
                if (isDownloadingUpdate) {
                    Text(stringResource(R.string.downloading_update))
                } else {
                    Text(stringResource(R.string.new_version_available))
                }
            },
            text = {
                if (isDownloadingUpdate) {
                    val progress = when (updateState) {
                        is UpdateState.Downloading -> (updateState as UpdateState.Downloading).progress
                        else -> 0
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("正在下载更新，请稍候...")
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                } else if (latestVersion != null) {
                    Column {
                        Text("版本: ${latestVersion?.tagName}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = latestVersion?.body ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "注意：如果没有找到 APK 文件，您需要到 GitHub Releases 手动下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                val androidApk = latestVersion?.assets?.firstOrNull { it.name.endsWith(".apk") }
                if (!isDownloadingUpdate && latestVersion != null) {
                    if (androidApk != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.downloadUpdate(androidApk) { progress ->
                                        // 进度通过 updateState 传递
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.update_now))
                        }
                    } else {
                        Button(
                            onClick = {
                                // 打开 GitHub Releases 页面让用户手动下载
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(latestVersion?.htmlUrl ?: GITHUB_REPO)
                                }
                                context.startActivity(intent)
                                showUpdateDialog = false
                                viewModel.resetUpdateState()
                            }
                        ) {
                            Text("前往 GitHub 下载")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                            viewModel.resetUpdateState()
                        }
                    ) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 2) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun PortDialog(
    currentPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var port by remember { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proxy_port)) },
        text = {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("端口号 (1024-65535)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    port.toIntOrNull()?.let { onConfirm(it) }
                },
                enabled = port.toIntOrNull()?.let { it in 1024..65535 } == true
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = "zh" }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLanguage == "zh",
                        onClick = { selectedLanguage = "zh" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("简体中文")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = "en" }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLanguage == "en",
                        onClick = { selectedLanguage = "en" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("English")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedLanguage) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun CertificateDialog(
    installed: Boolean,
    onDismiss: () -> Unit,
    onRequestInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_certificate)) },
        text = {
            Text(
                if (installed) "CA 证书已安装，可正常拦截 HTTPS 流量"
                else stringResource(R.string.certificate_rationale)
            )
        },
        confirmButton = {
            if (!installed) {
                Button(onClick = onRequestInstall) {
                    Text("我知道了")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        },
        dismissButton = {
            if (!installed) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun installApk(context: android.content.Context, filePath: String) {
    try {
        val file = java.io.File(filePath)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
