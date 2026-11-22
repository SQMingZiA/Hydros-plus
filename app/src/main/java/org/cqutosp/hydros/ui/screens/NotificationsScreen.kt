package org.cqutosp.hydros.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner as ComposeLocalLifecycleOwner
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.notify.HydrosNotifier
import org.cqutosp.hydros.data.notify.NotificationTopic
import org.cqutosp.hydros.data.storage.NotificationSettings
import org.cqutosp.hydros.ui.components.IconTone
import org.cqutosp.hydros.ui.components.InlineState
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.SecondaryAction
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.SwitchRow
import org.cqutosp.hydros.ui.components.TextAction

/**
 * 通知管理：系统权限、总开关、分类开关与测试通知。
 *
 * 页面只保留可操作的开关；解释性文案交给开关状态和系统设置页。
 */
@Composable
fun NotificationsScreen(
    settings: NotificationSettings,
    onMasterChange: (Boolean) -> Unit,
    onTopicChange: (NotificationTopic, Boolean) -> Unit,
    onTestNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = ComposeLocalLifecycleOwner.current
    var systemEnabled by remember { mutableStateOf(HydrosNotifier.systemEnabled(context)) }
    var permissionGranted by remember { mutableStateOf(HydrosNotifier.permissionGranted(context)) }
    var channelStates by remember { mutableStateOf(mapOf<NotificationTopic, Boolean?>()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun refresh() {
        systemEnabled = HydrosNotifier.systemEnabled(context)
        permissionGranted = HydrosNotifier.permissionGranted(context)
        channelStates = NotificationTopic.entries.associateWith { HydrosNotifier.channelEnabled(context, it) }
        refreshKey++
    }

    // 从系统设置返回时重新读取权限与渠道状态。
    DisposableEffect(lifecycleOwner, refreshKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    PageColumn {
        if (!systemEnabled || !permissionGranted) {
            Section("系统权限") {
                if (!systemEnabled) {
                    InlineState(
                        title = "系统通知已关闭",
                        action = "前往设置",
                        onAction = { runCatching { context.openNotificationSettings() } },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InlineState(title = "尚未允许通知权限", icon = R.drawable.ic_notifications)
                        PrimaryAction(
                            label = "允许通知",
                            icon = R.drawable.ic_notifications,
                            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        )
                    }
                }
            }
        }

        Section("通知") {
            SwitchRow(
                title = "接收 Hydros+ 通知",
                icon = R.drawable.ic_notifications,
                checked = settings.enabled,
                onCheckedChange = onMasterChange,
                switchTag = "notification-master",
            )
            if (systemEnabled && permissionGranted) {
                TextAction(
                    label = "系统通知设置",
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = { runCatching { context.openNotificationSettings() } },
                )
            }
            if (settings.enabled) {
                SubtleDivider()
                SecondaryAction(
                    label = "发送测试通知",
                    icon = R.drawable.ic_notifications,
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = systemEnabled && permissionGranted,
                    disabledReason = when {
                        !systemEnabled -> "系统通知已被关闭"
                        !permissionGranted -> "尚未授予通知权限"
                        else -> null
                    },
                    onClick = onTestNotification,
                )
            }
        }

        if (settings.enabled) {
            Section("分类通知") {
                NotificationTopic.entries.forEachIndexed { index, topic ->
                    SwitchRow(
                        title = topic.label,
                        icon = topic.icon(),
                        tone = if (topic == NotificationTopic.WATER) IconTone.PRIMARY else IconTone.NEUTRAL,
                        checked = settings.enabledForTopic(topic),
                        onCheckedChange = { onTopicChange(topic, it) },
                        switchTag = "notification-" + topic.key,
                    )
                    if (channelStates[topic] == false) {
                        TextAction(
                            label = "系统渠道已关闭，前往开启",
                            modifier = Modifier.padding(bottom = 6.dp),
                            onClick = { runCatching { context.openChannelSettings(topic.channelId) } },
                        )
                    }
                    if (index < NotificationTopic.entries.lastIndex) SubtleDivider()
                }
            }
        }
    }
}

private fun NotificationSettings.enabledForTopic(topic: NotificationTopic): Boolean = when (topic) {
    NotificationTopic.WATER -> waterAlerts
    NotificationTopic.BILLING -> billing
    NotificationTopic.CAMPUS -> campus
    NotificationTopic.DEVICE -> device
    NotificationTopic.UPDATE -> updates
}

private fun NotificationTopic.icon(): Int = when (this) {
    NotificationTopic.WATER -> R.drawable.ic_water
    NotificationTopic.BILLING -> R.drawable.ic_wallet
    NotificationTopic.CAMPUS -> R.drawable.ic_message_circle
    NotificationTopic.DEVICE -> R.drawable.ic_water
    NotificationTopic.UPDATE -> R.drawable.ic_update
}

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private fun Context.openChannelSettings(channelId: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
