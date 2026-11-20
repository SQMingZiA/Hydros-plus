package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import org.cqutosp.hydros.R
import java.io.File
import org.cqutosp.hydros.ui.theme.IosTokens
import org.cqutosp.hydros.ui.theme.LocalHydrosGradient
import org.cqutosp.hydros.ui.theme.LocalHydrosGradientEnabled

/** True when the active color scheme is dark. */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * 页面底色：纯净的页面背景色，不再叠加光斑/渐变，避免与内容抢视觉。
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val dark = isDarkScheme()
    Box(modifier.fillMaxSize().background(if (dark) IosTokens.PageDark else IosTokens.Page))
}

/**
 * 轻量容器：仅在“需要强调/需要边界”的少数块使用（状态提示、主数字块、弹层内容）。
 * 白底 + 1dp 发丝描边 + 12dp 圆角，无投影；列表分组不再使用它。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(IosTokens.CardRadius),
    color: Color = Color.Unspecified,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isDarkScheme()
    val fill = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.surface
    Surface(
        modifier = modifier,
        shape = shape,
        color = fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border ?: BorderStroke(1.dp, if (dark) IosTokens.CardBorderDark else IosTokens.CardBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/**
 * 首页背景：纯色底 + 当前渐变预设的三色光斑（薄荷/天蓝/浅紫），向下淡出回底色。
 * 关闭渐变（LocalHydrosGradientEnabled = false）时只保留纯色页面。
 */
@Composable
fun HomeBackdrop(modifier: Modifier = Modifier) {
    val dark = isDarkScheme()
    val preset = LocalHydrosGradient.current
    val enabled = LocalHydrosGradientEnabled.current
    // 关闭渐变时回到纯色页面底色，不与首页标题区的光斑背景混用。
    val page = when {
        dark -> IosTokens.PageDark
        enabled -> IosTokens.HomePage
        else -> IosTokens.Page
    }
    val mint = preset.blobMint
    val sky = preset.blobSky
    val lilac = preset.blobLilac
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(page)
        if (!enabled) return@Canvas
        fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
        // 顶部一层预设在 start→end 之间的极淡色彩倾向，让首页与主题色一脉相承。
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    preset.start.copy(alpha = if (dark) 0.22f else 0.10f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.40f
            )
        )
        blob(mint, w * 0.04f, h * 0.02f, w * 0.95f, if (dark) 0.34f else 0.95f)
        blob(sky, w * 0.94f, h * 0.05f, w * 0.9f, if (dark) 0.3f else 0.9f)
        blob(lilac, w * 0.28f, h * 0.24f, w * 0.9f, if (dark) 0.18f else 0.36f)
        blob(sky, w * 0.86f, h * 0.46f, w * 0.7f, if (dark) 0.14f else 0.3f)
        // 底部再压一层页面底色，避免光斑一路拖到导航栏，保持列表区域的干净。
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, page, page),
                startY = h * 0.45f,
                endY = h * 0.98f
            )
        )
    }
}

/** 页面壁纸：图片铺满 + 压暗 + 模糊，未设置时回退到 [HomeBackdrop]。 */
@Composable
fun WallpaperBackdrop(
    path: String?,
    dim: Float = 0f,
    blur: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(path) {
        path?.takeIf { it.isNotBlank() }?.let { file -> decodeWallpaperBitmap(file) }
    }
    if (bitmap == null) {
        HomeBackdrop(modifier)
        return
    }
    val dark = isDarkScheme()
    val page = if (dark) IosTokens.PageDark else IosTokens.HomePage
    val radius = blur.coerceIn(0f, 40f)
    val dimAlpha = dim.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().background(page)) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 轻微放大，避免模糊后的边缘露出底色。
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
                .then(if (radius > 0f) Modifier.blur(radius.dp) else Modifier),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
        if (dimAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }
        // 顶部/底部轻压页面底色，保证标题与底部导航可读。
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        page.copy(alpha = if (dark) 0.45f else 0.28f),
                        Color.Transparent,
                        page.copy(alpha = if (dark) 0.30f else 0.16f)
                    ),
                )
            )
        )
    }
}

/**
 * 解码壁纸文件：大图按 2 的幂降采样到长边不超过 [maxSize]，避免整张原图解码占用几十 MB 内存。
 * 文件不存在或解码失败返回 null。
 */
