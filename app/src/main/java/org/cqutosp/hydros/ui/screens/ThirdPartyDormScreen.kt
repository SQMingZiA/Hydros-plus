package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.cqutosp.hydros.R
import org.cqutosp.hydros.thirdparty.BoundDorm
import org.cqutosp.hydros.thirdparty.normalizeDormMac
import org.cqutosp.hydros.thirdparty.normalizeDormRoom
import org.cqutosp.hydros.ui.components.*

/** 附近扫描到的一台可绑定设备。 */
data class DormNearbyDevice(
    val id: String,
    val name: String,
    val mac: String,
    val location: String,
)

private val BOUND_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@Composable
fun DormBindingScreen(
    bound: BoundDorm?,
    editing: Boolean,
    error: String?,
    onSave: (room: String, mac: String, label: String) -> Unit,
    onClear: () -> Unit,
    nearbyDevices: List<DormNearbyDevice> = emptyList(),
    nearbySearching: Boolean = false,
    onScanNearby: () -> Unit = {},
) {
    var roomInput by remember(bound) { mutableStateOf(bound?.room.orEmpty()) }
    var macInput by remember(bound) { mutableStateOf(bound?.deviceMac.orEmpty()) }
    var labelInput by remember(bound) { mutableStateOf(bound?.label.orEmpty()) }
    var submitted by remember(bound) { mutableStateOf(false) }
    var selectedDeviceId by remember(bound) { mutableStateOf<String?>(null) }

    val showForm = bound == null || editing
    val selectedDevice = nearbyDevices.firstOrNull { it.id == selectedDeviceId }
    val roomError = if (submitted && roomInput.isBlank()) "未能识别寝室号，请重新选择附近设备" else null

    PageColumn {
        if (bound != null && !showForm) {
            Section("已绑定寝室") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconTile(R.drawable.ic_shower, tone = IconTone.PRIMARY, size = 44.dp, iconSize = 22.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            bound.room,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            bound.deviceMac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SubtleDivider()
                if (bound.label.isNotBlank()) InfoRow("备注", bound.label)
                InfoRow("绑定时间", formatBoundAt(bound.boundAt))
                Column(Modifier.padding(vertical = 12.dp)) {
                    DangerAction(
                        label = "解除绑定",
                        icon = R.drawable.ic_shield,
                        onClick = onClear
                    )
                }
            }
        } else {
            // 默认路径：直接挑附近扫到的设备，点一下就把寝室号、MAC、备注填好。
            Section("附近设备") {
                when {
                    nearbySearching -> InlineState(title = "正在查找附近设备…", loading = true)
                    nearbyDevices.isEmpty() -> InlineState(
                        title = "未发现附近设备",
                        description = "绑定寝室只支持蓝牙设备，请靠近热水表后重新扫描。",
                        icon = R.drawable.ic_bluetooth,
                        action = "重新扫描",
                        onAction = onScanNearby
                    )
                    else -> nearbyDevices.forEachIndexed { index, device ->
                        NearbyDeviceRow(
                            device = device,
                            selected = device.id == selectedDeviceId,
                            onClick = {
                                selectedDeviceId = device.id
                                macInput = device.mac
                                labelInput = device.name
                                roomFromLocation(device.location)?.let { roomInput = it }
                                submitted = false
                            }
                        )
                        if (index < nearbyDevices.lastIndex) SubtleDivider()
                    }
                }
                if (!nearbySearching && nearbyDevices.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextAction(label = "重新扫描附近设备", onClick = onScanNearby)
                    }
                }
            }

            Section(if (bound == null) "绑定信息" else "修改绑定信息") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconTile(R.drawable.ic_shower, tone = IconTone.PRIMARY, size = 44.dp, iconSize = 22.dp)
                    Text(
                        if (bound == null) "绑定寝室热水设备" else "更新寝室设备",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (selectedDevice == null) {
                    Text(
                        text = "绑定寝室只支持蓝牙设备：请在上方选择扫到的热水表，不支持手动输入 MAC。",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    InfoRow("设备 MAC", selectedDevice.mac)
                    SubtleDivider()
                    InfoRow("寝室号", roomInput.ifBlank { "—" })
                    roomError?.let {
                        Text(
                            text = it,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(Modifier.padding(vertical = 12.dp)) {
                    PrimaryAction(
                        label = "保存绑定",
                        enabled = selectedDevice != null && roomInput.isNotBlank(),
                        disabledReason = "请先在上方选择一台附近的热水表",
                        onClick = {
                            submitted = true
                            val device = selectedDevice ?: return@PrimaryAction
                            val targetMac = normalizeDormMac(device.mac) ?: return@PrimaryAction
                            if (roomInput.isNotBlank()) {
                                onSave(normalizeDormRoom(roomInput), targetMac, labelInput.ifBlank { device.name })
                            }
                        }
                    )
                }
            }
        }

        error?.let { InlineState(title = "绑定未完成", description = it, icon = R.drawable.ic_info) }
    }
}

/** 一行附近设备：圆形蓝牙图标 + 名称 / 位置 / MAC，选中时右侧出现对勾。 */
@Composable
private fun NearbyDeviceRow(
    device: DormNearbyDevice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .hydrosPressable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(
            R.drawable.ic_bluetooth,
            tone = if (selected) IconTone.PRIMARY else IconTone.NEUTRAL,
            size = 38.dp,
            iconSize = 19.dp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (device.location.isNotBlank()) {
                Text(
                    device.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                device.mac,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        if (selected) {
            Glyph(R.drawable.ic_check_circle, Modifier.size(20.dp), tint = colors.primary)
        }
    }
}

/** 从「宿舍楼洗浴热水表-4#楼-6层-615房」这类位置里取出最后一段的房号（615）。 */
private fun roomFromLocation(location: String): String? {
    val tail = location.split('-', '－', '—', '/', '·').lastOrNull()?.trim().orEmpty()
    if (tail.isEmpty()) return null
    val room = tail.removeSuffix("房").removeSuffix("室").removeSuffix("间").trim()
    if (room.isEmpty()) return null
    if (room.any { it == '楼' || it == '层' || it == '#' }) return null
    return room.takeIf { candidate -> candidate.any { it.isDigit() } }
}

private fun formatBoundAt(millis: Long): String = if (millis <= 0L) "—" else runCatching {
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(BOUND_AT_FORMAT)
}.getOrDefault("—")
