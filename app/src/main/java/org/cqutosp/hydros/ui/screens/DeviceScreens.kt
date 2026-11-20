package org.cqutosp.hydros.ui.screens

import org.cqutosp.hydros.data.feedback.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.*
import org.cqutosp.hydros.ui.theme.IosTokens
import kotlinx.coroutines.delay

@Composable
fun DeviceDetailScreen(device: DeviceUi?, state: HydrosUiState, onConnect: (DeviceUi) -> Unit, onStart: (DeviceUi) -> Unit, onSession: () -> Unit, onBack: () -> Unit, onFavorite: (String) -> Unit, onSaveRemark: suspend (String, String) -> Boolean) {
    var editingRemark by rememberSaveable(device?.id) { mutableStateOf(false) }
    var remark by rememberSaveable(device?.id) { mutableStateOf("") }
    var savingRemark by remember { mutableStateOf(false) }
    var remarkError by remember { mutableStateOf<String?>(null) }
    val favorite = device?.id in state.favoriteDeviceIds
    LaunchedEffect(device?.id, favorite) { if (!favorite) editingRemark = false }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = LocalHydrosSnackbar.current
    val restriction = when {
        device == null -> "设备不存在，请重新扫描设备二维码"
        state.session != null -> {
            val otherDevice = state.session.device.id != device.id
            if (otherDevice) when (state.session.stage) {
                SessionStage.USING -> "已有设备正在使用，请先结束当前设备"
                SessionStage.OPENING -> "当前设备正在开启，请等待结果"
                SessionStage.STOPPING -> "原设备正在关闭，请等待结果"
                SessionStage.OPEN_UNCONFIRMED -> "原设备开启结果待确认，请先返回核实"
                SessionStage.CLOSE_UNCONFIRMED -> "原设备关闭结果待确认，请先返回核实"
                SessionStage.SETTLING, SessionStage.SETTLEMENT_PENDING -> "原设备结算尚未完成，请先返回处理"
                SessionStage.WRITEBACK_PENDING -> "原设备消费记录待清除，请先返回处理"
            } else null
        }
        state.connectingId == device.id && state.connectionStage != ConnectionStage.IDLE -> null
        device.status == DeviceStatus.BUSY -> "设备正在使用，请选择其他设备"
        device.status == DeviceStatus.OFFLINE -> "设备暂时离线，请稍后重试或选择其他设备"
        else -> null
    }
    SnackbarMessage(restriction, eventKey = device?.id to restriction)
    SnackbarMessage(
        state.connectionMessage?.takeIf { device != null && state.connectingId == device.id && state.connectionStage == ConnectionStage.READY },
        eventKey = device?.id to state.connectionMessage,
        error = false,
        severity = FeedbackSeverity.INFO,
    )
    PageColumn {
        if (device == null) SecondaryAction("返回", onClick = onBack)
        else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconTile(device.category.icon, tone = IconTone.PRIMARY, size = 44.dp, iconSize = 22.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = device.displayName(state.deviceRemarks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(device.status.label)
            }
            Section("设备信息") {
                InfoRow("原始设备名", device.name)
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("位置", device.location)
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("设备编号", device.id)
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("当前状态", device.status.label)
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("连接状态", if (state.connectingId != device.id) "未连接" else when (state.connectionStage) {
                    ConnectionStage.IDLE -> "未连接"
                    ConnectionStage.CONNECTING -> "连接中"
                    ConnectionStage.READY -> "已连接"
                    ConnectionStage.FAILED -> "连接失败"
                }, Modifier.semantics(mergeDescendants = true) {})
                SubtleDivider(horizontalPadding = 0.dp)
                InfoRow("预扣金额", "¥ " + money(device.depositCents))
            }
            if (favorite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextAction(
                        label = if (device.id in state.deviceRemarks) "修改备注" else "设置备注",
                        onClick = {
                            remark = state.deviceRemarks[device.id].orEmpty()
                            remarkError = null
                            editingRemark = true
                        }
                    )
                }
            }
            if (device.id in state.connectedDeviceIds) {
                SecondaryAction(
                    label = if (favorite) "取消收藏" else "收藏设备",
                    icon = if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                    onClick = { onFavorite(device.id) }
                )
            }
            state.deviceActionError?.takeIf { state.connectingId == device.id && state.session == null }?.let {
                StateCard("开启未完成", it, action = "重新连接", onAction = { onConnect(device) })
            }
            when {
                state.session != null -> PrimaryAction("进入使用页面", onClick = onSession)
                state.connectingId == device.id && state.connectionStage == ConnectionStage.CONNECTING -> StateCard("正在连接",
                    state.networkMessage ?: state.connectionMessage ?: "正在核实云端连接…", loading = true)
                state.connectingId == device.id && state.connectionStage == ConnectionStage.FAILED -> {
                    StateCard("连接未完成", state.connectionMessage ?: "暂时无法连接设备，请重新连接", action = "重新连接", onAction = { onConnect(device) })
                    if (state.connectionAction == RecoveryAction.SETTINGS) {
                        TextAction("前往设置", onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)))
                        })
                    }
                }
                state.connectingId == device.id && state.connectionStage == ConnectionStage.READY -> {
                    if (device.status == DeviceStatus.BUSY) PrimaryAction("核实并恢复本人订单", enabled = !state.working, loading = state.working) { onStart(device) }
                    else HoldActionButton(device.id, enabled = !state.working, working = state.working) { onStart(device) }
                }
                device.status == DeviceStatus.OFFLINE && device.channel == ControlChannel.CLOUD -> SecondaryAction("返回", onClick = onBack)
                else -> PrimaryAction("连接设备") { onConnect(device) }
            }
        }
    }
    if (editingRemark && device != null && favorite) HydrosDialog(
        onDismiss = { editingRemark = false },
        dismissEnabled = !savingRemark,
        title = "设备备注",
        content = {
            Column {
                FormField(remark, { remark = it; remarkError = null }, "备注", maxLength = DEVICE_REMARK_MAX_LENGTH, error = remarkError)
            }
        },
        dismissButton = { TextAction("取消", enabled = !savingRemark, onClick = { editingRemark = false }) },
        confirmButton = {
            TextAction(
                label = if (savingRemark) "保存中" else "保存",
                enabled = !savingRemark && !state.working,
                loading = savingRemark,
                onClick = {
                    scope.launch {
                        savingRemark = true
                        try {
                            val before = snackbar.notice.value?.id
                            if (onSaveRemark(device.id, remark)) {
                                editingRemark = false
                                snackbar.show("备注已保存", severity = FeedbackSeverity.SUCCESS)
                            } else {
                                remarkError = "保存失败，请重试"
                                if (snackbar.notice.value?.id == before) snackbar.show(remarkError!!, error = true)
                            }
                        } finally { savingRemark = false }
                    }
                }
            )
        }
    )
}

