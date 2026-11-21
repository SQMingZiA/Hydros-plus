package org.cqutosp.hydros.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 液态玻璃的“真实背景”来源：页面内容被实时录进一个 [GraphicsLayer]，
 * 底部导航把它裁成胶囊后重绘（模糊 + 染色 + 高光），而不是伪造一层渐变。
 */
class LiquidBackdropState {
    var layer: GraphicsLayer? = null
    var origin: Offset = Offset.Zero
    var size: IntSize = IntSize.Zero
}

val LocalLiquidBackdrop = compositionLocalOf<LiquidBackdropState?> { null }

/** 底部玻璃占用的高度：滚动内容据此留出安全距离。 */
val LocalLiquidGlassInset = compositionLocalOf<Dp> { 84.dp }

/** 把这一层内容同时画到屏幕与 [state] 的采样层里。 */
fun Modifier.liquidBackdropSource(state: LiquidBackdropState): Modifier =
    drawWithContent {
        val target = state.layer
        if (target != null) {
            val w = size.width.roundToInt().coerceAtLeast(1)
            val h = size.height.roundToInt().coerceAtLeast(1)
            state.size = IntSize(w, h)
            target.record(density = this, layoutDirection = layoutDirection, size = IntSize(w, h)) {
                this@drawWithContent.drawContent()
            }
        }
        drawContent()
    }

/** 记录采样层在窗口中的位置。 */
fun Modifier.liquidBackdropOrigin(state: LiquidBackdropState): Modifier =
    onGloballyPositioned { state.origin = it.positionInWindow() }

fun capsulePath(width: Float, height: Float): Path = Path().apply {
    addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(height / 2f)))
}

fun circlePath(center: Offset, radius: Float): Path = Path().apply {
    addOval(Rect(center = center, radius = radius))
}

/**
 * 采样层：把页面内容按 [origin] 对齐后画进当前区域（交给外层 blur 模糊）。
 * 只负责“玻璃看到的背景”，不做染色与高光。
 */
fun DrawScope.drawLiquidBackdrop(
    state: LiquidBackdropState?,
    origin: Offset,
    shape: Path,
) {
    val layer = state?.layer ?: return
    val dx = origin.x - state.origin.x
    val dy = origin.y - state.origin.y
    clipPath(shape) {
        translate(left = -dx, top = -dy) { drawLayer(layer) }
    }
}

/** 玻璃染色：上亮下暗的厚度感 + 品牌色微光 + 上缘镜面高光 + 斜向反光。 */
fun DrawScope.drawLiquidGlassTint(
    shape: Path,
    tint: Color,
    tintTop: Color,
    edgeLight: Color,
    edgeShade: Color,
    brandGlow: Color = Color.Transparent,
) {
    clipPath(shape) {
        drawRect(
            brush = Brush.verticalGradient(0f to tintTop, 0.48f to tint, 1f to tint),
            size = size
        )
        if (brandGlow.alpha > 0f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(brandGlow, Color.Transparent),
                    center = Offset(size.width * 0.20f, size.height * 0.78f),
                    radius = size.maxDimension * 0.9f
                ),
                size = size
            )
        }
        drawRect(
            brush = Brush.verticalGradient(0.70f to Color.Transparent, 1f to edgeShade),
            size = size
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeLight, Color.Transparent)),
            start = Offset(size.width * 0.06f, 0.9.dp.toPx()),
            end = Offset(size.width * 0.94f, 0.9.dp.toPx()),
            strokeWidth = 1.2.dp.toPx()
        )
        drawLine(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.22f), Color.Transparent),
                start = Offset(size.width * 0.12f, 0f),
                end = Offset(size.width * 0.52f, size.height)
            ),
            start = Offset(size.width * 0.18f, 1.dp.toPx()),
            end = Offset(size.width * 0.34f, size.height * 0.6f),
            strokeWidth = 2.dp.toPx()
        )
    }
    drawPath(
        path = shape,
        brush = Brush.verticalGradient(
            0f to edgeLight,
            0.55f to edgeLight.copy(alpha = edgeLight.alpha * 0.40f),
            1f to edgeShade.copy(alpha = 0.85f)
        ),
        style = Stroke(width = 0.9.dp.toPx())
    )
}

/** 圆形玻璃的染色层（扫码球）：品牌渐变 + 左上环境反射 + 下缘内阴影。 */
fun DrawScope.drawLiquidGlassBallTint(
    shape: Path,
    center: Offset,
    radius: Float,
    brandStart: Color,
    brandEnd: Color,
    dark: Boolean,
) {
    clipPath(shape) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(brandStart, Color.White, if (dark) 0.22f else 0.55f),
                    brandStart,
                    lerp(brandEnd, Color.Black, 0.22f)
                ),
                center = Offset(size.width * 0.34f, size.height * 0.28f),
                radius = size.maxDimension * 0.9f
            ),
            size = size
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.34f), Color.Transparent),
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.45f),
                radius = radius * 0.9f
            ),
            radius = radius,
            center = center
        )
        drawArc(
            color = Color.Black.copy(alpha = 0.16f),
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - radius + 1.dp.toPx(), center.y - radius + 1.dp.toPx()),
            size = Size((radius - 1.dp.toPx()) * 2f, (radius - 1.dp.toPx()) * 2f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.65f)
                )
            ),
            startAngle = 195f,
            sweepAngle = 155f,
            useCenter = false,
            topLeft = Offset(center.x - radius + 1.2.dp.toPx(), center.y - radius + 1.2.dp.toPx()),
            size = Size((radius - 1.2.dp.toPx()) * 2f, (radius - 1.2.dp.toPx()) * 2f),
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.42f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )
}
