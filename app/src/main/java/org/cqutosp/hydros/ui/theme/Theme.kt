package org.cqutosp.hydros.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cqutosp.hydros.R
import org.cqutosp.hydros.data.storage.FeedbackSettings

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("亮色主题"), DARK("暗色主题") }

/** 应用字体选择：内置苹方或跟随系统。 */
enum class FontChoice(val label: String) {
    PINGFANG("苹方 PingFang"),
    SYSTEM("系统字体");

    companion object {
        fun fromKey(key: String?): FontChoice = entries.firstOrNull { it.name == key } ?: PINGFANG
    }
}

/**
 * iOS 字体组合：拉丁字母/数字用 SF Pro（大字号 Display、正文 Text），中文回退到苹方对应字重。
 * 每个字重内先放 SF Pro 再放苹方，缺字时由系统按顺序回退，得到与 iOS 一致的混排效果。
 */
data class HydrosFontSet(val display: FontFamily, val text: FontFamily)

/** 内置苹果字体（SF Pro + 苹方 Regular/Medium/Bold），不受系统主题字体影响。 */
val HydrosAppleDisplay: FontFamily = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.pingfang_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_regular, FontWeight.Medium),
    Font(R.font.pingfang_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.pingfang_medium, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.pingfang_bold, FontWeight.Bold),
)

val HydrosAppleText: FontFamily = FontFamily(
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.pingfang_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium),
    Font(R.font.pingfang_medium, FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.pingfang_bold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_semibold, FontWeight.Bold),
    Font(R.font.pingfang_bold, FontWeight.Bold),
)

val HydrosSystemFontSet = HydrosFontSet(FontFamily.SansSerif, FontFamily.SansSerif)

fun FontChoice.fontSet(): HydrosFontSet = when (this) {
    FontChoice.PINGFANG -> HydrosFontSet(HydrosAppleDisplay, HydrosAppleText)
    FontChoice.SYSTEM -> HydrosSystemFontSet
}

/** 兼容旧调用：返回正文字体族。 */
fun FontChoice.family(): FontFamily = fontSet().text

/**
 * 首页渐变背景预设。
 *
 * [start]/[end] 用于顶部标题区的色彩倾向，[blobMint]/[blobSky]/[blobLilac] 是三色光斑。
 */
