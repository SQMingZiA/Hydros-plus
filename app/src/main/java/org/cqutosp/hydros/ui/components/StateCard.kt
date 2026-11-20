package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R

/** Inline loading or empty placeholder without an outer card container. */
@Composable
fun InlineState(
    title: String,
    description: String = "",
    loading: Boolean = false,
    icon: Int? = if (loading) null else R.drawable.ic_info,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp)
        } else if (icon != null) {
            IconTile(icon, tone = IconTone.PRIMARY, size = 46.dp, iconSize = 22.dp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 44.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun StateCard(
    title: String,
    description: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        InlineState(
            title = title,
            description = description,
            loading = loading,
            action = action,
            onAction = onAction
        )
    }
}

/** Shared status card with filled background, centered content and unified spacing. */
@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
