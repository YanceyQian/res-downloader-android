package com.resdownloader.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.resdownloader.R
import com.resdownloader.ResDownloaderApp
import com.resdownloader.data.preferences.LanguageManager
import com.resdownloader.data.repository.UpdateState
import com.resdownloader.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val TAG = "SettingsScreen"
private const val GITHUB_REPO = "https://github.com/YanceyQian/res-downloader-android"

// ==================== 主设置界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    languageManager: LanguageManager,
    onRequestCertificateInstall: () -> Unit,
    onLanguageChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 收集所有状态
    val theme by viewModel.theme.collectAsState()
    val host by viewModel.host.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val downloadPath by viewModel.downloadPath.collectAsState()
    val filenameLen by viewModel.filenameLen.collectAsState()
    val filenameTime by viewModel.filenameTime.collectAsState()
    val upstreamProxy by viewModel.upstreamProxy.collectAsState()
    val downloadProxy by viewModel.downloadProxy.collectAsState()
    val autoProxy by viewModel.autoProxy.collectAsState()
    val wxAction by viewModel.wxAction.collectAsState()
    val taskNumber by viewModel.taskNumber.collectAsState()
    val downNumber by viewModel.downNumber.collectAsState()
    val userAgent by viewModel.userAgent.collectAsState()
    val useHeaders by viewModel.useHeaders.collectAsState()
    val insertTail by viewModel.insertTail.collectAsState()
    val rule by viewModel.rule.collectAsState()
    val certificateInstalled by viewModel.certificateInstalled.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val latestVersionInfo by viewModel.latestVersion.collectAsState()
    val currentLanguage by languageManager.currentLanguage.collectAsState(initial = "")

    // 对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLanguageRestartDialog by remember { mutableStateOf(false) }
    var showHostDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showFilenameLenDialog by remember { mutableStateOf(false) }
    var showUpstreamProxyDialog by remember { mutableStateOf(false) }
    var showTaskNumberDialog by remember { mutableStateOf(false) }
    var showDownNumberDialog by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showUseHeadersDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showCertificateDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    // 输入状态
    var hostInput by remember(host) { mutableStateOf(host) }
    var portInput by remember(proxyPort) { mutableStateOf(proxyPort.toString()) }
    var filenameLenInput by remember(filenameLen) { mutableStateOf(if (filenameLen == 0) "" else filenameLen.toString()) }
    var upstreamProxyInput by remember(upstreamProxy) { mutableStateOf(upstreamProxy) }
    var taskNumberInput by remember(taskNumber) { mutableStateOf(taskNumber.toString()) }
    var downNumberInput by remember(downNumber) { mutableStateOf(downNumber.toString()) }
    var userAgentInput by remember(userAgent) { mutableStateOf(userAgent) }
    var useHeadersInput by remember(useHeaders) { mutableStateOf(useHeaders) }
    var ruleInput by remember(rule) { mutableStateOf(rule) }

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

    // 监听更新状态
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

    // 监听 UI 事件
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.UpdateAvailable -> {
                    showUpdateDialog = true
                }
                is SettingsViewModel.UiEvent.NoUpdate -> {
                    Toast.makeText(context, context.getString(R.string.no_update), Toast.LENGTH_SHORT).show()
                }
                is SettingsViewModel.UiEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is SettingsViewModel.UiEvent.LanguageChanged -> {
                    onLanguageChange(event.lang)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    // 主题切换快捷入口
                    IconButton(onClick = {
                        scope.launch { viewModel.setTheme(if (theme == "darkTheme") "lightTheme" else "darkTheme") }
                    }) {
                        Icon(
                            imageVector = if (theme == "darkTheme") Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = stringResource(R.string.theme),
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ==================== 基础设置 ====================
            item {
                SettingsHeader(title = stringResource(R.string.basic_setting))
            }

            // 语言设置
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.language),
                    subtitle = if (currentLanguage == "en") "English" else "简体中文",
                    onClick = { showLanguageDialog = true }
                )
            }

            // 下载目录
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.download_path),
                    subtitle = downloadPath.ifEmpty { "点击选择" },
                    onClick = { folderPickerLauncher.launch(null) }
                )
            }

            // 清晰度
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.HighQuality,
                    title = stringResource(R.string.quality),
                    subtitle = getQualityText(quality),
                    onClick = { showQualityDialog = true },
                    helpText = stringResource(R.string.quality_tip)
                )
            }

            // ==================== 文件命名 ====================
            item {
                SettingsHeader(title = stringResource(R.string.filename_rules))
            }

            // 添加时间
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.filename_time),
                    subtitle = "文件末尾添加时间标识",
                    checked = filenameTime,
                    onCheckedChange = { scope.launch { viewModel.setFilenameTime(it) } },
                    helpText = stringResource(R.string.filename_rules_tip)
                )
            }

            // 文件名长度
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.TextFields,
                    title = stringResource(R.string.filename_len),
                    subtitle = if (filenameLen == 0) "不限制" else "$filenameLen 字符",
                    onClick = { showFilenameLenDialog = true },
                    helpText = "控制文件名的最大长度，0表示不限制"
                )
            }

            // ==================== 网络设置 ====================
            item {
                SettingsHeader(title = stringResource(R.string.network))
            }

            // 代理Host
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Computer,
                    title = stringResource(R.string.host),
                    subtitle = host,
                    onClick = { showHostDialog = true },
                    helpText = stringResource(R.string.restart_tip)
                )
            }

            // 代理端口
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.SettingsEthernet,
                    title = stringResource(R.string.proxy_port),
                    subtitle = proxyPort.toString(),
                    onClick = { showPortDialog = true },
                    helpText = stringResource(R.string.restart_tip)
                )
            }

            // 自动拦截
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.auto_proxy),
                    subtitle = "启动应用时自动开启抓取",
                    checked = autoProxy,
                    onCheckedChange = { scope.launch { viewModel.setAutoProxy(it) } },
                    helpText = stringResource(R.string.auto_proxy_tip)
                )
            }

            // 上游代理
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Hub,
                    title = stringResource(R.string.upstream_proxy),
                    subtitle = upstreamProxy.ifEmpty { "未设置" },
                    onClick = { showUpstreamProxyDialog = true },
                    helpText = stringResource(R.string.upstream_proxy_tip)
                )
            }

            // 下载代理
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.download_proxy),
                    subtitle = "下载时使用上游代理",
                    checked = downloadProxy,
                    onCheckedChange = { scope.launch { viewModel.setDownloadProxy(it) } },
                    helpText = stringResource(R.string.download_proxy_tip)
                )
            }

            // ==================== 高级设置 ====================
            item {
                SettingsHeader(title = stringResource(R.string.advanced_setting))
            }

            // 全量拦截
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = stringResource(R.string.full_intercept),
                    subtitle = "微信视频号全量拦截",
                    checked = wxAction,
                    onCheckedChange = { scope.launch { viewModel.setWxAction(it) } },
                    helpText = stringResource(R.string.full_intercept_tip)
                )
            }

            // 添入尾部
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.ArrowDownward,
                    title = stringResource(R.string.insert_tail),
                    subtitle = "新资源添加到列表末尾",
                    checked = insertTail,
                    onCheckedChange = { scope.launch { viewModel.setInsertTail(it) } },
                    helpText = stringResource(R.string.insert_tail_tip)
                )
            }

            // 任务数
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.List,
                    title = stringResource(R.string.task_number),
                    subtitle = "$taskNumber (连接数)",
                    onClick = { showTaskNumberDialog = true },
                    helpText = stringResource(R.string.connections_tip)
                )
            }

            // 下载数
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.down_number),
                    subtitle = "$downNumber (同时下载任务数)",
                    onClick = { showDownNumberDialog = true },
                    helpText = stringResource(R.string.down_number_tip)
                )
            }

            // User-Agent
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.user_agent),
                    subtitle = if (userAgent.length > 30) userAgent.take(30) + "..." else userAgent,
                    onClick = { showUserAgentDialog = true },
                    helpText = stringResource(R.string.user_agent_tip)
                )
            }

            // 使用Headers
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Label,
                    title = stringResource(R.string.use_headers),
                    subtitle = useHeaders,
                    onClick = { showUseHeadersDialog = true },
                    helpText = stringResource(R.string.use_headers_tip)
                )
            }

            // 域名规则
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.FilterList,
                    title = stringResource(R.string.domain_rule),
                    subtitle = "点击编辑规则",
                    onClick = { showRuleDialog = true },
                    helpText = "设置需要抓取的域名，* 匹配所有"
                )
            }

            // 证书安装
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.install_certificate),
                    subtitle = if (certificateInstalled) "已安装 ✓" else "未安装",
                    subtitleColor = if (certificateInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                    onClick = { showCertificateDialog = true },
                    helpText = "安装CA证书以拦截HTTPS流量"
                )
            }

            // 电池优化
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.BatteryAlert,
                    title = stringResource(R.string.battery_optimization),
                    subtitle = "点击设置",
                    onClick = { openBatteryOptimizationSettings(context) },
                    helpText = "禁用电池优化以保证后台运行"
                )
            }

            // ==================== 关于 ====================
            item {
                SettingsHeader(title = stringResource(R.string.about_section))
            }

            // 检查更新
            item {
                SettingsLoadingItem(
                    icon = Icons.Outlined.SystemUpdate,
                    title = stringResource(R.string.check_update),
                    subtitle = latestVersionInfo?.tagName?.let { "最新: $it" } ?: "点击检查更新",
                    onClick = {
                        if (!isDownloadingUpdate) {
                            scope.launch {
                                viewModel.checkForUpdates()
                            }
                        }
                    },
                    isLoading = isDownloadingUpdate
                )
            }

            // 关于
            item {
                SettingsClickableItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about),
                    subtitle = "版本 ${viewModel.currentVersion}",
                    onClick = { showAboutDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ==================== 对话框 ====================

    // 语言选择
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { lang ->
                scope.launch {
                    viewModel.setLanguage(lang)
                    languageManager.setLanguage(lang)
                    onLanguageChange(lang)
                }
                showLanguageDialog = false
                // 显示重启提示对话框
                showLanguageRestartDialog = true
            }
        )
    }

    // 语言切换重启对话框
    if (showLanguageRestartDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageRestartDialog = false },
            title = { Text("语言切换") },
            text = { Text("语言切换需要重启应用才能生效，是否立即重启？") },
            confirmButton = {
                TextButton(onClick = {
                    showLanguageRestartDialog = false
                    ResDownloaderApp.restart(context)
                }) {
                    Text("立即重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLanguageRestartDialog = false }) {
                    Text("稍后")
                }
            }
        )
    }

    // Host设置
    if (showHostDialog) {
        InputTextDialog(
            title = stringResource(R.string.host),
            value = hostInput,
            onValueChange = { hostInput = it },
            keyboardType = KeyboardType.Text,
            onConfirm = {
                if (hostInput.isNotEmpty()) {
                    scope.launch { viewModel.setHost(hostInput) }
                }
                showHostDialog = false
            },
            onDismiss = { showHostDialog = false }
        )
    }

    // 端口设置
    if (showPortDialog) {
        InputTextDialog(
            title = stringResource(R.string.proxy_port),
            value = portInput,
            onValueChange = { portInput = it },
            keyboardType = KeyboardType.Number,
            placeholder = "1024-65535",
            onConfirm = {
                portInput.toIntOrNull()?.let {
                    if (it in 1024..65535) {
                        scope.launch { viewModel.setProxyPort(it) }
                    } else {
                        Toast.makeText(context, "端口号无效，请输入1024-65535", Toast.LENGTH_SHORT).show()
                    }
                }
                showPortDialog = false
            },
            onDismiss = { showPortDialog = false }
        )
    }

    // 清晰度选择
    if (showQualityDialog) {
        QualitySelectionDialog(
            currentQuality = quality,
            onDismiss = { showQualityDialog = false },
            onSelect = { quality ->
                scope.launch { viewModel.setQuality(quality) }
                showQualityDialog = false
            }
        )
    }

    // 文件名长度
    if (showFilenameLenDialog) {
        InputTextDialog(
            title = stringResource(R.string.filename_len),
            value = filenameLenInput,
            onValueChange = { filenameLenInput = it },
            keyboardType = KeyboardType.Number,
            placeholder = "0表示不限制",
            onConfirm = {
                val len = filenameLenInput.toIntOrNull() ?: 0
                scope.launch { viewModel.setFilenameLen(len) }
                showFilenameLenDialog = false
            },
            onDismiss = { showFilenameLenDialog = false }
        )
    }

    // 上游代理
    if (showUpstreamProxyDialog) {
        InputTextDialog(
            title = stringResource(R.string.upstream_proxy),
            value = upstreamProxyInput,
            onValueChange = { upstreamProxyInput = it },
            keyboardType = KeyboardType.Text,
            placeholder = "http://127.0.0.1:7890",
            onConfirm = {
                scope.launch { viewModel.setUpstreamProxy(upstreamProxyInput) }
                showUpstreamProxyDialog = false
            },
            onDismiss = { showUpstreamProxyDialog = false }
        )
    }

    // 任务数
    if (showTaskNumberDialog) {
        InputTextDialog(
            title = stringResource(R.string.task_number),
            value = taskNumberInput,
            onValueChange = { taskNumberInput = it },
            keyboardType = KeyboardType.Number,
            placeholder = "2-64",
            onConfirm = {
                taskNumberInput.toIntOrNull()?.let {
                    if (it in 2..64) {
                        scope.launch { viewModel.setTaskNumber(it) }
                    }
                }
                showTaskNumberDialog = false
            },
            onDismiss = { showTaskNumberDialog = false }
        )
    }

    // 下载数
    if (showDownNumberDialog) {
        InputTextDialog(
            title = stringResource(R.string.down_number),
            value = downNumberInput,
            onValueChange = { downNumberInput = it },
            keyboardType = KeyboardType.Number,
            placeholder = "1-10",
            onConfirm = {
                downNumberInput.toIntOrNull()?.let {
                    if (it in 1..10) {
                        scope.launch { viewModel.setDownNumber(it) }
                    }
                }
                showDownNumberDialog = false
            },
            onDismiss = { showDownNumberDialog = false }
        )
    }

    // User-Agent
    if (showUserAgentDialog) {
        InputTextDialog(
            title = stringResource(R.string.user_agent),
            value = userAgentInput,
            onValueChange = { userAgentInput = it },
            keyboardType = KeyboardType.Text,
            singleLine = false,
            maxLines = 4,
            onConfirm = {
                scope.launch { viewModel.setUserAgent(userAgentInput) }
                showUserAgentDialog = false
            },
            onDismiss = { showUserAgentDialog = false }
        )
    }

    // Headers
    if (showUseHeadersDialog) {
        HeadersSelectionDialog(
            currentValue = useHeaders,
            onDismiss = { showUseHeadersDialog = false },
            onSelect = { headers ->
                scope.launch { viewModel.setUseHeaders(headers) }
                showUseHeadersDialog = false
            }
        )
    }

    // 域名规则
    if (showRuleDialog) {
        RuleEditorDialog(
            value = ruleInput,
            onValueChange = { ruleInput = it },
            onDismiss = { showRuleDialog = false },
            onSave = {
                scope.launch { viewModel.setRule(ruleInput) }
                showRuleDialog = false
                Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 证书安装
    if (showCertificateDialog) {
        CertificateInstallDialog(
            onDismiss = { showCertificateDialog = false },
            onInstall = {
                showCertificateDialog = false
                onRequestCertificateInstall()
            },
            onMarkInstalled = {
                scope.launch {
                    viewModel.setCertificateInstalled(true)
                    Toast.makeText(context, "已标记证书为已安装", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 更新对话框
    if (showUpdateDialog) {
        UpdateAvailableDialog(
            versionName = latestVersionInfo?.tagName ?: "",
            onDismiss = { showUpdateDialog = false },
            onUpdate = {
                showUpdateDialog = false
                val apkAsset = latestVersionInfo?.assets?.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    scope.launch { viewModel.downloadUpdate(apkAsset) }
                }
            }
        )
    }

    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            version = viewModel.currentVersion,
            onDismiss = { showAboutDialog = false }
        )
    }
}

// ==================== 设置分组标题 ====================

@Composable
private fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

// ==================== 可点击的设置项 ====================

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.outline,
    helpText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                shadowElevation = 0.dp // 移除阴影
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }

        // 帮助文本
        AnimatedVisibility(
            visible = helpText != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            helpText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 54.dp, top = 6.dp)
                )
            }
        }

        Divider(
            modifier = Modifier.padding(start = 54.dp, top = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ==================== 开关类型的设置项 ====================

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                shadowElevation = 0.dp // 移除阴影
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        // 帮助文本
        AnimatedVisibility(
            visible = helpText != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            helpText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 54.dp, top = 6.dp)
                )
            }
        }

        Divider(
            modifier = Modifier.padding(start = 54.dp, top = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ==================== 带加载状态的设置项 ====================

@Composable
private fun SettingsLoadingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                shadowElevation = 0.dp // 移除阴影
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }

        Divider(
            modifier = Modifier.padding(start = 54.dp, top = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ==================== 语言选择对话框 ====================

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                LanguageItem("zh", "简体中文", currentLanguage, onSelect)
                LanguageItem("en", "English", currentLanguage, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageItem(
    value: String,
    label: String,
    current: String,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(value) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onClick(value) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ==================== 文本输入对话框 ====================

@Composable
private fun InputTextDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = singleLine,
                maxLines = maxLines
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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

// ==================== 清晰度选择对话框 ====================

@Composable
private fun QualitySelectionDialog(
    currentQuality: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quality)) },
        text = {
            Column {
                QualityItem(0, "默认(推荐)", currentQuality, onSelect)
                QualityItem(1, "超清", currentQuality, onSelect)
                QualityItem(2, "高画质", currentQuality, onSelect)
                QualityItem(3, "中画质", currentQuality, onSelect)
                QualityItem(4, "低画质", currentQuality, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun QualityItem(
    value: Int,
    label: String,
    current: Int,
    onClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(value) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onClick(value) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ==================== Headers选择对话框 ====================

@Composable
private fun HeadersSelectionDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.use_headers)) },
        text = {
            Column {
                Text(
                    text = "定义下载时可使用的HTTP Header参数",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                HeadersItem("default", "系统默认", currentValue, onSelect)
                HeadersItem("User-Agent,Referer", "User-Agent + Referer", currentValue, onSelect)
                HeadersItem("User-Agent,Referer,Cookie", "完整Headers", currentValue, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun HeadersItem(
    value: String,
    label: String,
    current: String,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(value) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onClick(value) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==================== 域名规则编辑对话框 ====================

@Composable
private fun RuleEditorDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.domain_rule)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "设置需要抓取的域名规则：\n• 使用 * 匹配所有子域名（例如 *.douyin.com）\n• 每行一个规则\n• 使用 ! 开头表示排除（例如 !static.douyin.com）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 10,
                    placeholder = { Text("支持域名匹配规则") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
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

// ==================== 证书安装对话框 ====================

@Composable
private fun CertificateInstallDialog(
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onMarkInstalled: () -> Unit
) {
    val context = LocalContext.current
    val githubCertUrl = "$GITHUB_REPO/releases/latest"
    val baiduCertUrl = "https://pan.baidu.com/s/1_yuYcNTyrgUuKcylCQ_o1w"
    val baiduPassword = "7y52"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_certificate)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "为了拦截 HTTPS 流量，需要安装 CA 证书。\n\n" +
                            "安装步骤：\n" +
                            "1. 下载证书压缩包\n" +
                            "2. 解压后得到 ca.crt 证书文件\n" +
                            "3. 打开「设置」→「安全」→「加密与凭据」\n" +
                            "4. 选择「从存储设备安装证书」\n" +
                            "5. 选择证书文件，命名后确认安装\n\n" +
                            "⚠️ 注意：安装证书后才能抓取 HTTPS 网站的视频和音频资源。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // GitHub 下载
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubCertUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开下载页面", Toast.LENGTH_SHORT).show()
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub 下载",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "点击前往 GitHub releases 页面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 百度网盘下载
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                // 复制提取码到剪贴板
                                val clipboard = context.getSystemService<android.content.ClipboardManager>()
                                val clip = android.content.ClipData.newPlainText("提取码", baiduPassword)
                                clipboard?.setPrimaryClip(clip)
                                // 打开网盘页面
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(baiduCertUrl))
                                context.startActivity(intent)
                                Toast.makeText(context, "已复制提取码: $baiduPassword", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开网盘页面", Toast.LENGTH_SHORT).show()
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "百度网盘下载",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "点击复制提取码并打开网盘",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onMarkInstalled()
                onDismiss()
            }) {
                Text("我已安装证书")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ==================== 更新对话框 ====================

@Composable
private fun UpdateAvailableDialog(
    versionName: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_version_available)) },
        text = {
            Column {
                Text("发现新版本: $versionName")
                Spacer(modifier = Modifier.height(8.dp))
                Text("是否立即更新?", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}

// ==================== 关于对话框 ====================

// 开源归属信息常量
private const val ORIGINAL_AUTHOR = "putyy"
private const val ORIGINAL_REPO = "https://github.com/putyy/res-downloader"
private const val CURRENT_MAINTAINER = "YanceyQian"
private const val LICENSE_NAME = "Apache License 2.0"
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
private fun AboutDialog(
    version: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.about_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_support),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                

                Text(
                    text = stringResource(R.string.about_application),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== 开源归属信息 ====================
                Text(
                    "开源归属",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "本项目基于 Res-Downloader 开发",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 维护者信息卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 原作者
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "原作者: $ORIGINAL_AUTHOR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ORIGINAL_REPO)))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Link, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "原始项目",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        // 当前维护者
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "当前维护者: $CURRENT_MAINTAINER",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO)))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Code, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "查看本项目源码",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 许可证信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "许可证: $LICENSE_NAME",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LICENSE_URL)))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "查看全文",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 功能链接
                Text(
                    "快捷链接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 源码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO)))
                    }) {
                        Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.about_source_code))
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$GITHUB_REPO/releases")))
                    }) {
                        Icon(Icons.Outlined.History, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.about_update_log))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        // 证书下载
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://raw.githubusercontent.com/YanceyQian/res-downloader-android/main/cert/ca.crt")))
                    }) {
                        Icon(Icons.Outlined.Security, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.about_cert_download))
                    }
                    TextButton(onClick = {
                        // 帮助文档 - 本项目文档
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$GITHUB_REPO/blob/main/docs/USER_GUIDE.md")))
                    }) {
                        Icon(Icons.Outlined.MenuBook, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("使用文档")
                    }
                    TextButton(onClick = {
                        // 帮助与支持 - Issues 页面
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$GITHUB_REPO/issues")))
                    }) {
                        Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.about_help))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 免责声明
                Text(
                    "免责声明",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "本软件仅供用户下载自己上传到各大平台的网络资源，请勿用于任何商业或非法用途。使用本软件产生的任何后果由用户自行承担。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

// ==================== 辅助函数 ====================

@Composable
private fun PlatformIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PlatformDrawableIcon(
    iconResId: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun getQualityText(quality: Int): String {
    return when (quality) {
        0 -> "默认(推荐)"
        1 -> "超清"
        2 -> "高画质"
        3 -> "中画质"
        4 -> "低画质"
        else -> "默认"
    }
}

private fun installApk(context: Context, filePath: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("file://$filePath"),
                "application/vnd.android.package-archive"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to install APK", e)
        Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开电池设置", Toast.LENGTH_SHORT).show()
        }
    }
}

// ==================== 证书下载 ====================

private fun downloadCertificate(context: Context) {
    try {
        // 从 releases 页面下载证书文件
        val certUrl = "$GITHUB_REPO/releases/latest"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(certUrl))
        context.startActivity(intent)
        Toast.makeText(context, "请在 releases 页面下载证书压缩包", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
