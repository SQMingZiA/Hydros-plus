package org.cqutosp.hydros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.messages.CampusMessage
import org.cqutosp.hydros.ui.components.GlassIconButton
import org.cqutosp.hydros.ui.components.Glyph
import org.cqutosp.hydros.ui.components.InlineState
import org.cqutosp.hydros.ui.components.MessageBody
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.TextAction
import org.cqutosp.hydros.ui.components.hydrosPressable
import org.cqutosp.hydros.ui.components.htmlToPlainText
import org.cqutosp.hydros.ui.components.rememberHydrosHaptics

/**
 * 校园消息（底部一级页）。
 *
 * 版式与首页/钱包一致：顶部一行概览与操作，下面是平铺的消息行，不用卡片容器。
 * 未读行加粗并带一个主色圆点；点开进入详情，进入即标记已读。
 */
@Composable
fun CampusMessagesScreen(
    messages: List<CampusMessage>,
    readRevisions: Collection<String>,
    loading: Boolean = false,
    loaded: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onOpen: (CampusMessage) -> Unit = {},
) {
    val readSet = readRevisions.toSet()
    val unread = messages.count { it.revision !in readSet }
    PageColumn {
        error?.let { InlineState("消息同步失败", it, action = "重新同步", onAction = onRefresh) }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    loading && messages.isEmpty() -> "正在同步…"
                    messages.isEmpty() -> "暂无消息"
                    unread > 0 -> unread.toString() + " 条未读"
                    else -> "全部已读"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (unread > 0) TextAction("全部已读", onClick = onMarkAllRead)
            GlassIconButton(onClick = onRefresh, description = "刷新消息") {
                Glyph(R.drawable.ic_refresh, modifier = Modifier.size(18.dp))
            }
        }

        if (loading && messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        if (loaded && messages.isEmpty() && error == null) {
            InlineState(
                "还没有校园通知",
                "开学通知、停水公告这类消息会出现在这里。",
                icon = R.drawable.ic_message_circle,
            )
        }

        messages.forEach { message ->
            MessageRow(
                message = message,
                unread = message.revision !in readSet,
                onClick = { onOpen(message) },
            )
        }

        if (messages.isNotEmpty()) {
            Text(
                "共 " + messages.size + " 条 · 消息只在本机缓存，点开后才会记为已读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
    }
    LaunchedEffect(Unit) { if (!loaded && !loading) onRefresh() }
}

@Composable
private fun MessageRow(message: CampusMessage, unread: Boolean, onClick: () -> Unit) {
    val haptics = rememberHydrosHaptics()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .hydrosPressable { haptics.light(); onClick() }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unread) Box(
                Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
            Text(
                message.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                message.publishedLabel.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val summary = htmlToPlainText(message.summary.ifBlank { message.body })
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** 消息详情：正文可能是纯文本或 HTML 公告，进入即标记已读。 */
@Composable
fun CampusMessageDetailScreen(
    message: CampusMessage?,
    onRead: (CampusMessage) -> Unit = {},
) {
    LaunchedEffect(message?.revision) { message?.let(onRead) }
    PageColumn {
        if (message == null) {
            InlineState("消息不存在", "可能已被服务端撤回，返回列表看看其他消息。", icon = R.drawable.ic_message_circle)
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    message.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    message.publishedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                MessageBody(message.body.ifBlank { message.summary })
            }
        }
    }
}
