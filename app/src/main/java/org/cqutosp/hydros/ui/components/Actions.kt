package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.ui.theme.IosTokens

/**
 * 主操作：实心主色按钮（48dp 高、8dp 圆角）。
 * 保留原有 enabled / loading / disabledReason 语义（禁用原因用 snackbar 提示）。
 */
@Composable
fun PrimaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    icon: Int? = null,
    onClick: () -> Unit
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    val painted = loading || (enabled && disabledReason == null)
    val shape = RoundedCornerShape(IosTokens.ControlRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .hydrosPressable(
                role = Role.Button,
                enabled = enabled || loading,
                onClick = {
                    if (loading) return@hydrosPressable
                    if (disabledReason != null) {
                        snackbar.show(disabledReason)
                    } else {
                        haptics.light()
                        onClick()
                    }
                }
            )
            .clip(shape)
            .background(
                when {
                    painted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .semantics {
                if (loading) stateDescription = "正在进行"
                else if (disabledReason != null) stateDescription = disabledReason
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val contentColor = if (painted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            if (loading) {
                BusyIndicator(contentColor)
            } else if (icon != null) {
                Glyph(icon, modifier = Modifier.size(19.dp), tint = contentColor)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** 次操作：主色细描边按钮（透明底 + 蓝色文字）。 */
@Composable
fun SecondaryAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    icon: Int? = null,
    onClick: () -> Unit
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    val effectiveEnabled = enabled && disabledReason == null
    val dark = isDarkScheme()
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(IosTokens.ControlRadius),
        color = Color.Transparent,
        contentColor = if (effectiveEnabled) accent else accent.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (effectiveEnabled) accent.copy(alpha = 0.6f) else accent.copy(alpha = 0.24f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .hydrosPressable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (disabledReason != null) {
                        snackbar.show(disabledReason)
                    } else {
                        haptics.light()
                        onClick()
                    }
                }
            )
            .semantics { if (disabledReason != null) stateDescription = disabledReason }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Glyph(icon, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TextAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    disabledReason: String? = null,
    trailingIcon: Int? = null,
    onClick: () -> Unit
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    val baseColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val painted = loading || (enabled && disabledReason == null)
    val contentColor = if (painted) baseColor else baseColor.copy(alpha = 0.4f)
    TextButton(
        onClick = {
            if (loading) return@TextButton
            if (disabledReason != null) {
                snackbar.show(disabledReason)
            } else {
                haptics.light()
                onClick()
            }
        },
        enabled = enabled || loading,
        modifier = modifier.heightIn(min = 44.dp).semantics {
            if (loading) stateDescription = "正在进行"
            else if (disabledReason != null) stateDescription = disabledReason
        },
        shape = RoundedCornerShape(IosTokens.ControlRadius),
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = baseColor.copy(alpha = 0.4f)
        )
    ) {
        if (loading) {
            BusyIndicator(contentColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (!loading && trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Glyph(trailingIcon, modifier = Modifier.size(16.dp), tint = contentColor)
        }
    }
}

/** 危险操作：柔和红底按钮。 */
@Composable
fun DangerAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    icon: Int? = null,
    onClick: () -> Unit
) {
    val snackbar = LocalHydrosSnackbar.current
    val haptics = rememberHydrosHaptics()
    val painted = loading || (enabled && disabledReason == null)
    val contentColor = if (painted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    Button(
        onClick = {
            if (loading) return@Button
            if (disabledReason != null) {
                snackbar.show(disabledReason)
            } else {
                haptics.warning()
                onClick()
            }
        },
        enabled = enabled || loading,
        shape = RoundedCornerShape(IosTokens.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            if (loading) stateDescription = "正在进行"
            else if (disabledReason != null) stateDescription = disabledReason
        }
    ) {
        if (loading) {
            BusyIndicator(contentColor)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Glyph(icon, modifier = Modifier.size(19.dp), tint = contentColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BusyIndicator(color: Color) {
    CircularProgressIndicator(Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
}

/** Horizontal row of equal-priority actions, spaced by 12dp. */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}