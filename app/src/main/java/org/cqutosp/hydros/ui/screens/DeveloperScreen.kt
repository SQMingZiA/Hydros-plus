package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.DeviceCategory
import org.cqutosp.hydros.ui.state.DeviceStatus
import org.cqutosp.hydros.ui.state.DeviceUi

/** 开发者工具页：演示模式、构建信息与危险操作；运行日志单独一页。 */
@Composable
fun DeveloperScreen(
    demoMode: Boolean = false,
    onDemoMode: (Boolean) -> Unit = {},
    updateCheckEnabled: Boolean = true,
    onUpdateCheckEnabled: (Boolean) -> Unit = {},
    onOpenLogs: () -> Unit = {},
    buildInfo: List<Pair<String, String>> = emptyList(),
    onCopyBuildInfo: () -> Unit = {},
    onResetOnboarding: () -> Unit = {},
    onClearData: () -> Unit = {},
    devices: List<DeviceUi> = emptyList(),
    onAddDemoDevice: (String, String, DeviceCategory, Boolean) -> Unit = { _, _, _, _ -> },
    onUpdateDemoDevice: (String, String, String, DeviceCategory, Boolean) -> Unit = { _, _, _, _, _ -> },
    onRemoveDemoDevice: (String) -> Unit = {}
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DeviceUi?>(null) }
    PageColumn {
        Section("开发选项") {
            SwitchRow(
                title = "演示数据模式",
                checked = demoMode,
                onCheckedChange = onDemoMode,
                supporting = "模拟在线设备、使用中会话与账单，便于无设备调试",
                icon = R.drawable.ic_code,
                tone = IconTone.PRIMARY
            )
            SubtleDivider()
            SwitchRow(
                title = "自动版本检测",
                checked = updateCheckEnabled,
                onCheckedChange = onUpdateCheckEnabled,
                supporting = if (updateCheckEnabled) {
                    "启动时检查新版本；关闭后不再自动访问更新源，仍可在「软件更新」里手动检查"
                } else {
                    "已暂停自动检测，不会再因连不上更新源而提示失败"
                },
                icon = R.drawable.ic_download,
                tone = IconTone.SECONDARY
            )
            SubtleDivider()
            MineRow(
                title = "重看新手引导",
                supporting = "重新播放首次启动的引导流程",
                icon = R.drawable.ic_history,
                tone = IconTone.SECONDARY,
                onClick = onResetOnboarding
            )
        }
        Section("演示设备") {
            Text(
                text = if (demoMode) {
                    "演示模式下可以自己新增在线设备，新增的设备会立刻出现在首页与设备页。"
                } else {
                    "开启演示数据模式后，可以在这里新增在线设备，无需真实硬件。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            devices.forEach { device ->
                val custom = device.id.startsWith("demo-custom-")
                val online = device.status != DeviceStatus.OFFLINE
                ActionRow(
                    title = device.name,
                    supporting = device.location + " · " + (if (online) "在线" else "离线"),
                    icon = if (device.category == DeviceCategory.DRINK) R.drawable.ic_water else R.drawable.ic_shower,
                    tone = if (online) IconTone.PRIMARY else IconTone.NEUTRAL
                ) {
                    // 只允许修改/删除自己在演示模式里新增的设备，内置演示设备仅作展示。
                    if (custom) {
                        TextAction("编辑", onClick = { editing = device })
                        TextAction("删除", danger = true, onClick = { onRemoveDemoDevice(device.id) })
                    }
                }
                SubtleDivider()
            }
            PrimaryAction(
                label = "新增在线设备",
                icon = R.drawable.ic_add,
                enabled = demoMode,
                // 只有真正关闭时才给禁用原因，否则按钮会一直是灰色且点击只弹提示。
                disabledReason = if (demoMode) null else "请先开启演示数据模式",
                onClick = { adding = true }
            )
        }
        Section("调试") {
            MineRow(
                title = "应用日志",
                supporting = "查看最近的调用与异常记录",
                icon = R.drawable.ic_receipt,
                tone = IconTone.PRIMARY,
                onClick = onOpenLogs
            )
        }
        Section("构建信息") {
            if (buildInfo.isEmpty()) {
                Text(
                    text = "暂无构建信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            buildInfo.forEachIndexed { index, item ->
                if (index > 0) SubtleDivider()
                InfoRow(title = item.first, value = item.second)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                label = "复制构建信息",
                icon = R.drawable.ic_receipt,
                onClick = onCopyBuildInfo
            )
        }
        Section("危险操作") {
            Text(
                text = "会退出登录并清空本地设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            DangerAction(
                label = "清除所有数据",
                icon = R.drawable.ic_delete_sweep,
                onClick = onClearData
            )
        }
    }
    if (adding) {
        DemoDeviceDialog(
            onDismiss = { adding = false },
            onConfirm = { name, location, category, online ->
                adding = false
                onAddDemoDevice(name, location, category, online)
            }
        )
    }
    editing?.let { device ->
        DemoDeviceDialog(
            initial = device,
            onDismiss = { editing = null },
            onConfirm = { name, location, category, online ->
                editing = null
                onUpdateDemoDevice(device.id, name, location, category, online)
            }
        )
    }
}

/** 新增/编辑演示设备弹层：名称、位置、用途与在线状态。 */
@Composable
private fun DemoDeviceDialog(
    initial: DeviceUi? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, DeviceCategory, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "我的演示设备") }
    var location by remember { mutableStateOf(initial?.location ?: "演示楼-1层-演示房") }
    var category by remember { mutableStateOf(initial?.category ?: DeviceCategory.SHOWER) }
    var online by remember {
        mutableStateOf(initial?.let { it.status != DeviceStatus.OFFLINE } ?: true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增演示设备" else "编辑演示设备") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField(value = name, onValue = { name = it }, label = "设备名称")
                FormField(value = location, onValue = { location = it }, label = "安装位置")
                DeviceCategory.entries.forEach { item ->
                    RadioRow(
                        title = item.label,
                        selected = category == item,
                        onClick = { category = item },
                        supporting = "演示用途"
                    )
                }
                SwitchRow(
                    title = "在线状态",
                    supporting = if (online) "在线时可以连接并直接开始用水" else "离线时连接会提示设备离线",
                    checked = online,
                    onCheckedChange = { online = it },
                    icon = R.drawable.ic_bluetooth,
                    tone = IconTone.SECONDARY
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, location, category, online) }) {
                Text(if (initial == null) "添加" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 应用日志页：独立的滚动日志视图，最多显示最近 200 条。 */
@Composable
fun AppLogsScreen(
    logs: List<String> = emptyList(),
    onRefresh: () -> Unit = {},
    onClear: () -> Unit = {}
) {
    val visible = logs.takeLast(200)
    PageColumn {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextAction("刷新", onClick = onRefresh)
            TextAction("清空", danger = true, onClick = onClear)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            if (visible.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 不再嵌套竖向滚动（外层 PageColumn 已在滚动），直接铺开日志文本。
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = visible.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = "只保留最近 200 条，退出应用后清空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
