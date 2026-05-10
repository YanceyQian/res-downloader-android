package com.resdownloader.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resdownloader.data.model.*
import com.resdownloader.data.repository.AnnouncementType
import com.resdownloader.data.repository.Announcement
import com.resdownloader.data.repository.FallbackOptionUi

/**
 * 公告横幅组件
 * 用于显示重要通知、更新提示等
 */
@Composable
fun AnnouncementBanner(
    announcement: Announcement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    val backgroundColor = when (announcement.type) {
        AnnouncementType.URGENT -> MaterialTheme.colorScheme.errorContainer
        AnnouncementType.WARNING -> Color(0xFFFFF3E0) // 浅橙色
        AnnouncementType.UPDATE -> MaterialTheme.colorScheme.primaryContainer
        AnnouncementType.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (announcement.type) {
        AnnouncementType.URGENT -> MaterialTheme.colorScheme.onErrorContainer
        AnnouncementType.WARNING -> Color(0xFFE65100) // 深橙色
        AnnouncementType.UPDATE -> MaterialTheme.colorScheme.onPrimaryContainer
        AnnouncementType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when (announcement.type) {
        AnnouncementType.URGENT -> Icons.Default.Warning
        AnnouncementType.WARNING -> Icons.Default.Warning
        AnnouncementType.UPDATE -> Icons.Default.Update
        AnnouncementType.INFO -> Icons.Default.Info
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = announcement.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 如果有操作按钮
                    if (announcement.actionUrl != null && announcement.actionText != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(announcement.actionUrl)
                                } catch (e: Exception) {
                                    // 处理无法打开链接的情况
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = contentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(
                                text = announcement.actionText,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 错误提示 Snackbar 组件
 */
@Composable
fun ErrorSnackbar(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }
    ) {
        Text(text = message)
    }
}

/**
 * 更新提醒对话框
 */
@Composable
fun UpdateReminderDialog(
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "发现新版本")
        },
        text = {
            Text(
                text = "微信 API 已更新，当前版本可能无法正常使用视频号功能。\n\n建议更新到最新版本以获得更好的兼容性。"
            )
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("稍后")
            }
        }
    )
}

/**
 * API 失效警告条
 */
