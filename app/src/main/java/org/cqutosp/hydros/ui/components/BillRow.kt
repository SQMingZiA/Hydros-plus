package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.state.*

/**
 * 账单明细行：38dp 圆角图标 + 标题/时间 + 右对齐金额（充值朝上为绿色主色）。
 * 行本身不带卡片背景，由父级（BillHistoryList 的月度卡片）负责白底、圆角与描边。
 */
@Composable
fun BillRow(bill: BillUi, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val rowModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    } else Modifier

    val fill = when {
        pressed -> colors.surfaceVariant.copy(alpha = 0.65f)
        hovered -> colors.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val bleed = 16.dp
    Surface(
        shape = RectangleShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
            .then(if (fill == Color.Transparent) Modifier else Modifier.drawBehind {
                val extra = bleed.toPx()
                drawRect(color = fill, topLeft = Offset(-extra, 0f), size = Size(size.width + extra * 2, size.height))
            })
            .then(rowModifier)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                res = if (bill.recharge) R.drawable.ic_wallet else R.drawable.ic_receipt,
                tone = if (bill.recharge) IconTone.PRIMARY else IconTone.NEUTRAL,
                size = 38.dp,
                iconSize = 19.dp
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    bill.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    bill.time.format(BillTimeFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (bill.recharge) "+" else "−"} ¥${money(bill.amountCents)}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (bill.recharge) colors.primary else colors.onSurface,
                    maxLines = 1
                )
                if (onClick != null) {
                    Glyph(
                        R.drawable.ic_chevron,
                        modifier = Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}
