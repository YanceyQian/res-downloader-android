package com.resdownloader.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resdownloader.R
import com.resdownloader.data.model.Platform
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.model.ResourceType
import com.resdownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    onDownload: (ResourceInfo) -> Unit
) {
    val proxyState by viewModel.proxyState.collectAsState()
    val resources by viewModel.resources.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedResource by remember { mutableStateOf<ResourceInfo?>(null) }

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
                            contentDescription = "添加链接",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.clearResources() }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "清空",
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
                isRunning = proxyState,
                onToggle = {
                    if (proxyState) onStopProxy() else onStartProxy()
                }
            )

            FilterChips(
                currentFilter = currentFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

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
                            text = if (proxyState) stringResource(R.string.pull_to_refresh) else "请先启动代理",
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
                            onClick = { selectedResource = resource },
                            onDownload = { onDownload(resource) },
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

    selectedResource?.let { resource ->
        ResourceDetailDialog(
            resource = resource,
            platformName = viewModel.getPlatformDisplayName(resource.platform),
            typeName = viewModel.getResourceTypeDisplayName(resource.type),
            onDismiss = { selectedResource = null },
            onDownload = {
                onDownload(resource)
                selectedResource = null
            }
        )
    }
}

@Composable
fun ProxyStatusCard(
    isRunning: Boolean,
    onToggle: () -> Unit
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            if (isRunning) Color(0xFF4CAF50)
                            else Color(0xFF9E9E9E)
                        )
                )
                Column {
                    Text(
                        text = if (isRunning) stringResource(R.string.proxy_running)
                               else stringResource(R.string.proxy_stopped),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isRunning) {
                        Text(
                            text = "127.0.0.1:8899",
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
    }
}

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
        ResourceType.M3U8 to stringResource(R.string.filter_m3u8)
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
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    maxLines = 1,
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
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.download),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
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
        title = { Text("添加链接") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("输入资源链接") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
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
fun ResourceDetailDialog(
    resource: ResourceInfo,
    platformName: String,
    typeName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resource.filename) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("平台", platformName)
                DetailRow("类型", typeName)
                DetailRow("链接", resource.url)
                if (resource.size > 0) {
                    DetailRow("大小", formatFileSize(resource.size))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
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
            maxLines = 2,
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
    ResourceType.OTHER -> Icons.Default.InsertDriveFile
}

fun getTypeColor(type: ResourceType) = when (type) {
    ResourceType.VIDEO -> Color(0xFF2196F3)
    ResourceType.AUDIO -> Color(0xFF4CAF50)
    ResourceType.IMAGE -> Color(0xFFFF9800)
    ResourceType.M3U8 -> Color(0xFFE91E63)
    ResourceType.OTHER -> Color(0xFF9E9E9E)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
