package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.ui.theme.IosTokens

enum class IconTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL
}

@Composable
fun Glyph(
    res: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(res),
        contentDescription = description,
        modifier = modifier.size(24.dp),
        tint = tint
    )
}

/** 列表行左侧的浅色圆角图标块。 */
@Composable
fun IconTile(
    res: Int,
    modifier: Modifier = Modifier,
    tone: IconTone = IconTone.NEUTRAL,
    size: Dp = 32.dp,
    iconSize: Dp = 17.dp
) {
    val dark = isDarkScheme()
    val colors = MaterialTheme.colorScheme
    val (bgColor, fgColor) = when (tone) {
        IconTone.PRIMARY -> (if (dark) colors.primary.copy(alpha = 0.22f) else colors.primary.copy(alpha = 0.14f)) to colors.primary
        IconTone.SECONDARY -> (if (dark) colors.secondary.copy(alpha = 0.22f) else IosTokens.Violet.copy(alpha = 0.14f)) to colors.secondary
        IconTone.TERTIARY -> (if (dark) colors.tertiary.copy(alpha = 0.22f) else IosTokens.Success.copy(alpha = 0.16f)) to colors.tertiary
        IconTone.NEUTRAL -> (if (dark) colors.surfaceVariant else Color(0xFFEFF2F8)) to colors.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Glyph(res, modifier = Modifier.size(iconSize), tint = fgColor)
    }
}