enum class GradientPreset(
    val label: String,
    val start: Color,
    val end: Color,
    val blobMint: Color,
    val blobSky: Color,
    val blobLilac: Color,
) {
    DEFAULT("经典蓝", Color(0xFF2F6BEA), Color(0xFF1F55C9), Color(0xFFCFF3E4), Color(0xFFD6E4FF), Color(0xFFEDE4FF)),
    OCEAN("海雾青", Color(0xFF0EA5E9), Color(0xFF0B6FB8), Color(0xFFBDEBFF), Color(0xFFC9E2FF), Color(0xFFD9F2F2)),
    SUNSET("落日橙", Color(0xFFF97316), Color(0xFFD9480F), Color(0xFFFFE0CC), Color(0xFFFFD6E0), Color(0xFFFFE9C7)),
    FOREST("森林绿", Color(0xFF16A34A), Color(0xFF166534), Color(0xFFD7F2E1), Color(0xFFCDEDE9), Color(0xFFE6F0C9)),
    MONO("石墨灰", Color(0xFF4B5563), Color(0xFF1F2937), Color(0xFFE5E7EB), Color(0xFFE8EAED), Color(0xFFF0F1F3));

    companion object {
        fun fromKey(key: String?): GradientPreset = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

// Font-size preview must not rescale slider layout; gesture controls read this density instead.
val LocalSystemDensity = staticCompositionLocalOf { Density(1f) }

/** 当前首页渐变预设，由 [HydrosTheme] 提供，默认跟随外层或经典蓝。 */
val LocalHydrosGradient = staticCompositionLocalOf { GradientPreset.DEFAULT }

/** 是否启用首页渐变光斑背景。 */
val LocalHydrosGradientEnabled = staticCompositionLocalOf { true }

/** 当前自定义强调色，null 表示使用主题默认色。 */
val LocalHydrosAccent = staticCompositionLocalOf<Color?> { null }

/**
 * 设计令牌（参考微信/支付宝/Flutter 系统列表的“平铺列表”范式）。
 *
 * 特征：白底页面 + 16dp 边距 + 分组小标题 + 平铺行（56dp、发丝分隔线），
 * 不使用圆角卡片包裹内容；圆角只出现在输入框、弹层与小 chip 上；
 * 单一蓝色强调色，强调块用浅蓝底 + 主色竖条。
 */
object IosTokens {
    // 页面底色（平铺列表：白底为主，暗色深底）
    val Page = Color(0xFFFFFFFF)
    val PageDark = Color(0xFF0E1013)
    val BlobMint = Color(0xFFB9F0D2)
    val BlobSky = Color(0xFFBFD8FF)
    val BlobLilac = Color(0xFFE3D4FF)
    val BlobPeach = Color(0xFFFFE0CC)

    // 卡片与描边（企业级：白底、发丝描边、极轻投影）
    val Card = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF15181E)
    val CardBorder = Color(0xFFEDEFF3)
    val CardBorderDark = Color(0xFF23272F)
    val Hairline = Color(0xFFEFF1F4)
    val HairlineDark = Color(0xFF22252C)
    // 强调块：浅色底 + 主色左竖条（仅用于“正在使用”等少数块）
    val Emphasis = Color(0xFFF2F6FF)
    val EmphasisDark = Color(0xFF151C29);
    val SectionTitle = Color(0xFF8A9099)
    val SectionTitleDark = Color(0xFF8E95A2)
    val Glass = Color(0xE6FFFFFF)
    val GlassStrong = Color(0xF5FFFFFF)
    val GlassDark = Color(0xF2161920)
    val GlassBorder = Color(0xCCFFFFFF)
    val GlassBorderDark = Color(0xFF2A2F39)
    val GlassPressed = Color(0x99F1F3F8)

    // 强调色（单一主色，企业级克制用色）
    val Accent = Color(0xFF2F6BEA)
    val AccentDeep = Color(0xFF1F55C9)
    val AccentSoft = Color(0xFFEAF1FF)
    val Violet = Color(0xFF6D5BD0)
    val GradientStart = Color(0xFF2F6BEA)
    val GradientEnd = Color(0xFF1F55C9)

    // 文本
    val TextPrimary = Color(0xFF10131A)
    val TextSecondary = Color(0xFF6D7380)
    val TextTertiary = Color(0xFF9AA0AC)
    val DarkTextPrimary = Color(0xFFF2F4F9)
    val DarkTextSecondary = Color(0xFF9AA1AF)

    // 语义色
    val Success = Color(0xFF34C759)
    val SuccessSoft = Color(0xFFE4F8E9)
    val Warning = Color(0xFFFF9F0A)
    val WarningSoft = Color(0xFFFFF2DC)
    val Danger = Color(0xFFFF5A5F)
    val DangerSoft = Color(0xFFFFE9EA)

    // 首页玻璃卡片（iOS 参考：半透明白、乳白描边、柔和投影）
    val GlassFill = Color(0xE8FFFFFF)
    val GlassFillDark = Color(0xE61A1D24)
    val GlassStroke = Color(0xD9FFFFFF)
    val GlassStrokeDark = Color(0x24FFFFFF)
    val GlassShadow = Color(0x14101828)
    val GlassShadowStrong = Color(0x26101828)
    // 卡片在浅底上的可见边框（玻璃描边在浅底上会看不见）
    val GlassHairline = Color(0x14101828)
    // 首页背景底色：比纯白略冷一点，让白色玻璃卡浮起来
    val HomePage = Color(0xFFF4F6FB)
    val HomeMint = Color(0xFFCFF3E4)
    val HomeSky = Color(0xFFD6E4FF)
    val HomeLilac = Color(0xFFEDE4FF)
    val HomeMintDark = Color(0xFF123329)
    val HomeSkyDark = Color(0xFF142440)
    val HomeLilacDark = Color(0xFF241C3A)
    val FabStart = Color(0xFF5B9BFF)
    val FabEnd = Color(0xFF8664FF)
    // 液态玻璃面：上缘高光 + 内缘乳白描边，让半透明胶囊有厚度与折射感
    val GlassHighlight = Color(0x8CFFFFFF)
    val GlassHighlightDark = Color(0x1FFFFFFF)
    val GlassInnerStroke = Color(0x59FFFFFF)
    // 设备使用页：延续长按按钮的 primary 蓝，向深水色过渡
    val SessionTop = Color(0xFF2F6BEA)
    val SessionDeep = Color(0xFF0B4F9C)

    // 圆角：只给输入框/弹层/小 chip，列表与页面不用圆角容器
    val CardRadius = 12.dp
    val TileRadius = 8.dp
    val ControlRadius = 8.dp
    val SheetRadius = 16.dp
    val PillRadius = 999.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF2F6BEA), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FF), onPrimaryContainer = Color(0xFF173A82),
    secondary = Color(0xFF6D5BD0), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEAFB), onSecondaryContainer = Color(0xFF31275F),
    tertiary = Color(0xFF34C759), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4F8E9), onTertiaryContainer = Color(0xFF0F5B27),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF111318),
    surface = Color.White, onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF4F6F9), onSurfaceVariant = Color(0xFF6D7380),
    surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFEFF2F8), surfaceContainerHighest = Color(0xFFE6EAF2),
    outline = Color(0xFFDDE1E7), outlineVariant = Color(0xFFECEFF3),
    error = Color(0xFFFF5A5F), onError = Color.White,
    errorContainer = Color(0xFFFFE9EA), onErrorContainer = Color(0xFF7A1B1E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7BA8FF), onPrimary = Color(0xFF0A1A33),
    primaryContainer = Color(0xFF24314A), onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFFB79CFF), onSecondary = Color(0xFF1E1440),
    secondaryContainer = Color(0xFF2C2547), onSecondaryContainer = Color(0xFFE6DCFF),
    tertiary = Color(0xFF4CD97A), onTertiary = Color(0xFF06260F),
    tertiaryContainer = Color(0xFF163A24), onTertiaryContainer = Color(0xFFCFF5DA),
    background = Color(0xFF0E1013), onBackground = Color(0xFFF2F4F9),
    surface = Color(0xFF0E1013), onSurface = Color(0xFFF2F4F9),
    surfaceVariant = Color(0xFF1A1D24), onSurfaceVariant = Color(0xFF969CAA),
    surfaceContainer = Color(0xFF15181E), surfaceContainerLow = Color(0xFF0E1013),
    surfaceContainerHigh = Color(0xFF1E222B), surfaceContainerHighest = Color(0xFF262B36),
    outline = Color(0xFF333844), outlineVariant = Color(0xFF262B35),
    error = Color(0xFFFF6B70), onError = Color(0xFF2A0508),
    errorContainer = Color(0xFF4A1B1E), onErrorContainer = Color(0xFFFFB3B5),
)