@Composable
fun ApiWarningBanner(
    isVisible: Boolean,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "视频号功能可能不可用，微信版本已更新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onCheckUpdate,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("检查更新")
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 降级方案对话框
 * 当所有方案都失败时，显示替代方案列表
 */
@Composable
fun FallbackSolutionDialog(
    message: String,
    options: List<FallbackOptionUi>,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = "视频号功能暂时不可用")
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "临时解决方案：",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { option ->
                    FallbackOptionItem(
                        option = option,
                        onClick = { onOptionSelected(option.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 降级方案选项 UI 模型
 */
data class FallbackOptionUi(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val isPrimary: Boolean = false
)

/**
 * 降级方案选项项
 */
@Composable
private fun FallbackOptionItem(
    option: FallbackOptionUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (option.isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (option.isPrimary)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.isPrimary)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 用户反馈对话框
 */
@Composable
fun FeedbackDialog(
    template: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf(template) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "提交反馈")
        },
        text = {
            Column {
                Text(
                    text = "请描述您遇到的问题（以上为模板，可直接修改）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("描述您遇到的问题...") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(feedbackText) }) {
                Text("复制反馈内容")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 分享链接帮助对话框
 */
@Composable
fun ShareLinkHelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "分享链接方式")
        },
        text = {
            Column {
                Text(
                    text = "如果自动抓取失败，可以尝试以下步骤：",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                NumberedStep("打开微信，找到要保存的视频")
                NumberedStep("点击视频右下角的\"...\"分享按钮")
                NumberedStep("选择\"复制链接\"")
                NumberedStep("打开本应用，粘贴链接到输入框")
                NumberedStep("点击下载")

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "注意：部分视频可能设置为不可下载，此方式也可能失败。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun NumberedStep(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/* =========================================
 * 通用平台降级方案对话框组件
 * ========================================= */

/**
 * 通用平台降级方案对话框
 * 当所有方案都失败时，显示替代方案列表
 */
@Composable
fun PlatformFallbackDialog(
    platform: Platform,
    message: String,
    options: List<FallbackOptionUi>,
    onOptionSelected: (String) -> Unit,
    onFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showCopiedSnackbar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = getPlatformIcon(platform),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "${platform.displayName} 功能暂时不可用")
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "临时解决方案：",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { option ->
                    PlatformFallbackOptionItem(
                        option = option,
                        onClick = { 
                            when (option.id) {
                                "feedback" -> onFeedback()
                                "share_link", "short_url", "proxy_capture", "screen_record" -> onOptionSelected(option.id)
                                "third_party" -> onOptionSelected(option.id)
                                else -> onOptionSelected(option.id)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 获取平台图标
 */
@Composable
private fun getPlatformIcon(platform: Platform) = when (platform) {
    Platform.WECHAT -> Icons.Default.Chat
    Platform.DOUYIN -> Icons.Default.PlayCircle
    Platform.KUAISHOU -> Icons.Default.PlayArrow
    Platform.XIAOHONGSHU -> Icons.Default.Book
    Platform.BILIBILI -> Icons.Default.Tv
    Platform.NETEASE -> Icons.Default.MusicNote
    Platform.QQMUSIC -> Icons.Default.MusicNote
    Platform.KOUGOU -> Icons.Default.MusicNote
    Platform.GENERAL -> Icons.Default.Download
    Platform.UNKNOWN -> Icons.Default.Link
}

/**
 * 平台降级方案选项项
 */
@Composable
private fun PlatformFallbackOptionItem(
    option: FallbackOptionUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (option.isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getFallbackIcon(option.icon),
                contentDescription = null,
                tint = if (option.isPrimary)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (option.isPrimary)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.isPrimary)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 获取降级方法图标
 */
@Composable
private fun getFallbackIcon(iconName: String) = when (iconName) {
    "link" -> Icons.Default.Link
    "wifi_tethering" -> Icons.Default.WifiTethering
    "videocam" -> Icons.Default.Videocam
    "photo_camera" -> Icons.Default.PhotoCamera
    "mic" -> Icons.Default.Mic
    "apps" -> Icons.Default.Apps
    "feedback" -> Icons.Default.Feedback
    "person" -> Icons.Default.Person
    "language" -> Icons.Default.Language
    "search" -> Icons.Default.Search
    else -> Icons.Default.Help
}

/**
 * 平台反馈对话框
 */
@Composable
fun PlatformFeedbackDialog(
    platform: Platform,
    template: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf(template) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = getPlatformIcon(platform),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "提交反馈 - ${platform.displayName}")
        },
        text = {
            Column {
                Text(
                    text = "请描述您遇到的问题（以上为模板，可直接修改）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    placeholder = { Text("描述您遇到的问题...") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(feedbackText) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制反馈内容")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 平台 API 失效警告条
 */
@Composable
fun PlatformApiWarningBanner(
    platform: Platform,
    isVisible: Boolean,
    onShowFallback: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getPlatformIcon(platform),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "${platform.displayName} 功能可能不可用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onShowFallback,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("查看方案")
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 平台公告横幅
 */
@Composable
fun PlatformAnnouncementBanner(
    announcement: PlatformAnnouncement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    val backgroundColor = when (announcement.type) {
        PlatformAnnouncementType.URGENT -> MaterialTheme.colorScheme.errorContainer
        PlatformAnnouncementType.WARNING -> Color(0xFFFFF3E0) // 浅橙色
        PlatformAnnouncementType.UPDATE -> MaterialTheme.colorScheme.primaryContainer
        PlatformAnnouncementType.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (announcement.type) {
        PlatformAnnouncementType.URGENT -> MaterialTheme.colorScheme.onErrorContainer
        PlatformAnnouncementType.WARNING -> Color(0xFFE65100) // 深橙色
        PlatformAnnouncementType.UPDATE -> MaterialTheme.colorScheme.onPrimaryContainer
        PlatformAnnouncementType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when (announcement.type) {
        PlatformAnnouncementType.URGENT -> Icons.Default.Warning
        PlatformAnnouncementType.WARNING -> Icons.Default.Warning
        PlatformAnnouncementType.UPDATE -> Icons.Default.Update
        PlatformAnnouncementType.INFO -> Icons.Default.Info
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = getPlatformIcon(announcement.platform),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = announcement.platform.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = announcement.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 如果有操作按钮
                    if (announcement.actionUrl != null && announcement.actionText != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(announcement.actionUrl)
                                } catch (e: Exception) {
                                    // 处理无法打开链接的情况
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = contentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(
                                text = announcement.actionText,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
