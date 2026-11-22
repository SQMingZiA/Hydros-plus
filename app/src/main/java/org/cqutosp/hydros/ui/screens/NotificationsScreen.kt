package org.cqutosp.hydros.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.cqutosp.hydros.ui.components.InfoRow
import org.cqutosp.hydros.ui.components.InlineState
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.SecondaryAction
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SubtleDivider
import org.cqutosp.hydros.ui.components.SwitchRow
import org.cqutosp.hydros.ui.components.TextAction
import org.cqutosp.hydros.ui.components.IconTone

/**
 * 通知管理：总开关 + 分类开关 + 系统权限/渠道状态。
 *
 * 应用内开关只是第一层：系统通知权限、单个通知渠道都可能被用户或系统关闭，
 * 页面把三层状态都摊开，并在被关闭时直接给出跳到对应系统设置页的入口。
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

    // 从系统设置返回时重新读取权限与渠道状态，避免页面显示过期结果。
    DisposableEffect(lifecycleOwner, refreshKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    PageColumn {
        Section("通知权限") {
            when {
                !systemEnabled -> InlineState(
                    title = "系统通知已关闭",
                    description = "在系统设置里允许 Hydros+ 发送通知后，下面的开关才会生效。",
                    icon = R.drawable.ic_info,
                    action = "前往系统设置",
                    onAction = { runCatching { context.openNotificationSettings() } },
                )
                !permissionGranted -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InlineState(
                        title = "尚未允许通知权限",
                        description = "Android 13 及以后需要单独授予通知权限，授予后才可以收到用水、账单与校园通知。",
                        icon = R.drawable.ic_info,
                    )
                    PrimaryAction(
                        label = "允许通知",
                        icon = R.drawable.ic_notifications,
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
                else -> {
                    InfoRow("系统通知", "已允许")
                    SubtleDivider()
                    InfoRow("通知渠道", NotificationTopic.entries.size.toString() + " 个")
                    TextAction(
                        label = "打开系统通知设置",
                        modifier = Modifier.padding(top = 4.dp),
                        onClick = { runCatching { context.openNotificationSettings() } },
                    )
                }
            }
        }

        Section("通知") {
            SwitchRow(
                title = "接收 Hydros+ 通知",
                supporting = "总开关。关闭后应用不会再发送任何系统通知，应用内的提示与提醒不受影响。",
                icon = R.drawable.ic_notifications,
                checked = settings.enabled,
                onCheckedChange = onMasterChange,
                switchTag = "notification-master",
            )
        }

        Section("分类通知") {
            NotificationTopic.entries.forEachIndexed { index, topic ->
                val channelOff = channelStates[topic] == false
                SwitchRow(
                    title = topic.label,
                    supporting = topic.description +
                        (if (!settings.enabled) " · 总开关已关闭" else "") +
                        (if (channelOff) " · 系统已关闭此渠道" else ""),
                    icon = topic.icon(),
                    tone = if (topic == NotificationTopic.WATER) IconTone.PRIMARY else IconTone.NEUTRAL,
                    checked = settings.enabledForTopic(topic),
                    enabled = settings.enabled,
                    onCheckedChange = { onTopicChange(topic, it) },
                    switchTag = "notification-" + topic.key,
                )
                if (channelOff) {
                    TextAction(
                        label = "前往开启「" + topic.channelName + "」渠道",
                        modifier = Modifier.padding(bottom = 6.dp),
                        onClick = { runCatching { context.openChannelSettings(topic.channelId) } },
                    )
                }
                if (index < NotificationTopic.entries.lastIndex) SubtleDivider()
            }
        }

        Section("通知渠道") {
            NotificationTopic.entries.forEach { topic ->
                InfoRow(
                    title = topic.channelName,
                    value = when {
                        !systemEnabled -> "系统已关闭"
                        channelStates[topic] == false -> "系统已关闭"
                        settings.enabledForTopic(topic).not() -> "应用内已关闭"
                        else -> "默认重要度"
                    },
                )
                SubtleDivider()
            }
            Text(
                text = "五个渠道在系统设置里可以单独静音；应用内开关与系统渠道开关需要同时开启，通知才会出现。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Section("测试") {
            Text(
                text = "发送一条测试通知，确认权限、渠道与勿扰模式都不会拦截消息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryAction(
                label = "发送测试通知",
                icon = R.drawable.ic_notifications,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = settings.enabled && systemEnabled && permissionGranted,
                disabledReason = when {
                    !settings.enabled -> "请先开启通知总开关"
                    !systemEnabled -> "系统通知已被关闭"
                    !permissionGranted -> "尚未授予通知权限"
                    else -> null
                },
                onClick = onTestNotification,
            )
        }

        Section("说明") {
            Text(
                text = "通知只在本机生成，不包含密码与完整手机号；关闭总开关不会删除已经收到的系统通知，也不会影响应用内的用水限额提示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
