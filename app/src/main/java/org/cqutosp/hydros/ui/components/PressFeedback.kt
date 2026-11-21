package org.cqutosp.hydros.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import org.cqutosp.hydros.ui.theme.LocalFeedbackSettings

/**
 * 全站统一的按压反馈：按下时整体轻微缩小并降低不透明度，松开用弹簧回弹。
 * 只影响"按下去的手感"，不改变布局、不改变点击语义（可点击性、无障碍角色、testTag 都保持）。
 *
 * @param pressedScale 按下时的缩放比例，默认 0.965。
 * @param enabled 为 false 时不响应点击。
 * @param disableIndication 是否同时屏蔽 Material 水波纹（默认屏蔽，交给缩放反馈表达）。
 * @param onClickLabel 无障碍点击描述。
 */
fun Modifier.hydrosPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    disableIndication: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 开启「减少动画效果」后不再做缩放/透明度过渡，按压立即生效。
    val reduceMotion = LocalFeedbackSettings.current.reduceMotionEnabled
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "press-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) 0.86f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "press-alpha"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interaction,
            indication = if (disableIndication) null else LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}
