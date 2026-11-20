package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.state.*

@Composable
fun DeviceRow(device: DeviceUi, remarks: Map<String, String> = emptyMap(), onClick: () -> Unit) {
    val haptics = rememberHydrosHaptics()
    val colors = MaterialTheme.colorScheme
    val statusColor = when (device.status) {
        DeviceStatus.AVAILABLE -> colors.tertiary
        DeviceStatus.BUSY -> colors.secondary
        else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.primary),
                role = Role.Button,
                onClick = { haptics.light(); onClick() }
            )
            .semantics(mergeDescendants = true) { role = Role.Button }
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(device.category.icon, tone = IconTone.PRIMARY, size = 40.dp, iconSize = 20.dp)
        Text(
            text = device.displayName(remarks),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(Modifier.size(6.dp).background(statusColor, CircleShape))
        Glyph(
            R.drawable.ic_chevron,
            modifier = Modifier.size(14.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