fun decodeWallpaperBitmap(file: String, maxSize: Int = 2048): ImageBitmap? = runCatching {
    if (!File(file).exists()) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(file, options)?.asImageBitmap()
}.getOrNull()

/**
 * 首页玻璃卡片：半透明白底 + 乳白描边 + 柔和投影 + 20dp 圆角。
 * 只在首页/关键信息块使用，列表页仍走平铺行。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isDarkScheme()
    val haptics = rememberHydrosHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = if (dark) IosTokens.GlassShadow else IosTokens.GlassShadowStrong,
                spotColor = if (dark) IosTokens.GlassShadow else IosTokens.GlassShadowStrong
            )
            .clip(shape)
            .background(if (dark) IosTokens.GlassFillDark else IosTokens.GlassFill)
            .then(
                if (onClick != null) Modifier.clickable { haptics.light(); onClick() } else Modifier
            )
            .border(1.dp, if (dark) IosTokens.GlassStrokeDark else IosTokens.GlassHairline, shape)
            .padding(contentPadding),
        content = content
    )
}

/** 首页分组标题：16sp 半粗黑标题，右侧可选“更多”文字 + 箭头（iOS 参考里的 Upcoming Meetings 行）。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Glyph(
                R.drawable.ic_chevron,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 次级图标按钮：透明底，按下时一层极淡填充（返回、刷新、更多）。 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    description: String? = null,
    content: @Composable () -> Unit
) {
    val haptics = rememberHydrosHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = description
            ) { haptics.light(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProviderHelper(content)
    }
}

@Composable
private fun CompositionLocalProviderHelper(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) { content() }
}

@Composable
fun SubtleDivider(modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = horizontalPadding),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/**
 * 分组标题 + 平铺内容：不套任何卡片容器，行与行之间由调用方插入 SubtleDivider()。
 * 分组标题 13sp 灰色、上间距 16dp，形成清晰的块间层次。
 */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isDarkScheme()
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (dark) IosTokens.SectionTitleDark else IosTokens.SectionTitle
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** 强调块：浅蓝底 + 左侧 3dp 主色竖条，只用于“正在使用”这类需要突出的一块。 */
@Composable
fun EmphasisBlock(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isDarkScheme()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(if (dark) IosTokens.EmphasisDark else IosTokens.Emphasis)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(
            Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/** 主视觉块：单色强调底，12dp 圆角，无投影（仅主数字/操作时使用）。 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(IosTokens.CardRadius)
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Standard brand header with icon card, app title and description. */
@Composable
fun AppBrandingHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_launcher_foreground,
    iconSize: Dp = 76.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (title.isNotBlank()) Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 全局壁纸配置：HydrosApp 在根部 provide，所有 [HydrosPage] 默认取用。 */
val LocalHydrosWallpaper = androidx.compose.runtime.compositionLocalOf<WallpaperConfig?> { null }

/** 页面壁纸配置：图片路径、压暗程度（0f..1f）与模糊半径（dp）。 */
data class WallpaperConfig(
    val path: String? = null,
    val dim: Float = 0f,
    val blur: Float = 0f,
)

/**
 * 页面外壳：浅色渐变底 + 顶部标题行（可选圆形返回按钮）+ 悬浮底部导航。
 * 标题层级：页面标题 24sp Bold；可选 header 插槽用于日期/辅助信息。
 */
@Composable
fun HydrosPage(
    title: String,
    onBack: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    bottomBar: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    background: (@Composable () -> Unit)? = null,
    wallpaper: WallpaperConfig? = LocalHydrosWallpaper.current,
    content: @Composable () -> Unit
) {
    val hasWallpaper = wallpaper?.path?.isNotBlank() == true
    Box(Modifier.fillMaxSize()) {
        when {
            background != null -> background()
            hasWallpaper -> WallpaperBackdrop(
                path = wallpaper?.path,
                dim = wallpaper?.dim ?: 0f,
                blur = wallpaper?.blur ?: 0f,
            )
            else -> LiquidBackground()
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (background != null || hasWallpaper) Color.Transparent else MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (header != null) header()
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (onBack != null) {
                            GlassIconButton(onClick = onBack, description = "返回", size = 36.dp) {
                                Glyph(R.drawable.ic_back, modifier = Modifier.size(22.dp))
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (titleContent != null) titleContent()
                            else if (title.isNotBlank()) Text(
                                text = title,
                                modifier = Modifier.testTag("page-title"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            modifier = Modifier.padding(start = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            bottomBar = bottomBar
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
        }
    }
}