// iOS 字阶：大标题紧字距、字重偏重，正文舒展。
private val HydrosTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-1).sp, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.8).sp, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.6).sp, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.5).sp, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.4).sp, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.3).sp, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.2).sp, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = (-0.1).sp, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.2.sp, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.2.sp, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** 把整套字阶替换为指定字体：大标题用 Display 字族，正文用 Text 字族，字号/行高/字重保持不变。 */
fun hydrosTypography(fontSet: HydrosFontSet, base: Typography): Typography {
    val display = fontSet.display
    val text = fontSet.text
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = base.headlineSmall.copy(fontFamily = display),
        titleLarge = base.titleLarge.copy(fontFamily = text),
        titleMedium = base.titleMedium.copy(fontFamily = text),
        titleSmall = base.titleSmall.copy(fontFamily = text),
        bodyLarge = base.bodyLarge.copy(fontFamily = text),
        bodyMedium = base.bodyMedium.copy(fontFamily = text),
        bodySmall = base.bodySmall.copy(fontFamily = text),
        labelLarge = base.labelLarge.copy(fontFamily = text),
        labelMedium = base.labelMedium.copy(fontFamily = text),
        labelSmall = base.labelSmall.copy(fontFamily = text),
    )
}

@Deprecated("改用 hydrosTypography(HydrosFontSet, Typography)")
fun hydrosTypography(fontFamily: FontFamily, base: Typography): Typography =
    hydrosTypography(HydrosFontSet(fontFamily, fontFamily), base)

@Composable
fun HydrosTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    feedback: FeedbackSettings = FeedbackSettings(),
    fontSizeScale: Float = 1f,
    fontSet: HydrosFontSet = HydrosSystemFontSet,
    accent: Color? = null,
    gradientPreset: GradientPreset = LocalHydrosGradient.current,
    gradientEnabled: Boolean = LocalHydrosGradientEnabled.current,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val systemDensity = LocalDensity.current
    val density = Density(systemDensity.density, systemDensity.fontScale * fontSizeScale)
    val resolvedAccent = accent ?: LocalHydrosAccent.current
    val baseScheme = if (dark) Dark else Light
    val scheme = if (resolvedAccent == null) baseScheme else baseScheme.copy(
        primary = resolvedAccent,
        onPrimary = if (resolvedAccent.luminance() > 0.55f) Color(0xFF10131A) else Color.White,
        primaryContainer = resolvedAccent.copy(alpha = if (dark) 0.24f else 0.12f),
        onPrimaryContainer = if (dark) Color.White else resolvedAccent,
        secondary = resolvedAccent,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = hydrosTypography(fontSet, HydrosTypography),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(10.dp),
            large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(16.dp),
        ),
        content = { CompositionLocalProvider(
            LocalFeedbackSettings provides feedback,
            LocalHydrosAccent provides resolvedAccent,
            LocalHydrosGradient provides gradientPreset,
            LocalHydrosGradientEnabled provides gradientEnabled,
            LocalSystemDensity provides systemDensity,
            LocalDensity provides density,
            content = content,
        ) },
    )
}