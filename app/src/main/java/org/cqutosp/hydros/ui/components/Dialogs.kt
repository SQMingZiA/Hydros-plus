package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 共享对话框外壳：14dp 圆角、纯白底、无 tonal 提升、
 * 标题 20sp SemiBold。内容/按钮/操作状态仍由调用方提供。
 */
@Composable
fun HydrosDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissEnabled: Boolean = true,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        modifier = Modifier.dismissSnackbarOnInteraction(),
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = { ModalSnackbarHost(); content() },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    )
}

/** Scrollable choices shared by month selection and multiple QR results. */
@Composable
fun <T : Any> ChoiceDialog(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "关闭"
) {
    val haptics = rememberHydrosHaptics()
    HydrosDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(options) { option ->
                TextButton(
                    onClick = { haptics.light(); onSelect(option) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