@Composable
fun SessionScreen(session: SessionUi?, onFinish: () -> Unit, onHome: () -> Unit, remarks: Map<String, String> = emptyMap(), working: Boolean = false, observation: SessionObservation = SessionObservation(), resolvingDestination: Boolean = false) {
    var seconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(session?.startedAt, session?.endedAt) {
        while (session != null) {
            seconds = (((session.endedAt ?: System.currentTimeMillis()) - session.startedAt) / 1000).coerceAtLeast(0)
            if (session.endedAt != null) break
            delay(1000)
        }
    }
    val spinning = session != null && (working || session.stage in listOf(SessionStage.OPENING, SessionStage.STOPPING, SessionStage.SETTLING))
    val observationNotice = sessionObservationNotice(session, observation, spinning)
    SnackbarMessage(observationNotice, eventKey = Triple(session?.device?.id, session?.startedAt, observationNotice), error = false, source = "device", severity = FeedbackSeverity.WARNING)
    when {
        session == null && resolvingDestination -> SpinningTransition(title = "正在打开账单")
        session == null -> PageColumn {
            StateCard("当前没有使用中的设备", "", action = "返回首页", onAction = onHome)
        }
        spinning -> SpinningTransition(
            title = sessionBusyTitle(session),
            description = session.message,
            elapsed = elapsed(seconds),
            deviceName = session.device.displayName(remarks),
            onHome = onHome,
        )
        else -> Box(Modifier.fillMaxSize()) {
            WaterSurface()
            PageColumn {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Glyph(
                            session.device.category.icon,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = if (session.stage == SessionStage.USING) "正在" + session.device.category.label else session.stage.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = elapsed(seconds),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp, lineHeight = 62.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "使用时长",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                WaterPanel {
                    WaterInfoRow("设备", session.device.displayName(remarks))
                    WaterHairline()
                    WaterInfoRow("位置", session.device.location)
                    WaterHairline()
                    WaterInfoRow("预扣", "¥ " + money(session.device.depositCents))
                }
                Spacer(Modifier.height(16.dp))
                WaterPillButton(label = sessionActionLabel(session), onClick = onFinish, filled = true)
                WaterPillButton(label = "返回首页", onClick = onHome, filled = false)
            }
        }
    }
}

private fun sessionBusyTitle(session: SessionUi): String = when (session.stage) {
    SessionStage.OPENING -> "正在开启"
    SessionStage.SETTLING, SessionStage.SETTLEMENT_PENDING -> if (session.endedAt != null) "设备已关闭，正在结算" else "正在核实结算"
    SessionStage.WRITEBACK_PENDING -> "正在清除设备记录"
    SessionStage.OPEN_UNCONFIRMED -> "正在核实"
    else -> "正在关闭"
}

@Composable
private fun SpinningTransition(
    title: String,
    description: String? = null,
    elapsed: String? = null,
    deviceName: String? = null,
    onHome: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().testTag("session-transition")) {
        WaterSurface()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 2.5.dp)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (elapsed != null) {
                    Text(
                        elapsed,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 48.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("使用时长", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                if (!deviceName.isNullOrBlank()) {
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
                if (onHome != null) {
                    Spacer(Modifier.height(4.dp))
                    WaterPillButton(label = "返回首页", onClick = onHome, filled = false)
                }
            }
        }
    }
}

@Composable
private fun WaterSurface(modifier: Modifier = Modifier) {
    // 使用页整页纯色，与主按钮同一个蓝，不再有渐变或光斑。
    val background = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
    }
}

@Composable
private fun WaterPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun WaterInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.38f),
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@Composable
private fun WaterHairline() {
    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
}

@Composable
private fun WaterPillButton(label: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    val haptics = rememberHydrosHaptics()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (filled) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.16f))
            .then(if (filled) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), shape))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (filled) colors.primary else Color.White),
                role = Role.Button,
                onClick = {
                    haptics.light()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) colors.primary else Color.White,
        )
    }
}