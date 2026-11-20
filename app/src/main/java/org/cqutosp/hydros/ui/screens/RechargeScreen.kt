package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.thirdparty.*
import org.cqutosp.hydros.ui.components.*

/**
 * 充值页：余额、套餐、支付方式、下单入口。
 *
 * 和官方一致，这里只让用户选服务端下发的套餐，不提供自由输入金额，
 * 否则活动 / 赠送 / 限额全部失效。
 *
 * Hydros 没有支付宝 SDK：下单得到的 orderInfo 原样交给上层（onConsumeOrder 之后由
 * 上层尝试 Intent 拉起），本页不会自己产生支付结果；onPaymentResult 留给上层
 * 在真的拿到 resultStatus 时回传。余额本页绝不加减，只能刷新服务端数据。
 */
@Composable
fun RechargeScreen(
    ui: RechargeUi,
    pendingOrder: RechargeOrder?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onSelectPackage: (Long) -> Unit,
    onSelectChannel: (RechargeChannel) -> Unit,
    onSubmit: () -> Unit,
    onConsumeOrder: () -> Unit,
    onPaymentResult: (AlipayResult) -> Unit,
    /** 收款方 / 学校名；网关没有下发厂家信息时退回学校名，两者都没有就不显示。 */
    merchant: String? = null,
) {
    LaunchedEffect(Unit) { onOpen() }
    // 订单被消费后 pendingOrder 会变空，用本地标记把“已交给支付宝”的提示留在页面上。
    var handedOff by remember { mutableStateOf(false) }
    LaunchedEffect(pendingOrder) { if (pendingOrder != null) handedOff = false }

    PageColumn {
        BalanceSection(ui, onRefresh, merchant)

        when {
            ui.loading && ui.packages.isEmpty() && ui.channels.isEmpty() -> {
                InlineState(title = "正在读取充值信息…", loading = true)
            }

            ui.error != null && ui.packages.isEmpty() -> {
                InlineState(
                    title = "充值信息没拿到",
                    description = ui.error.orEmpty(),
                    icon = R.drawable.ic_info,
                    action = "重试",
                    onAction = onOpen,
                )
            }

            ui.channels.isEmpty() -> {
                InlineState(
                    title = "本校暂未开通在线充值",
                    icon = R.drawable.ic_info,
                )
            }

            ui.packages.isEmpty() -> {
                InlineState(
                    title = "暂时没有可选金额",
                    icon = R.drawable.ic_info,
                    action = "重试",
                    onAction = onOpen,
                )
            }

            else -> {
                Section("选择充值金额") {
                    PackageGrid(
                        packages = ui.packages,
                        selectedId = ui.selectedPackageId,
                        enabled = !ui.busy,
                        onSelect = onSelectPackage,
                    )
                }

                Section("支付方式") {
                    ui.channels.forEachIndexed { index, channel ->
                        if (index > 0) SubtleDivider()
                        RadioRow(
                            title = channel.label,
                            selected = ui.selectedChannel == channel,
                            supporting = channelDetail(channel).takeIf { it != channel.label }.orEmpty(),
                            enabled = !ui.busy,
                            onClick = { onSelectChannel(channel) },
                        )
                    }
                }

                if (pendingOrder != null) {
                    PendingOrderSection(
                        order = pendingOrder,
                        onPay = {
                            onConsumeOrder()
                            handedOff = true
                        },
                    )
                }

                if (handedOff) {
                    Text(
                        "支付完成后点「刷新余额」确认到账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ui.notice?.let { notice -> NoticeCard(notice) }

                val selectedAmount = ui.selectedPackage?.amount
                PrimaryAction(
                    label = when {
                        ui.placing -> "正在下单…"
                        selectedAmount != null -> "确认充值 ¥" + selectedAmount
                        else -> "请选择充值金额"
                    },
                    enabled = ui.canSubmit,
                    loading = ui.placing,
                ) { onSubmit() }
            }
        }

    }
}

/** 余额块：浅主色底 + 28sp 金额 + 刷新按钮（页面唯一强调块）。 */
@Composable
private fun BalanceSection(ui: RechargeUi, onRefresh: () -> Unit, merchant: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.primary.copy(alpha = 0.07f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "账户余额",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    ui.balanceCents?.let(::yuanText) ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface,
                )
                if (!merchant.isNullOrBlank()) {
                    Text(
                        "收款方 · " + merchant,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (ui.refreshingBalance) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            color = colors.onSurfaceVariant,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "正在刷新…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .hydrosPressable(enabled = !ui.refreshingBalance, onClick = onRefresh)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    R.drawable.ic_refresh,
                    modifier = Modifier.size(18.dp),
                    description = "刷新余额",
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PackageGrid(
    packages: List<RechargePackage>,
    selectedId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        packages.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { pkg ->
                    PackageTile(
                        pkg = pkg,
                        selected = pkg.id == selectedId,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(pkg.id) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PackageTile(
    pkg: RechargePackage,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .then(if (enabled) Modifier.hydrosPressable(pressedScale = 0.98f, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary.copy(alpha = 0.10f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.primary else Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "¥" + pkg.amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) colors.primary else colors.onSurface,
            )
            val bonus = pkg.bonus
            if (bonus != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "赠 ¥" + bonus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PendingOrderSection(order: RechargeOrder, onPay: () -> Unit) {
    Section("待支付订单") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryAction(
                label = if (order is RechargeOrder.Alipay) "去支付" else "打开支付页面",
                icon = R.drawable.ic_open_in_new,
                onClick = onPay,
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(R.drawable.ic_info, tone = IconTone.NEUTRAL)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 各支付方式的简短标签，与服务端下发的通道对应。 */
private fun channelDetail(channel: RechargeChannel): String = when (channel) {
    RechargeChannel.ALIPAY -> "支付宝"
    RechargeChannel.JUHE -> "微信 / 支付宝 / 银联"
    RechargeChannel.CAMPUS -> "学校一卡通"
}

/** 分 → 两位小数的元，避免 Locale 影响小数点。 */
private fun yuanText(cents: Long): String {
    val abs = kotlin.math.abs(cents)
    val sign = if (cents < 0) "-" else ""
    return sign + "¥" + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
}
