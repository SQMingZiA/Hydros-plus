package org.cqutosp.hydros.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.*
import org.cqutosp.hydros.ui.state.*
import org.cqutosp.hydros.ui.theme.IosTokens

@Composable
fun HomeScreen(
    state: HydrosUiState,
    onScan: () -> Unit,
    onDevice: (String) -> Unit,
    onSession: () -> Unit,
    onScanNearby: () -> Unit,
    onStopNearby: () -> Unit,
    onNearbyDevice: (String) -> Unit = onDevice,
    onWaterReport: () -> Unit = {},
    onWallet: () -> Unit = {}
) {
    val connected = state.connectedDeviceIds.mapNotNull { id -> state.devices.find { it.id == id } }
    // 「我的设备」= 收藏过的设备（不再要求它此刻仍处于最近连接），并按收藏顺序展示。
    val favorites = remember(state.devices, state.connectedDeviceIds, state.favoriteDeviceIds) {
        val pool = (state.devices + connected).distinctBy { it.id }
        state.favoriteDeviceIds.mapNotNull { id -> pool.find { it.id == id } }
    }
    val today = remember { LocalDate.now() }
    val todayCount = remember(state.waterUsage, today) { todaySessions(state.waterUsage, today) }
    val session = state.session
    val name = remember(state.account.name) {
        state.account.name.trim().takeIf { it.isNotBlank() && it != "—" }?.removeSuffix("同学").orEmpty()
    }
    val greeting = remember { greetingOf(java.time.LocalTime.now().hour) }
    Box(Modifier.fillMaxSize()) {
        // 背景由页面级 HydrosPage(background = { HomeBackdrop() }) 提供，避免双层绘制。
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 28.dp)
        ) {
            HomeHeader(
                dateLabel = todayLabel(),
                greeting = greeting,
                name = name,
                todayCount = todayCount,
                onRefresh = onScanNearby
            )
            Spacer(Modifier.height(14.dp))
            if (session != null) {
                GlassPanel(onClick = onSession) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "正在使用",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(IosTokens.PillRadius))
                                .background(Color(0xFF1C1C1E))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Glyph(
                            R.drawable.ic_chevron,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        session.device.displayName(state.deviceRemarks),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sessionSubtitle(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "查看与结束",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Glyph(
                            R.drawable.ic_chevron,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val limitCents = state.waterLimitCents?.takeIf { it > 0 }
                StatCard(
                    icon = R.drawable.ic_water,
                    value = if (limitCents == null) "¥ " + money(state.waterUsage.totalCents)
                    else "¥" + money(state.waterUsage.totalCents) + "/¥" + money(limitCents),
                    label = "本月用水",
                    modifier = Modifier.weight(1f),
                    onClick = onWaterReport
                )
                StatCard(
                    icon = R.drawable.ic_wallet,
                    value = "¥ " + money(state.wallet.totalCents),
                    label = "钱包余额",
                    modifier = Modifier.weight(1f),
                    onClick = onWallet
                )
            }
            // 没有收藏时整块（含分组标题）都不渲染：用户明确不要空态卡片。
            if (favorites.isNotEmpty()) {
                SectionHeader("我的设备")
                GlassPanel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    favorites.forEachIndexed { index, device ->
                        if (index > 0) SubtleDivider(horizontalPadding = 0.dp)
                        FavoriteDeviceRow(device, state.deviceRemarks) { onDevice(device.id) }
                    }
                }
            }
            // 显示设置里可以关掉“连接身边的设备”入口，关闭后首页不再渲染它。
            if (state.homeConnectCardVisible) {
                Spacer(Modifier.height(14.dp))
                GlassPanel(onClick = onScan) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconTile(R.drawable.ic_scan, tone = IconTone.PRIMARY, size = 42.dp, iconSize = 21.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "连接身边的设备",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "扫描设备二维码，连接后即可开始使用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Glyph(
                            R.drawable.ic_chevron,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            NearbyDevices(
                state.nearby,
                state.devices,
                onScanNearby,
                onStopNearby,
                onNearbyDevice,
                state.deviceRemarks,
                demoMode = state.demoMode,
            )
        }
    }
}

/** 首页顶部：日期、问候语、姓名与今日用水次数，右侧一个圆形玻璃刷新按钮。 */
@Composable
private fun HomeHeader(
    dateLabel: String,
    greeting: String,
    name: String,
    todayCount: Int,
    onRefresh: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (name.isNotBlank()) greeting + "，" + name else greeting,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                when (todayCount) {
                    0 -> "今天还没有用水"
                    1 -> "今天用了 1 次水"
                    else -> "今天用了 " + todayCount + " 次水"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 钱包入口在上方顶部标签里，这里只保留刷新。
        HomeRoundButton(onClick = onRefresh) {
            Glyph(
                R.drawable.ic_refresh,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 首页右上角的圆形玻璃按钮。 */
@Composable
private fun HomeRoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val haptics = rememberHydrosHaptics()
    val dark = isDarkScheme()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (dark) IosTokens.GlassFillDark else Color(0xCCFFFFFF))
            .border(
                0.75.dp,
                if (dark) IosTokens.GlassStrokeDark else Color(0xE6FFFFFF),
                CircleShape
            )
            .hydrosPressable { haptics.light(); onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 首页双列数字卡：左上图标、右上箭头、中间大数字、底部灰字说明。 */
@Composable
private fun StatCard(
    icon: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassPanel(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tone = IconTone.PRIMARY, size = 30.dp, iconSize = 16.dp)
            Spacer(Modifier.weight(1f))
            Glyph(
                R.drawable.ic_chevron,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            value,
            style = if (value.length > 10) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 时段问候语。 */
private fun greetingOf(hour: Int): String = when {
    hour < 5 -> "夜深了"
    hour < 11 -> "早上好"
    hour < 14 -> "中午好"
    hour < 18 -> "下午好"
    else -> "晚上好"
}

/** "2026年11月20日 · 星期五"。 */
private fun todayLabel(): String {
    val today = LocalDate.now()
    val week = when (today.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "星期一"
        java.time.DayOfWeek.TUESDAY -> "星期二"
        java.time.DayOfWeek.WEDNESDAY -> "星期三"
        java.time.DayOfWeek.THURSDAY -> "星期四"
        java.time.DayOfWeek.FRIDAY -> "星期五"
        java.time.DayOfWeek.SATURDAY -> "星期六"
        java.time.DayOfWeek.SUNDAY -> "星期日"
    }
    return "%d年%d月%d日 · %s".format(today.year, today.monthValue, today.dayOfMonth, week)
}

@Composable
fun WalletScreen(state: HydrosUiState, onMonth: (String) -> Unit = {}, onBillType: (BillType) -> Unit = {}, onRefresh: () -> Unit = {}, onBill: (String) -> Unit) {
    val haptics = rememberHydrosHaptics()
    // 底栏是悬浮玻璃，不占布局高度；列表底部必须自己让出这段空间，否则最后一条会被压住。
    val glassInset = LocalLiquidGlassInset.current
    val month = state.month
    val walletError = state.walletError
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = glassInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item("wallet-balance", "balance") {
                GlassPanel(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "钱包余额",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextAction("刷新", onClick = onRefresh)
                    }
                    when {
                        state.wallet.totalMilli != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "¥ " + money(state.wallet.totalCents),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.walletLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        state.walletLoading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在获取余额…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Text(
                            "余额暂无法获取",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (walletError != null) item("wallet-error") {
                InlineState("余额刷新未完成", walletError, action = "重新刷新", onAction = onRefresh)
            }
            billHistoryList(
                month = month,
                billType = state.billType,
                bills = state.bills,
                loading = state.loadingBills,
                error = state.billsError != null,
                errorMessage = state.billsError,
                onMonth = onMonth,
                onBillType = { type ->
                    if (type != state.billType) haptics.medium()
                    onBillType(type)
                },
                onBill = onBill
            )
        }
    }
}

@Composable
fun MineScreen(
    state: HydrosUiState,
    onManageAccount: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    hasUpdate: Boolean = false,
    onFeedback: () -> Unit = {},
    onDisplay: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onWaterReport: () -> Unit = {},
    onWaterLimit: (Long?) -> Unit = {},
    onWaterLimitPage: () -> Unit = {},
    onUseCode: () -> Unit = {},
    onDorm: () -> Unit = {},
    onRecharge: () -> Unit = {},
    onAvatarPath: (String?) -> Unit = {},
    onMessages: () -> Unit = {},
    onNotifications: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            scope.launch {
                // 相册原图可能有十几兆，先等比缩到 512px 再存进应用私有目录，避免每次渲染都解码大图。
                val saved = runCatching { storeAvatar(context, picked) }.getOrNull()
                if (saved != null) onAvatarPath(saved)
            }
        }
    }
    PageColumn {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(state.avatarPath) {
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    state.account.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    maskPhone(state.account.phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Section {
            MineRow(
                "管理账户",
                icon = R.drawable.ic_person,
                tone = IconTone.PRIMARY,
                onClick = onManageAccount
            )
        }
        if (state.accountError != null) {
            InlineState("账户资料未更新", state.accountError, action = "刷新资料", onAction = onRefresh)
        }
        Section("校园服务") {
            val unread = state.unreadMessageCount
            MineRow("校园消息", icon = R.drawable.ic_message_circle, tone = IconTone.PRIMARY,
                trailing = if (unread > 0) {
                    {
                        Text(
                            unread.toString() + " 条未读",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else null,
                onClick = onMessages)
            SubtleDivider()
            MineRow("智能用水报告", icon = R.drawable.ic_water, tone = IconTone.PRIMARY,
                trailing = {
                    Text(
                        "¥ " + money(state.waterUsage.totalCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = onWaterReport)
            SubtleDivider()
            MineRow("用水限额", icon = R.drawable.ic_notifications, tone = IconTone.SECONDARY,
                onClick = onWaterLimitPage)
            SubtleDivider()
            MineRow("使用码", icon = R.drawable.ic_code, tone = IconTone.PRIMARY,
                onClick = onUseCode)
            SubtleDivider()
            MineRow("绑定寝室", icon = R.drawable.ic_home_outline, tone = IconTone.SECONDARY,
                onClick = onDorm)
        }
        Section("个性化") {
            MineRow("反馈设置", icon = R.drawable.ic_vibration, tone = IconTone.PRIMARY, onClick = onFeedback)
            SubtleDivider()
            MineRow("显示设置", icon = R.drawable.ic_display_settings, tone = IconTone.PRIMARY, onClick = onDisplay)
            SubtleDivider()
            MineRow("通知管理", icon = R.drawable.ic_notifications, tone = IconTone.PRIMARY, onClick = onNotifications)
            SubtleDivider()
            MineRow("应用外观", icon = R.drawable.ic_palette, tone = IconTone.SECONDARY, onClick = onAppearance)
        }
        Section("应用与支持") {
            val updateBadge: (@Composable () -> Unit)? =
                if (hasUpdate) { { StatusBadge("有新版本", primary = true) } } else null
            MineRow("关于 Hydros+", icon = R.drawable.ic_info, tone = IconTone.TERTIARY,
                trailing = updateBadge,
                onClick = onAbout)
        }
    }
}

/** 每月用水限额编辑对话框：留空即取消限额，输入按“元”换算成分。 */
@Composable
internal fun WaterLimitDialog(initialCents: Long?, onDismiss: () -> Unit, onConfirm: (Long?) -> Unit) {
    var text by remember { mutableStateOf(initialCents?.let { money(it) }?.takeIf { it != "—" } ?: "") }
    val cents = remember(text) {
        text.trim().toBigDecimalOrNull()
            ?.takeIf { it > java.math.BigDecimal.ZERO }
            ?.multiply(java.math.BigDecimal(100))
            ?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
    }
    val valid = text.isBlank() || cents != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每月用水限额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("达到后提醒一次。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("限额金额（元）") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cents) }, enabled = valid) {
                Text(if (text.isBlank()) "取消限额" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun BillDetailScreen(
    bill: BillUi?,
    onWallet: () -> Unit,
    durationPending: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    backLabel: String = "返回钱包",
) {
    SnackbarMessage(if (bill == null && error == null) "未找到账单，请返回${backLabel.removePrefix("返回")}查看其他记录" else null)
    PageColumn {
        error?.let { InlineState("账单详情未更新", it, action = "重新查询", onAction = onRetry) }
        if (bill == null) SecondaryAction(backLabel, onClick = onWallet)
        else {
            // 账单头部居中：状态 + 金额 + 设备/渠道名，消费与充值共用同一版式。
            GlassPanel(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (bill.recharge) "充值完成" else if (bill.amountMilli == null) "消费金额待查询" else "消费已结算",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        (if (bill.recharge) "+" else "−") + " ¥" + money(bill.amountCents),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        bill.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!bill.recharge && bill.location.isNotBlank()) {
                        Text(
                            bill.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Section("详情") {
                if (bill.recharge) {
                    InfoRow("支付方式", bill.location.ifBlank { "—" })
                    SubtleDivider()
                } else {
                    InfoRow("项目", bill.title)
                    SubtleDivider()
                    InfoRow("位置", bill.location.ifBlank { "—" })
                    SubtleDivider()
                }
                InfoRow("时间", bill.time.toLocalDate().toString() + " " + bill.time.toLocalTime().withNano(0))
                if (!bill.recharge && (bill.durationText != null || durationPending)) {
                    SubtleDivider()
                    InfoRow("使用时长", bill.durationText ?: "查询中…")
                }
                if (!bill.recharge && bill.consumeDate.isNotBlank()) {
                    SubtleDivider()
                    InfoRow("消费日期", bill.consumeDate.take(10))
                }
                if (!bill.recharge) {
                    bill.waterText?.let { water ->
                        SubtleDivider()
                        InfoRow("用水量", water)
                    }
                }
                SubtleDivider()
                InfoRow("订单编号", bill.id)
            }
            PrimaryAction(backLabel, onClick = onWallet)
        }
    }
}

/** 概览数字行：左侧灰色标签，右侧加粗数值。 */
@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** 收藏设备行：左侧圆角图标块、名称与区域、右侧状态点与箭头。 */
@Composable
private fun FavoriteDeviceRow(device: DeviceUi, remarks: Map<String, String>, onClick: () -> Unit) {
    val haptics = rememberHydrosHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .hydrosPressable { haptics.light(); onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(device.category.icon, tone = IconTone.PRIMARY)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                device.displayName(remarks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(device.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusDot(device.status)
        Glyph(R.drawable.ic_chevron, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusDot(status: DeviceStatus) {
    val color = when (status) {
        DeviceStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
        DeviceStatus.BUSY -> MaterialTheme.colorScheme.error
        DeviceStatus.OFFLINE -> MaterialTheme.colorScheme.outline
        DeviceStatus.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}

private fun todaySessions(report: WaterUsageReport, today: LocalDate): Int {
    if (!report.loaded) return 0
    if (YearMonth.from(today).toString() != report.month) return 0
    return report.dailyUsages.firstOrNull { it.day == today.dayOfMonth }?.sessions ?: 0
}

private fun sessionSubtitle(session: SessionUi): String {
    val start = runCatching {
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
    return if (start.isBlank()) session.device.location else session.device.location + " · " + start + " 开始"
}

/** 头像按钮：圆形裁剪 + 右下角相机角标，点击打开系统相册。 */
@Composable
private fun ProfileAvatar(path: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val avatar = remember(path, context) {
        // 兼容三种历史存法：固定文件名、旧版绝对路径、更早版本直接存的 content:// URI。
        val file = path?.let { raw ->
            if (raw.startsWith("/")) File(raw) else File(File(context.filesDir, "avatar"), raw)
        }
        when {
            file != null && file.exists() ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            path != null ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            else -> null
        }
    }
    Box(Modifier.size(60.dp)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .hydrosPressable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (avatar != null) {
                Image(avatar, contentDescription = "头像", modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            } else {
                Glyph(R.drawable.ic_person, Modifier.size(28.dp), description = "头像",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            Modifier.align(Alignment.BottomEnd).size(20.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_photo, Modifier.size(11.dp), description = "更换头像",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 把相册里选中的图片缩到 512px 以内再写进应用私有目录，返回文件绝对路径。
 * 文件名带时间戳：路径变化能让界面里的 remember(path) 自动失效，不用额外维护版本号。
 */
/** 头像文件名固定，避免升级后绝对路径失效。 */
private const val AVATAR_FILE_NAME = "avatar.jpg"

private suspend fun storeAvatar(context: android.content.Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val bitmap = decodeAvatar(context, uri, maxSide = 512) ?: return@withContext null
    val dir = File(context.filesDir, "avatar").apply { mkdirs() }
    // 固定文件名 + 只存文件名：升级/重装后路径变化也不会丢头像。
    val target = File(dir, AVATAR_FILE_NAME)
    val temp = File(dir, "avatar.tmp")
    temp.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output) }
    bitmap.recycle()
    if (!temp.renameTo(target)) {
        target.outputStream().use { output -> temp.inputStream().copyTo(output) }
        temp.delete()
    }
    dir.listFiles()?.forEach { file -> if (file.name != target.name) file.delete() }
    AVATAR_FILE_NAME
}

/** 两趟解码：先读尺寸算出采样率，再真正解码，避免把相机原图整张读进内存。 */
private fun decodeAvatar(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
}

/** 手机号脱敏：保留前 3 位与后 4 位，不足 7 位原样返回。 */
private fun maskPhone(phone: String): String {
    val trimmed = phone.trim()
    if (trimmed.length < 7) return trimmed
    return trimmed.take(3) + " **** " + trimmed.takeLast(4)
}

/** "2026-11" → "2026年11月"：列表行里说明当前统计的月份。 */
private fun monthText(month: String): String {
    val parts = month.split("-")
    return if (parts.size == 2) parts[0] + "年" + parts[1].trimStart('0') + "月" else "本月"
}
