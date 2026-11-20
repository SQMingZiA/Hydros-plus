package org.cqutosp.hydros.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.ui.components.FormField
import org.cqutosp.hydros.ui.components.IconTile
import org.cqutosp.hydros.ui.components.IconTone
import org.cqutosp.hydros.ui.components.LocalHydrosSnackbar
import org.cqutosp.hydros.ui.components.hydrosPressable
import org.cqutosp.hydros.ui.components.PageColumn
import org.cqutosp.hydros.ui.components.PrimaryAction
import org.cqutosp.hydros.ui.components.Section
import org.cqutosp.hydros.ui.components.SwitchRow
import org.cqutosp.hydros.voice.VoiceCommands

/** 语音助手（实验）：总开关、一键复制的接入信息、可点选的口令与在线试用。 */
@Composable
fun VoiceAssistantScreen(
    enabled: Boolean = false,
    onEnabled: (Boolean) -> Unit = {},
    onTest: (String) -> Unit = {},
) {
    var testText by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val snackbar = LocalHydrosSnackbar.current
    val examples = listOf(
        "打开宿舍热水" to "开始用水",
        "结束用水" to "结束当前用水",
        "查余额" to "播报余额",
        "本月消费" to "播报本月支出",
        "我要充值" to "打开充值页",
    )
    val link = "hydros://voice?q=打开宿舍热水"

    PageColumn {
        Section("语音助手") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(R.drawable.ic_mic, tone = if (enabled) IconTone.PRIMARY else IconTone.NEUTRAL, size = 44.dp, iconSize = 22.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "实验功能",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        if (enabled) "已开启外部口令" else "默认关闭，不会响应任何外部口令",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                }
            }
            SwitchRow(
                title = "允许语音助手控制 Hydros",
                checked = enabled,
                supporting = "开启后，手机自带语音助手送来的口令才会被执行",
                icon = R.drawable.ic_mic,
                onCheckedChange = onEnabled,
            )
            Text(
                "口令只在本机解析，不保存也不上传录音；关闭开关后外部口令会被直接忽略。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Section("可用口令") {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                examples.forEach { (command, _) ->
                    SurfacePill(command) { testText = command }
                }
            }
            Text(
                "点一下口令填入下方试用框；也可以说“把宿舍热水打开”这类同义说法。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Section("一键接入") {
            StepRow("1", "复制下方链接")
            StepRow("2", "打开手机的语音助手 → 快捷指令 → 新建")
            StepRow("3", "动作选择“打开链接”，粘贴后保存")
            DeepLinkBlock(link)
            PrimaryAction(
                label = "复制接入信息",
                icon = R.drawable.ic_receipt,
                onClick = {
                    val text = buildString {
                        append("Hydros+ 语音助手接入\n")
                        append("链接：").append(link).append('\n')
                        append("Intent 组件：org.cqutosp.hydros/.voice.VoiceCommandActivity\n")
                        append("extra：").append(VoiceCommands.EXTRA_COMMAND).append(" = 一句口令")
                    }
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Hydros+ 语音助手接入", text))
                    snackbar.show("接入信息已复制")
                },
            )
            Text(
                "小爱同学、小艺、Jovi、Breeno 等助手的“快捷指令 / 自定义语音”都支持打开链接；" +
                    "只支持 Intent 的助手请把组件名指向 org.cqutosp.hydros/.voice.VoiceCommandActivity，并在 extra 里传入 " +
                    VoiceCommands.EXTRA_COMMAND + "。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Section("在线试用") {
            FormField(value = testText, onValue = { testText = it }, label = "口令")
            PrimaryAction(
                label = "执行口令",
                icon = R.drawable.ic_mic,
                enabled = testText.isNotBlank() && enabled,
                disabledReason = if (enabled) "先输入一句口令" else "请先开启语音助手",
                onClick = { onTest(testText.trim()) },
            )
        }
    }
}

@Composable
private fun StepRow(index: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SurfacePill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .hydrosPressable(onClick = onClick)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun DeepLinkBlock(link: String) {
    Text(
        link,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
