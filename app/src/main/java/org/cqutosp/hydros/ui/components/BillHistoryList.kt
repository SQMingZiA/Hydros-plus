package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.state.BillUi
import org.cqutosp.hydros.ui.state.BillType
import org.cqutosp.hydros.ui.theme.IosTokens
import java.time.YearMonth

/**
 * 钱包账单记录：标题/月份/分段控件在卡片上方，整月记录装进一张 20dp 圆角白卡，
 * 行间 0.5dp 发丝线（首尾不画）。加载、错误与空态直接平铺在页边距内，不套卡片。
 */
fun LazyListScope.billHistoryList(
    month: String,
    billType: BillType,
    bills: List<BillUi>,
    loading: Boolean,
    error: Boolean,
    errorMessage: String? = null,
    onMonth: (String) -> Unit,
    onBillType: (BillType) -> Unit,
    onBill: (String) -> Unit
) {
    val selectedMonth = YearMonth.parse(month)
    val monthBills = bills.filter { it.month == selectedMonth && it.recharge == (billType == BillType.RECHARGE) }

    item("bills-heading", "heading") {
        var showMonths by rememberSaveable { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "账单记录",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.onSurface,
                    maxLines = 1
                )
                Surface(
                    onClick = { showMonths = true },
                    shape = CircleShape,
                    color = colors.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = colors.primary
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${selectedMonth.year}年${selectedMonth.monthValue}月",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Glyph(R.drawable.ic_chevron, modifier = Modifier.size(16.dp), tint = colors.primary)
                    }
                }
            }
            SegmentedControl(
                options = BillType.entries.map { it.label },
                selectedIndex = BillType.entries.indexOf(billType),
                onSelect = { index -> onBillType(BillType.entries[index]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showMonths) {
            BillMonthPickerSheet(
                month = selectedMonth,
                onDismiss = { showMonths = false },
                onConfirm = { selected ->
                    showMonths = false
                    if (selected != selectedMonth) onMonth(selected.toString())
                }
            )
        }
    }

    when {
        loading -> item("bills-loading", "loading") {
            InlineState(
                title = "正在加载${billType.label}",
                loading = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        error -> item("bills-error", "error") {
            InlineState(
                title = "${billType.label}加载未完成",
                description = errorMessage ?: "请重新加载账单",
                action = "重新加载${billType.label}",
                onAction = { onMonth(month) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        monthBills.isEmpty() -> item("bills-empty", "empty") {
            InlineState(
                title = "所选月份暂无${billType.label}",
                icon = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
        else -> item("bills-card", "bills") {
            val dark = isDarkScheme()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (dark) IosTokens.GlassFillDark else IosTokens.Card,
                border = BorderStroke(
                    1.dp,
                    if (dark) IosTokens.GlassStrokeDark else IosTokens.GlassHairline
                )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    monthBills.forEachIndexed { index, bill ->
                        BillRow(bill) { onBill(bill.id) }
                        if (index < monthBills.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
