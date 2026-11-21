package org.cqutosp.hydros.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
    /** 降采样后的整屏底图（磨砂 + 折射取样都基于它）。 */
    var image: ImageBitmap? = null
    var imageScale: Float = 1f
    var imageAt: Long = 0L
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
    state: LiquidBackdropState? = null,
    origin: Offset = Offset.Zero,
) {
    clipPath(shape) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(brandStart, Color.White, if (dark) 0.30f else 0.62f).copy(alpha = 0.88f),
                    brandStart.copy(alpha = 0.82f),
                    lerp(brandEnd, Color.Black, 0.18f).copy(alpha = 0.86f)
                ),
                center = Offset(size.width * 0.34f, size.height * 0.28f),
                radius = size.maxDimension * 0.9f
            ),
            size = size
        )
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.30f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )
}


/** 采样层降采样后的“磨砂底图”：真机与模拟器都不依赖 RenderEffect。 */
fun LiquidBackdropState.hasImage(): Boolean = image != null

suspend fun LiquidBackdropState.captureBlurred(layer: GraphicsLayer, downsample: Int = 5) {
    val full = runCatching { layer.toImageBitmap() }.getOrNull() ?: return
    val src = full.asAndroidBitmap()
    if (src.width < 16 || src.height < 16) return
    val w = (src.width / downsample).coerceAtLeast(1)
    val h = (src.height / downsample).coerceAtLeast(1)
    // 先降采样，再连续做三轮「缩小-放大」双线性重采样：等效于高斯模糊，
    // 比 RenderEffect 更稳（API 31 上 BlurEffect 会被静默丢弃）。
    var soft = Bitmap.createScaledBitmap(src, w, h, true)
    repeat(4) {
        val bw = (soft.width / 2).coerceAtLeast(1)
        val bh = (soft.height / 2).coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(soft, bw, bh, true)
        soft = Bitmap.createScaledBitmap(tiny, soft.width, soft.height, true)
    }
    image = soft.asImageBitmap()
    imageScale = src.width.toFloat() / w.toFloat()
    imageAt = android.os.SystemClock.uptimeMillis()
}

/**
 * 把降采样底图按窗口坐标铺回玻璃内部；[magnify] 模拟透镜放大，
 * 让边缘的内容发生位移，形成 iOS 26 的 lensing 光学位移。
 */
private fun DrawScope.drawBackdropImage(
    state: LiquidBackdropState?,
    origin: Offset,
    magnify: Float,
    pivot: Offset,
) {
    val image = state?.image ?: return
    val scale = state.imageScale
    val fullWidth = image.width * scale
    val fullHeight = image.height * scale
    val dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt())
    val dstSize = IntSize(fullWidth.roundToInt(), fullHeight.roundToInt())
    withTransform({
        if (magnify != 1f) {
            translate(pivot.x, pivot.y)
            scale(magnify, magnify, pivot)
            translate(-pivot.x, -pivot.y)
        }
    }) {
        drawImage(
            image = image,
            dstOffset = dstOffset,
            dstSize = dstSize,
            alpha = 1f,
            colorFilter = COLOR_VIBRANCY,
            filterQuality = FilterQuality.High
        )
    }
}

/** 提亮 + 提饱和：让透过玻璃的内容比周围更“鲜活”，这是 vibrancy 的关键。 */
private val COLOR_VIBRANCY: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1.45f)
    }
)

/**
 * 悬浮玻璃胶囊：底图 + 染色 + 上缘镜面高光 + 下缘内阴影 + 色散边纹。
 * 液态玻璃开关关闭时退化为普通磨砂（只有乳白霜面与描边）。
 */
fun DrawScope.drawLiquidGlassPanel(
    state: LiquidBackdropState?,
    origin: Offset,
    shape: Path,
    dark: Boolean,
    liquid: Boolean,
    magnify: Float = 1.06f,
) {
    // 保持克制：一层半透明白霜 + 一条细描边就够了。
    // 之前叠了高光、色散、内阴影、斜向反光四层，屏幕上看反而像脏污/噪点。
    clipPath(shape) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.18f else if (liquid) 0.58f else 0.70f),
                1f to Color.White.copy(alpha = if (dark) 0.08f else if (liquid) 0.30f else 0.40f)
            ),
            size = size
        )
    }
    drawPath(
        path = shape,
        color = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 1.dp.toPx())
    )
}

/** 显示设置里的“液态玻璃”开关：关闭后底栏退化为普通磨砂玻璃。 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * 激活态的液滴药丸：主体是上亮下白的乳白玻璃，顶部一条弧形反光，
 * 下缘一圈柔和内阴影，边缘 0.8dp 高光描边——iOS 26 的 lensing 药丸。
 */
fun DrawScope.drawLiquidDroplet(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    dark: Boolean,
    glow: Float,
    state: LiquidBackdropState? = null,
    origin: Offset = Offset.Zero,
) {
    if (width <= 0f || height <= 0f) return
    val radius = height / 2f
    val rect = RoundRect(left, top, left + width, top + height, CornerRadius(radius))
    val body = Path().apply { addRoundRect(rect) }
    // 简洁版药丸：一层白色渐变 + 一条细边，不再画投影、顶部弧光与底部冷色折射。
    clipPath(body) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.16f else 0.46f),
                1f to Color.White.copy(alpha = if (dark) 0.06f else 0.24f)
            ),
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
    }
    drawPath(
        path = body,
        color = Color.White.copy(alpha = if (dark) 0.18f else 0.66f),
        style = Stroke(width = 0.8.dp.toPx())
    )
}

/**
 * 液态融合：药丸靠近相邻按钮时，在两者之间拉出一条柔和的白色液桥，
 * 用径向渐变模拟表面张力，避免出现生硬的矩形边界。
 */
fun DrawScope.drawLiquidFusion(
    fromX: Float,
    toX: Float,
    centerY: Float,
    height: Float,
    strength: Float,
    dark: Boolean,
) {
    if (strength <= 0.01f) return
    val alpha = (if (dark) 0.14f else 0.34f) * strength.coerceIn(0f, 1f)
    val left = minOf(fromX, toX)
    val right = maxOf(fromX, toX)
    val neck = height * 0.30f * (0.55f + 0.45f * strength)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha), Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)),
            startX = left,
            endX = right.coerceAtLeast(left + 1f)
        ),
        topLeft = Offset(left, centerY - neck / 2f),
        size = Size((right - left).coerceAtLeast(1f), neck),
        cornerRadius = CornerRadius(neck / 2f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = Offset(left, centerY),
            radius = height * 0.55f
        ),
        radius = height * 0.55f,
        center = Offset(left, centerY)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = Offset(right, centerY),
            radius = height * 0.55f
        ),
        radius = height * 0.55f,
        center = Offset(right, centerY)
    )
}

/** 药丸掠过按钮时的水波纹：一圈随距离淡出的细环。 */
fun DrawScope.drawLiquidRipple(
    centerX: Float,
    centerY: Float,
    height: Float,
    strength: Float,
    dark: Boolean,
) {
    if (strength <= 0.01f) return
    val s = strength.coerceIn(0f, 1f)
    drawCircle(
        color = Color.White.copy(alpha = (if (dark) 0.18f else 0.55f) * s),
        radius = height * (0.30f + 0.28f * s),
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.1.dp.toPx())
    )
}
