package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

private const val DORM_ROOM_LIMIT = 32
private const val DORM_MAC_LIMIT = 17
private const val DORM_LABEL_LIMIT = 24
private const val DORM_ROOM_ERROR = "请输入寝室号"
private const val DORM_MAC_ERROR = "请输入 12 位十六进制 MAC，如 AA:BB:CC:DD:EE:FF"

/**
 * 绑定寝室页。
 *
 * 流程是一台明确的状态机：
 * 1. 已绑定且未点「重新绑定」→ 只显示绑定信息卡，不再展示设备列表与表单；
 * 2. 未绑定 / 点了「重新绑定」→ 自动扫描附近设备，列表是待选清单（点一下只是选中，不会偷偷写表单）；
 * 3. 选中设备后单独展开「确认信息」（可改寝室号与备注），底部只有一个主按钮完成绑定；
 * 4. 手动输入 MAC 收在文字按钮里，默认折叠，作为扫描不到时的备选。
 */
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
    onEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
) {
    val snackbar = LocalHydrosSnackbar.current
    // 页面内部也可以自行进入编辑态：外部未接通 onEdit 时「重新绑定」仍然可用。
    var localEditing by remember { mutableStateOf(false) }
    val showForm = bound == null || editing || localEditing
    val boundKey = bound?.let { it.room + "|" + it.deviceMac + "|" + it.label + "|" + it.boundAt }

    // 同一份草稿同时服务于"选中设备"与"手动输入"两条路径，避免出现两份互相打架的输入。
    var roomInput by remember(bound) { mutableStateOf(bound?.room.orEmpty()) }
    var macInput by remember(bound) { mutableStateOf(bound?.deviceMac.orEmpty()) }
    var labelInput by remember(bound) { mutableStateOf(bound?.label.orEmpty()) }
    var selectedDeviceId by remember(bound) { mutableStateOf<String?>(null) }
    var manualOpen by remember(bound) { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    // 只在提交后校验一次，避免用户还没开始填就满屏报错。
    var pendingBind by remember { mutableStateOf(false) }
    var saveBaseline by remember { mutableStateOf<String?>(null) }
    var savedNotice by remember { mutableStateOf<String?>(null) }

    val selectedDevice = nearbyDevices.firstOrNull { it.id == selectedDeviceId }
    val showConfirm = selectedDevice != null && !manualOpen

    // 进入表单态时扫描一次：未绑定时是首次扫描，点「重新绑定」时也会重新拿一遍附近列表。
    // 路由层进入页面时已经触发过一次扫描，这里只在没有扫描在跑时补一次，避免重复启动。
    LaunchedEffect(showForm) {
        if (showForm && !nearbySearching) onScanNearby()
    }

    // onSave 是异步落盘，这里通过 bound 的变化判定成功：绑定值变了才算成功，报错则留在表单里。
    LaunchedEffect(pendingBind, boundKey, error) {
        if (!pendingBind) return@LaunchedEffect
        if (error != null) {
            pendingBind = false
            return@LaunchedEffect
        }
        if (boundKey == null || boundKey == saveBaseline) return@LaunchedEffect
        val current = bound ?: return@LaunchedEffect
        pendingBind = false
        localEditing = false
        val notice = "已绑定寝室：" + current.room
        savedNotice = notice
        snackbar.show(notice)
    }

    fun submitDraft(room: String, mac: String, label: String) {
        val normalizedMac = normalizeDormMac(mac) ?: return
        if (normalizeDormRoom(room).isEmpty()) return
        savedNotice = null
        saveBaseline = boundKey
        pendingBind = true
        onSave(normalizeDormRoom(room), normalizedMac, label.trim())
    }

    fun startEditing() {
        val current = bound ?: return
        localEditing = true
        savedNotice = null
        submitted = false
        selectedDeviceId = null
        manualOpen = false
        roomInput = current.room
        macInput = current.deviceMac
        labelInput = current.label
        onEdit()
    }

    fun cancelEditing() {
        val current = bound
        localEditing = false
        pendingBind = false
        submitted = false
        selectedDeviceId = null
        manualOpen = false
        roomInput = current?.room.orEmpty()
        macInput = current?.deviceMac.orEmpty()
        labelInput = current?.label.orEmpty()
        onCancelEdit()
    }

    PageColumn {
        if (bound != null && !showForm) {
            Section("已绑定寝室") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconTile(R.drawable.ic_water, tone = IconTone.PRIMARY, size = 44.dp, iconSize = 22.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            bound.room,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            bound.label.ifBlank { "寝室热水设备" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("设备 MAC", bound.deviceMac)
                InfoRow("绑定时间", formatBoundAt(bound.boundAt))
                Text(
                    "绑定后，扫描到同寝室的设备会直接认作本寝室的设备，无需再挑一次。",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionRow(Modifier.padding(top = 14.dp)) {
                    SecondaryAction(
                        label = "重新绑定",
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.ic_refresh,
                        onClick = { startEditing() }
                    )
                    DangerAction(
                        label = "解除绑定",
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.ic_shield,
                        onClick = {
                            savedNotice = null
                            localEditing = false
                            pendingBind = false
                            onClear()
                        }
                    )
                }
            }
            savedNotice?.let {
                InlineState(
                    title = "绑定成功",
                    description = it,
                    icon = R.drawable.ic_check_circle
                )
            }
        } else {
            if (bound != null) {
                Section(if (manualOpen) "重新绑定（手动输入）" else "重新绑定寝室") {
                    Text(
                        "当前绑定：" + bound.room,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // 附近设备：点一下只是选中（高亮 + 勾选），不写表单。
            Section(if (bound == null) "选择寝室设备" else null) {
                when {
                    nearbySearching && nearbyDevices.isEmpty() -> InlineState(
                        title = "正在搜索附近设备…",
                        description = "请靠近寝室热水表，并保持本页面打开。",
                        loading = true
                    )
                    nearbyDevices.isEmpty() -> InlineState(
                        title = "没有找到设备",
                        description = "附近没有搜到可绑定的热水表。可以重新搜索，或在下方手动输入机身标签上的 MAC。",
                        icon = R.drawable.ic_bluetooth,
                        action = "重新搜索",
                        onAction = onScanNearby
                    )
                    else -> {
                        ScanStatusRow(searching = nearbySearching, count = nearbyDevices.size)
                        nearbyDevices.forEachIndexed { index, device ->
                            DormDeviceRow(
                                device = device,
                                isSelected = device.id == selectedDeviceId,
                                onClick = {
                                    selectedDeviceId = device.id
                                    macInput = device.mac
                                    labelInput = device.name
                                    roomFromLocation(device.location)?.let { roomInput = it }
                                    submitted = false
                                }
                            )
                            if (index < nearbyDevices.lastIndex) SubtleDivider(horizontalPadding = 0.dp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextAction(label = "重新搜索附近设备", onClick = onScanNearby)
                        }
                    }
                }
            }

            // 选中后的确认区：寝室号与备注都可以改，MAC 只读展示。
            val confirmed = selectedDevice
            if (confirmed != null && !manualOpen) {
                Section("确认信息") {
                    InfoRow("设备", confirmed.name)
                    InfoRow("设备 MAC", confirmed.mac)
                    SubtleDivider(horizontalPadding = 0.dp)
                    FormField(
                        value = roomInput,
                        onValue = { roomInput = it },
                        label = "寝室号",
                        error = if (submitted && roomInput.isBlank()) DORM_ROOM_ERROR else null,
                        maxLength = DORM_ROOM_LIMIT
                    )
                    FormField(
                        value = labelInput,
                        onValue = { labelInput = it },
                        label = "备注（可选）",
                        maxLength = DORM_LABEL_LIMIT
                    )
                }
            } else if (!manualOpen) {
                Section {
                    Text(
                        "选择上方任意一台设备后，会在这里确认寝室号并完成绑定。",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 表单态唯一的主按钮：没选设备时也显示，只是禁用并说明原因，避免"填完才发现不能提交"。
            if (!manualOpen) {
                val picked = selectedDevice
                BindSubmitButton(
                    problem = bindProblem(picked, roomInput),
                    onClick = {
                        submitted = true
                        if (picked != null) {
                            submitDraft(roomInput, macInput, labelInput.ifBlank { picked.name })
                        }
                    }
                )
            }

            // 手动输入：默认折叠，扫描不到时再展开。
            Section(if (showConfirm) "备选：手动输入 MAC" else null) {
                TextAction(
                    label = if (manualOpen) "收起手动输入" else "手动输入 MAC",
                    trailingIcon = if (manualOpen) null else R.drawable.ic_code,
                    onClick = {
                        manualOpen = !manualOpen
                        submitted = false
                    }
                )
                if (manualOpen) {
                    Text(
                        "按机身标签填写，MAC 支持带冒号或不带冒号的 12 位十六进制。",
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FormField(
                        value = roomInput,
                        onValue = { roomInput = it },
                        label = "寝室号",
                        error = if (submitted && roomInput.isBlank()) DORM_ROOM_ERROR else null,
                        maxLength = DORM_ROOM_LIMIT
                    )
                    FormField(
                        value = macInput,
                        onValue = { macInput = it },
                        label = "设备 MAC",
                        error = if (submitted && normalizeDormMac(macInput) == null) DORM_MAC_ERROR else null,
                        maxLength = DORM_MAC_LIMIT
                    )
                    FormField(
                        value = labelInput,
                        onValue = { labelInput = it },
                        label = "备注（可选）",
                        maxLength = DORM_LABEL_LIMIT
                    )
                    BindSubmitButton(
                        problem = when {
                            roomInput.isBlank() -> DORM_ROOM_ERROR
                            normalizeDormMac(macInput) == null -> DORM_MAC_ERROR
                            else -> null
                        },
                        label = "保存绑定",
                        onClick = {
                            submitted = true
                            submitDraft(roomInput, macInput, labelInput)
                        }
                    )
                }
            }

            if (bound != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextAction(label = "取消重新绑定", onClick = { cancelEditing() })
                }
            }
        }

        error?.let {
            InlineState(
                title = "绑定未完成",
                description = it,
                icon = R.drawable.ic_info
            )
        }
    }
}

/** 底部唯一主按钮：未满足条件时禁用并通过 disabledReason 说明原因。 */
@Composable
private fun BindSubmitButton(
    problem: String?,
    onClick: () -> Unit,
    label: String = "绑定到我的寝室",
) {
    Column(Modifier.padding(top = 14.dp)) {
        PrimaryAction(
            label = label,
            enabled = problem == null,
            disabledReason = problem,
            icon = R.drawable.ic_check_circle,
            onClick = onClick
        )
    }
}

/** 选中设备时的阻塞原因；null 表示可以提交。 */
private fun bindProblem(device: DormNearbyDevice?, room: String): String? = when {
    device == null -> "请先选择一台附近的热水表"
    room.isBlank() -> DORM_ROOM_ERROR
    normalizeDormMac(device.mac) == null -> "这台设备的 MAC 无法识别，请改用手动输入"
    else -> null
}

/** 扫描状态行：「正在搜索附近设备… / 已发现 N 台设备」。 */
@Composable
private fun ScanStatusRow(searching: Boolean, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (searching) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        Text(
            text = if (searching) "正在搜索附近设备…" else "已发现 " + count + " 台设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一台附近设备：点一下只改变选中态（高亮 + 勾选），不会顺手填表单。 */
@Composable
private fun DormDeviceRow(
    device: DormNearbyDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .hydrosPressable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected }
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(
            R.drawable.ic_water,
            tone = if (isSelected) IconTone.PRIMARY else IconTone.NEUTRAL,
            size = 38.dp,
            iconSize = 19.dp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
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
                color = colors.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }
        if (isSelected) {
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
