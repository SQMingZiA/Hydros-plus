package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.FeedbackSettings
import org.cqutosp.hydros.ui.components.*

@Composable
fun FeedbackSettingsScreen(settings: FeedbackSettings, onHaptic: (Boolean) -> Unit, onReduceMotion: (Boolean) -> Unit) {
    val haptics = rememberHydrosHaptics()
    var wasEnabled by remember { mutableStateOf(settings.hapticFeedbackEnabled) }
    LaunchedEffect(settings.hapticFeedbackEnabled) {
        if (!wasEnabled && settings.hapticFeedbackEnabled) haptics.medium()
        wasEnabled = settings.hapticFeedbackEnabled
    }
    PageColumn {
        Section("触感反馈") {
            SwitchRow(
                title = "振动反馈",
                icon = R.drawable.ic_vibration,
                checked = settings.hapticFeedbackEnabled,
                onCheckedChange = onHaptic
            )
        }
        Section("动画") {
            SwitchRow(
                title = "减少动画效果",
                supporting = "开启后页面转场、按压回弹与液态玻璃动画都会立刻完成，尽量不播放动效",
                icon = R.drawable.ic_animation,
                checked = settings.reduceMotionEnabled,
                onCheckedChange = onReduceMotion
            )
        }
        Section("说明") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(top = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.material3.Text(
                    "除振动反馈外，以下动效会被关闭：",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf("页面切换与底部标签滑动", "按钮/列表行的按压缩放", "液态玻璃底栏的拖动与液滴", "扫码取景框的扫描光带").forEach {
                    androidx.compose.material3.Text(
                        "· " + it,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
