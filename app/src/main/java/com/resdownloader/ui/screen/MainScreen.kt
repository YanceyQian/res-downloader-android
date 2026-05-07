package com.resdownloader.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resdownloader.R
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.service.ProxyVpnService
import com.resdownloader.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    isProxyRunning: Boolean,
    onDownload: (ResourceInfo) -> Unit,
    onCopyLink: (String) -> Unit
) {
    val resources by viewModel.resources.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val captureMode by viewModel.captureMode.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedResource by remember { mutableStateOf<ResourceInfo?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    val selectedResources = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.batch_import),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_list),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showBatchDialog = true }) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = stringResource(R.string.batch_download),
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ProxyStatusCard(
                isRunning = isProxyRunning,
                captureMode = captureMode,
                capturedApps = emptySet(),
                packetCount = ProxyVpnService.getPacketCount(),
                byteCount = ProxyVpnService.getByteCount(),
                onToggle = {
                    if (isProxyRunning) onStopProxy() else onStartProxy()
                },
                onModeChange = { mode ->
                    viewModel.setCaptureMode(mode)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterChips(
                currentFilter = currentFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            if (resources.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.total_resources, resources.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (resources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_resources),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isProxyRunning) stringResource(R.string.pull_to_refresh) else "请先启动代理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(resources, key = { it.id }) { resource ->
                        ResourceCard(
                            resource = resource,
                            platformName = viewModel.getPlatformDisplayName(resource.platform),
                            typeName = viewModel.getResourceTypeDisplayName(resource.type),
                            isSelected = selectedResources.contains(resource.id),
                            onToggleSelect = {
                                if (selectedResources.contains(resource.id)) {
                                    selectedResources.remove(resource.id)
                                } else {
                                    selectedResources.add(resource.id)
                                }
                            },
                            onClick = { selectedResource = resource },
                            onDownload = { onDownload(resource) },
                            onCopyLink = { onCopyLink(resource.url) },
                            onDelete = { viewModel.removeResource(resource.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUrlDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                viewModel.addManualResource(url)
                showAddDialog = false
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_list)) },
            text = { Text(stringResource(R.string.clear_list_tip)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearResources()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBatchDialog) {
        BatchOperationsDialog(
            selectedResources = selectedResources,
            allResources = resources,
            onDismiss = { showBatchDialog = false },
            onBatchDownload = { ids ->
                resources.filter { ids.contains(it.id) }.forEach { onDownload(it) }
                showBatchDialog = false
            },
            onBatchExport = { ids ->
                val urls = resources.filter { ids.contains(it.id) }.joinToString("\n") { it.url }
                onCopyLink(urls)
                showBatchDialog = false
            }
        )
    }

    selectedResource?.let { resource ->
        ResourceDetailDialog(
            resource = resource,
            platformName = viewModel.getPlatformDisplayName(resource.platform),
            typeName = viewModel.getResourceTypeDisplayName(resource.type),
            onDismiss = { selectedResource = null },
            onDownload = {
                onDownload(resource)
                selectedResource = null
            },
            onCopyLink = {
                onCopyLink(resource.url)
            }
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
        },
        placeholder = { Text(stringResource(R.string.search_description)) },
        singleLine = true
    )
}

@Composable
fun ProxyStatusCard(
    isRunning: Boolean,
    captureMode: String = "light",
    capturedApps: Set<String> = emptySet(),
    packetCount: Long = 0,
    byteCount: Long = 0,
    onToggle: () -> Unit,
    onModeChange: (String) -> Unit = {}
) {
    var showModeDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                Color(0xFFE8F5E9)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRunning -> Color(0xFF4CAF50)
                                    else -> Color(0xFF9E9E9E)
                                }
                            )
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    isRunning && captureMode == "light" -> "轻量模式抓取中"
                                    isRunning && captureMode == "full" -> "完整模式抓取中"
                                    else -> "抓取已停止"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.width(8.dp))
                                // 模式切换按钮
                                TextButton(
                                    onClick = { showModeDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "切换",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (isRunning) {
                            val modeText = if (captureMode == "light") "仅支持平台" else "全部流量"
                            Text(
                                text = "VPN $modeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggle() }
                )
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // 模式说明
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val (modeDesc, modeTip) = if (captureMode == "light") {
                            "轻量模式" to "仅抓取指定平台的资源，省电高效"
                        } else {
                            "完整模式" to "抓取全部流量（需安装证书），支持更多平台"
                        }
                        Text(
                            text = "📌 $modeDesc：$modeTip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusItem(
                        icon = Icons.Default.Apps,
                        value = "${capturedApps.size}",
                        label = "应用"
                    )
                    StatusItem(
                        icon = Icons.Default.DataUsage,
                        value = formatCount(packetCount),
                        label = "数据包"
                    )
                    StatusItem(
                        icon = Icons.Default.Storage,
                        value = formatBytes(byteCount),
                        label = "流量"
                    )
                }

                if (capturedApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "已捕获应用：${capturedApps.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "📌 使用提示：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. 打开支持的App（如微信、抖音）\n" +
                                   "2. 浏览视频、音乐等内容\n" +
                                   "3. 资源自动捕获显示在下方\n" +
                                   "4. 点击资源即可下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "💡 点击开关启动抓取，支持：抖音、快手、小红书、视频号、小程序、B站、酷狗、QQ音乐、YouTube等平台",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
    
    // 模式选择对话框
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("选择抓取模式") },
            text = {
                Column {
                    Text(
                        text = "选择抓取模式：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 轻量模式
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModeChange("light")
                                showModeDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = captureMode == "light",
                            onClick = {
                                onModeChange("light")
                                showModeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("轻量模式", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "抓取主流平台资源，无需安装证书，省电",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    
                    Divider()
                    
                    // 完整模式
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModeChange("full")
                                showModeDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = captureMode == "full",
                            onClick = {
                                onModeChange("full")
                                showModeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("完整模式", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "抓取全部流量，需安装CA证书，支持更多平台",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun StatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count < 1000 -> count.toString()
        count < 1000000 -> "${"%.1f".format(count / 1000.0)}K"
        else -> "${"%.1f".format(count / 1000000.0)}M"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChips(
    currentFilter: ResourceType?,
    onFilterSelected: (ResourceType?) -> Unit
) {
    val filters = listOf(
        null to stringResource(R.string.filter_all),
        ResourceType.VIDEO to stringResource(R.string.filter_video),
        ResourceType.AUDIO to stringResource(R.string.filter_audio),
        ResourceType.IMAGE to stringResource(R.string.filter_image),
        ResourceType.M3U8 to stringResource(R.string.filter_m3u8),
        ResourceType.LIVE to stringResource(R.string.filter_live),
        ResourceType.STREAM to stringResource(R.string.filter_stream),
        ResourceType.XLS to stringResource(R.string.filter_xls),
        ResourceType.DOC to stringResource(R.string.filter_doc),
        ResourceType.PDF to stringResource(R.string.filter_pdf),
        ResourceType.FONT to stringResource(R.string.filter_font)
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (type, label) ->
            FilterChip(
                selected = currentFilter == type,
                onClick = { onFilterSelected(type) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceCard(
    resource: ResourceInfo,
    platformName: String,
    typeName: String,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getTypeColor(resource.type)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getTypeIcon(resource.type),
                    contentDescription = null,
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = resource.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text(platformName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text(typeName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (resource.size > 0) {
                    Text(
                        text = formatFileSize(resource.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row {
                IconButton(onClick = onToggleSelect) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.download),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_operation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_link)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                onCopyLink()
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_link)) },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                            onClick = {
                                onClick()
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_row)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                onDelete()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_import)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.import_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.import_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
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
fun BatchOperationsDialog(
    selectedResources: List<String>,
    allResources: List<ResourceInfo>,
    onDismiss: () -> Unit,
    onBatchDownload: (List<String>) -> Unit,
    onBatchExport: (List<String>) -> Unit
) {
    var selectAll by remember { mutableStateOf(false) }
    val localSelected = remember(selectedResources.size) {
        mutableStateListOf(*selectedResources.toTypedArray())
    }

    LaunchedEffect(selectAll) {
        if (selectAll) {
            localSelected.clear()
            localSelected.addAll(allResources.map { it.id })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_download)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectAll || (localSelected.size == allResources.size && allResources.isNotEmpty()),
                        onCheckedChange = { checked ->
                            selectAll = checked
                            if (checked) {
                                localSelected.clear()
                                localSelected.addAll(allResources.map { it.id })
                            } else {
                                localSelected.clear()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.select))
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${localSelected.size}/${allResources.size}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(allResources) { resource ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (localSelected.contains(resource.id)) {
                                        localSelected.remove(resource.id)
                                    } else {
                                        localSelected.add(resource.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = localSelected.contains(resource.id),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        localSelected.add(resource.id)
                                    } else {
                                        localSelected.remove(resource.id)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resource.filename,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(resource.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onBatchExport(localSelected) },
                    enabled = localSelected.isNotEmpty()
                ) {
                    Text(stringResource(R.string.export_url))
                }
                Button(
                    onClick = { onBatchDownload(localSelected) },
                    enabled = localSelected.isNotEmpty()
                ) {
                    Text(stringResource(R.string.download))
                }
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
fun ResourceDetailDialog(
    resource: ResourceInfo,
    platformName: String,
    typeName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resource.filename) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(stringResource(R.string.platform), platformName)
                DetailRow(stringResource(R.string.filter_type), typeName)
                DetailRow(stringResource(R.string.file_path), resource.url)
                if (resource.size > 0) {
                    DetailRow(stringResource(R.string.resource_size), formatFileSize(resource.size))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onCopyLink()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.copy_link))
                }
                Button(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.download))
                }
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
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}

fun getTypeIcon(type: ResourceType) = when (type) {
    ResourceType.VIDEO -> Icons.Default.VideoLibrary
    ResourceType.AUDIO -> Icons.Default.MusicNote
    ResourceType.IMAGE -> Icons.Default.Image
    ResourceType.M3U8 -> Icons.Default.PlayCircle
    ResourceType.LIVE -> Icons.Default.LiveTv
    ResourceType.STREAM -> Icons.Default.Stream
    ResourceType.XLS -> Icons.Default.TableChart
    ResourceType.DOC -> Icons.Default.Description
    ResourceType.PDF -> Icons.Default.PictureAsPdf
    ResourceType.FONT -> Icons.Default.FontDownload
    ResourceType.OTHER -> Icons.Default.InsertDriveFile
}

fun getTypeColor(type: ResourceType) = when (type) {
    ResourceType.VIDEO -> Color(0xFF2196F3)
    ResourceType.AUDIO -> Color(0xFF4CAF50)
    ResourceType.IMAGE -> Color(0xFFFF9800)
    ResourceType.M3U8 -> Color(0xFFE91E63)
    ResourceType.LIVE -> Color(0xFF9C27B0)
    ResourceType.STREAM -> Color(0xFFFF5722)
    ResourceType.XLS -> Color(0xFF8BC34A)
    ResourceType.DOC -> Color(0xFF3F51B5)
    ResourceType.PDF -> Color(0xFFF44336)
    ResourceType.FONT -> Color(0xFF795548)
    ResourceType.OTHER -> Color(0xFF9E9E9E)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024 * 1024 * 1024.0))} GB"
    }
}
