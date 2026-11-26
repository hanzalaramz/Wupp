package com.wa.beastclient.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class WuppThemeMode(
    val storageKey: String,
    val label: String,
    val tagline: String
) {
    LIGHT("light", "Light", "Crisp daylight white & emerald"),
    DARK("dark", "Dark", "Classic slate charcoal"),
    AMOLED("amoled", "AMOLED", "True #000000 OLED pitch black"),
    AURORA("aurora", "Velvet Aurora", "Midnight indigo & glowing mint"),
    SYNTHWAVE("synthwave", "Synthwave", "Retro neon sunset & magenta"),
    SAKURA("sakura", "Sakura", "Soft cherry blossom daylight"),
    NOIR_GOLD("noir_gold", "Noir & Gold", "Obsidian black & warm 24k gold"),
    PAPER_INK("paper_ink", "Paper & Ink", "Warm editorial cream & forest"),
    MATCHA("matcha", "Matcha", "Botanical sage green daylight"),
    GLACIER("glacier", "Glacier", "Arctic frost slate & ice cyan"),
    IOS("ios", "iOS Style", "Apple iOS blue accents & clean light layout");

    companion object {
        fun fromStorageKey(key: String?, legacyDarkFallback: Boolean): WuppThemeMode {
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) }?.let { return it }
            return if (legacyDarkFallback) AMOLED else LIGHT
        }
    }
}

enum class WuppFontStyle(
    val storageKey: String,
    val label: String,
    val subtitle: String
) {
    ROBO_CLEAN("roboto", "Clean Sans", "Balanced modern typeface"),
    SYSTEM("system", "Device System", "Follow your Android phone font"),
    SERIF_EDITORIAL("serif", "Editorial Serif", "Crisp literary serif"),
    MONO_TECH("mono", "Tech Mono", "Fixed-width developer style"),
    CUSTOM("custom", "Imported Font", "Your own .ttf or .otf file");

    companion object {
        fun fromStorageKey(key: String?, legacyUseSystemFont: Boolean = false): WuppFontStyle {
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) }?.let { return it }
            return if (legacyUseSystemFont) SYSTEM else ROBO_CLEAN
        }
    }
}

enum class WuppClockFormat(
    val storageKey: String,
    val label: String,
    val subtitle: String
) {
    SYSTEM("system", "Follow device setting", "Match your phone's 12-hour or 24-hour clock"),
    HOUR_24("24h", "24-hour", "14:30"),
    HOUR_12("12h", "12-hour", "2:30 PM");

    companion object {
        fun fromStorageKey(key: String?): WuppClockFormat =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

enum class WuppAccentColor(
    val storageKey: String,
    val label: String,
    val accentArgb: Long,
    val brightArgb: Long
) {
    DEFAULT("default", "Theme Default", 0L, 0L),
    EMERALD("emerald", "Emerald", 0xFF00A884L, 0xFF25D366L),
    OCEAN("ocean", "Ocean Blue", 0xFF2563EBL, 0xFF3B82F6L),
    VIOLET("violet", "Royal Violet", 0xFF7C3AEDL, 0xFFA855F7L),
    ROSE("rose", "Crimson Rose", 0xFFE11D48L, 0xFFF43F5EL),
    AMBER("amber", "Sunset Gold", 0xFFD97706L, 0xFFF59E0BL),
    CYAN("cyan", "Cyber Cyan", 0xFF0891B2L, 0xFF06B6D4L),
    PINK("pink", "Neon Pink", 0xFFDB2777L, 0xFFEC4899L);

    companion object {
        fun fromStorageKey(key: String?): WuppAccentColor =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

internal fun blendRgb(base: Color, tint: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    val inv = 1f - t
    return Color(
        red = base.red * inv + tint.red * t,
        green = base.green * inv + tint.green * t,
        blue = base.blue * inv + tint.blue * t,
        alpha = 1f
    )
}

internal fun shouldPreferDarkContentOnLuminance(luminance: Float): Boolean {
    val l = luminance.coerceIn(0f, 1f)
    val darkContrast = (l + 0.05f) / 0.0566f // #0B141A relative luminance ~ 0.0066
    val lightContrast = 1.05f / (l + 0.05f)  // #FFFFFF relative luminance = 1.0
    return darkContrast >= lightContrast
}

/** Picks dark or light ink automatically so FAB/badge icons always achieve the highest contrast. */
fun Color.contrastingContentColor(): Color =
    if (shouldPreferDarkContentOnLuminance(luminance())) Color(0xFF0B141A) else Color.White

/** Returns Android 12+ wallpaper-derived Material You palette when supported by this OS version. */
fun resolvePlatformDynamicColorScheme(context: Context, isDark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

/** Maps a Material 3 [ColorScheme] (including Material You wallpaper palettes) across backgrounds, buttons, bubbles, and pills. */
internal fun applyDynamicColorSchemeToWaColors(
    base: WaColors,
    scheme: ColorScheme
): WaColors {
    val richButtonAccent = if (base.isDark) {
        val raw = blendRgb(scheme.primary, scheme.onPrimary, 0.38f)
        if (raw.luminance() > 0.30f) blendRgb(raw, scheme.onPrimary, 0.25f) else raw
    } else {
        scheme.primary
    }
    return base.copy(
        chatCanvas = if (base.isDark) {
            blendRgb(scheme.surfaceContainerLowest, scheme.primaryContainer, 0.08f)
        } else {
            blendRgb(scheme.surfaceContainerLow, scheme.primaryContainer, 0.20f)
        },
        appSurface = scheme.background,
        elevatedBar = scheme.surfaceContainerHigh,
        outgoingBubble = if (base.isDark) {
            blendRgb(scheme.primaryContainer, scheme.surfaceContainerLow, 0.12f)
        } else {
            blendRgb(scheme.primaryContainer, Color.White, 0.08f)
        },
        incomingBubble = if (base.isDark) scheme.surfaceContainer else scheme.surface,
        accentGreen = richButtonAccent,
        brightGreen = scheme.primary,
        blueTick = scheme.tertiary,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceVariant,
        pillActiveBg = scheme.primaryContainer,
        pillActiveText = scheme.onPrimaryContainer,
        pillInactiveBg = scheme.surfaceContainer,
        antiDeleteRed = scheme.error,
        divider = scheme.outlineVariant,
        doodleColor = scheme.onSurface
    )
}

@Immutable
data class WaColors(
    val themeMode: WuppThemeMode,
    val isDark: Boolean,
    val chatCanvas: Color,
    val appSurface: Color,
    val elevatedBar: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val accentGreen: Color,
    val brightGreen: Color,
    val blueTick: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color,
    val pillInactiveBg: Color,
    val antiDeleteRed: Color,
    val warningAmber: Color,
    val quotedBg: Color,
    val divider: Color,
    val mediaScrim: Color,
    val doodleColor: Color,
    val doodleTintAlpha: Float
)

val WaDarkColors = WaColors(
    themeMode = WuppThemeMode.DARK,
    isDark = true,
    chatCanvas = Color(0xFF0B0E11),
    appSurface = Color(0xFF0B0E11),
    elevatedBar = Color(0xFF171C20),
    outgoingBubble = Color(0xFF144D37),
    incomingBubble = Color(0xFF1F2428),
    accentGreen = Color(0xFF00A884),
    brightGreen = Color(0xFF25D366),
    blueTick = Color(0xFF53BDEB),
    textPrimary = Color(0xFFF3F6F8),
    textSecondary = Color(0xFF97A1A8),
    pillActiveBg = Color(0xFF0A332C),
    pillActiveText = Color(0xFF25D366),
    pillInactiveBg = Color(0xFF1C2328),
    antiDeleteRed = Color(0xFFF15C6D),
    warningAmber = Color(0xFFFFB74D),
    quotedBg = Color(0x45000000),
    divider = Color(0xFF1D242A),
    mediaScrim = Color(0x66000000),
    doodleColor = Color.White,
    doodleTintAlpha = 0.068f
)

val WaLightColors = WaColors(
    themeMode = WuppThemeMode.LIGHT,
    isDark = false,
    chatCanvas = Color(0xFFEFEAE2),
    appSurface = Color(0xFFFFFFFF),
    elevatedBar = Color(0xFFF0F2F5),
    outgoingBubble = Color(0xFFD9FDD3),
    incomingBubble = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF008069),
    brightGreen = Color(0xFF1DAA61),
    blueTick = Color(0xFF53BDEB),
    textPrimary = Color(0xFF111B21),
    textSecondary = Color(0xFF667781),
    pillActiveBg = Color(0xFFD8FDD2),
    pillActiveText = Color(0xFF15603E),
    pillInactiveBg = Color(0xFFF0F2F5),
    antiDeleteRed = Color(0xFFD32F2F),
    warningAmber = Color(0xFFE65100),
    quotedBg = Color(0x14000000),
    divider = Color(0xFFE9EDEF),
    mediaScrim = Color(0x66000000),
    doodleColor = Color(0xFF8C8275),
    doodleTintAlpha = 0.18f
)

val WaAmoledColors = WaColors(
    themeMode = WuppThemeMode.AMOLED,
    isDark = true,
    chatCanvas = Color(0xFF000000),
    appSurface = Color(0xFF000000),
    elevatedBar = Color(0xFF14181B),
    outgoingBubble = Color(0xFF134D37),
    incomingBubble = Color(0xFF1E2226),
    accentGreen = Color(0xFF00C49A),
    brightGreen = Color(0xFF25D366),
    blueTick = Color(0xFF53BDEB),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFF9AA3AB),
    pillActiveBg = Color(0xFF0A332B),
    pillActiveText = Color(0xFF2FE676),
    pillInactiveBg = Color(0xFF181C20),
    antiDeleteRed = Color(0xFFFF5C72),
    warningAmber = Color(0xFFFFB74D),
    quotedBg = Color(0x4D000000),
    divider = Color(0xFF1F2429),
    mediaScrim = Color(0x80000000),
    doodleColor = Color(0xFFCBD5E1),
    doodleTintAlpha = 0.055f
)

val WaAuroraColors = WaColors(
    themeMode = WuppThemeMode.AURORA,
    isDark = true,
    chatCanvas = Color(0xFF0A0D18),
    appSurface = Color(0xFF0D1120),
    elevatedBar = Color(0xFF161C32),
    outgoingBubble = Color(0xFF3E2978),
    incomingBubble = Color(0xFF1A223B),
    accentGreen = Color(0xFF00E5B8),
    brightGreen = Color(0xFF3DF5C8),
    blueTick = Color(0xFF70D6FF),
    textPrimary = Color(0xFFF1F4FF),
    textSecondary = Color(0xFF98A3C7),
    pillActiveBg = Color(0xFF231E4F),
    pillActiveText = Color(0xFF3DF5C8),
    pillInactiveBg = Color(0xFF171E35),
    antiDeleteRed = Color(0xFFFF6B8B),
    warningAmber = Color(0xFFFFC86B),
    quotedBg = Color(0x38000000),
    divider = Color(0xFF222A48),
    mediaScrim = Color(0x73050711),
    doodleColor = Color(0xFF9BB2FF),
    doodleTintAlpha = 0.075f
)

val WaSynthwaveColors = WaColors(
    themeMode = WuppThemeMode.SYNTHWAVE,
    isDark = true,
    chatCanvas = Color(0xFF130924),
    appSurface = Color(0xFF160B29),
    elevatedBar = Color(0xFF21123B),
    outgoingBubble = Color(0xFF831843),
    incomingBubble = Color(0xFF281747),
    accentGreen = Color(0xFFF43F5E),
    brightGreen = Color(0xFFFB7185),
    blueTick = Color(0xFF38BDF8),
    textPrimary = Color(0xFFFDF2F8),
    textSecondary = Color(0xFFC4B5FD),
    pillActiveBg = Color(0xFF4C0519),
    pillActiveText = Color(0xFFFDA4AF),
    pillInactiveBg = Color(0xFF281747),
    antiDeleteRed = Color(0xFFFF4D6D),
    warningAmber = Color(0xFFFBBF24),
    quotedBg = Color(0x38000000),
    divider = Color(0xFF3B1D63),
    mediaScrim = Color(0x73080212),
    doodleColor = Color(0xFFF472B6),
    doodleTintAlpha = 0.08f
)

val WaSakuraColors = WaColors(
    themeMode = WuppThemeMode.SAKURA,
    isDark = false,
    chatCanvas = Color(0xFFFFF5F7),
    appSurface = Color(0xFFFFFBFC),
    elevatedBar = Color(0xFFFDECEF),
    outgoingBubble = Color(0xFFFCDCE3),
    incomingBubble = Color(0xFFFFFFFF),
    accentGreen = Color(0xFFBE185D),
    brightGreen = Color(0xFFEC4899),
    blueTick = Color(0xFF0284C7),
    textPrimary = Color(0xFF311022),
    textSecondary = Color(0xFF83556B),
    pillActiveBg = Color(0xFFFCE7F3),
    pillActiveText = Color(0xFF9D174D),
    pillInactiveBg = Color(0xFFF9E6EC),
    antiDeleteRed = Color(0xFFDC2626),
    warningAmber = Color(0xFFD97706),
    quotedBg = Color(0x129D174D),
    divider = Color(0xFFF4D7E0),
    mediaScrim = Color(0x66000000),
    doodleColor = Color(0xFFBE185D),
    doodleTintAlpha = 0.08f
)

val WaNoirGoldColors = WaColors(
    themeMode = WuppThemeMode.NOIR_GOLD,
    isDark = true,
    chatCanvas = Color(0xFF0B0A08),
    appSurface = Color(0xFF0F0D0A),
    elevatedBar = Color(0xFF1C1812),
    outgoingBubble = Color(0xFF423212),
    incomingBubble = Color(0xFF1F1B14),
    accentGreen = Color(0xFFD9A441),
    brightGreen = Color(0xFFFBBF24),
    blueTick = Color(0xFFFCD34D),
    textPrimary = Color(0xFFFAF5EA),
    textSecondary = Color(0xFFB4A488),
    pillActiveBg = Color(0xFF2D230E),
    pillActiveText = Color(0xFFFDE68A),
    pillInactiveBg = Color(0xFF1E1A13),
    antiDeleteRed = Color(0xFFF87171),
    warningAmber = Color(0xFFF59E0B),
    quotedBg = Color(0x38000000),
    divider = Color(0xFF2B2418),
    mediaScrim = Color(0x80000000),
    doodleColor = Color(0xFFFBBF24),
    doodleTintAlpha = 0.06f
)

val WaPaperInkColors = WaColors(
    themeMode = WuppThemeMode.PAPER_INK,
    isDark = false,
    chatCanvas = Color(0xFFF4EFE6),
    appSurface = Color(0xFFFAF7F2),
    elevatedBar = Color(0xFFECE4D8),
    outgoingBubble = Color(0xFFE0EAD7),
    incomingBubble = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF2D5A27),
    brightGreen = Color(0xFF3E7B34),
    blueTick = Color(0xFF2563EB),
    textPrimary = Color(0xFF1F1B16),
    textSecondary = Color(0xFF6B6256),
    pillActiveBg = Color(0xFFDCE7D4),
    pillActiveText = Color(0xFF1E4619),
    pillInactiveBg = Color(0xFFEBE4D7),
    antiDeleteRed = Color(0xFFB91C1C),
    warningAmber = Color(0xFFB45309),
    quotedBg = Color(0x14000000),
    divider = Color(0xFFDFD5C5),
    mediaScrim = Color(0x66000000),
    doodleColor = Color(0xFF7D705F),
    doodleTintAlpha = 0.14f
)

val WaMatchaColors = WaColors(
    themeMode = WuppThemeMode.MATCHA,
    isDark = false,
    chatCanvas = Color(0xFFEAF2EA),
    appSurface = Color(0xFFF6FAF6),
    elevatedBar = Color(0xFFE1ECE1),
    outgoingBubble = Color(0xFFCDE7CC),
    incomingBubble = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF2E7D32),
    brightGreen = Color(0xFF43A047),
    blueTick = Color(0xFF0288D1),
    textPrimary = Color(0xFF112313),
    textSecondary = Color(0xFF556E57),
    pillActiveBg = Color(0xFFD2E9D1),
    pillActiveText = Color(0xFF1B5E20),
    pillInactiveBg = Color(0xFFE0ECE0),
    antiDeleteRed = Color(0xFFD32F2F),
    warningAmber = Color(0xFFEF6C00),
    quotedBg = Color(0x141B5E20),
    divider = Color(0xFFD0DFD0),
    mediaScrim = Color(0x66000000),
    doodleColor = Color(0xFF4D724F),
    doodleTintAlpha = 0.14f
)

val WaGlacierColors = WaColors(
    themeMode = WuppThemeMode.GLACIER,
    isDark = true,
    chatCanvas = Color(0xFF09131C),
    appSurface = Color(0xFF0B1620),
    elevatedBar = Color(0xFF122333),
    outgoingBubble = Color(0xFF0E4D64),
    incomingBubble = Color(0xFF152738),
    accentGreen = Color(0xFF06B6D4),
    brightGreen = Color(0xFF22D3EE),
    blueTick = Color(0xFF67E8F9),
    textPrimary = Color(0xFFECF8FF),
    textSecondary = Color(0xFF8AA8BE),
    pillActiveBg = Color(0xFF083344),
    pillActiveText = Color(0xFF67E8F9),
    pillInactiveBg = Color(0xFF152738),
    antiDeleteRed = Color(0xFFFB7185),
    warningAmber = Color(0xFFFBBF24),
    quotedBg = Color(0x38000000),
    divider = Color(0xFF1C3347),
    mediaScrim = Color(0x73020A12),
    doodleColor = Color(0xFF67E8F9),
    doodleTintAlpha = 0.07f
)

val WaiOSColors = WaColors(
    themeMode = WuppThemeMode.IOS,
    isDark = false,
    chatCanvas = Color(0xFFF2F2F7),
    appSurface = Color(0xFFFFFFFF),
    elevatedBar = Color(0xFFF6F6F6),
    outgoingBubble = Color(0xFFDCF7C5),
    incomingBubble = Color(0xFFFFFFFF),
    accentGreen = Color(0xFF007AFF),
    brightGreen = Color(0xFF007AFF),
    blueTick = Color(0xFF34C759),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF8E8E93),
    pillActiveBg = Color(0xFFE5F1FF),
    pillActiveText = Color(0xFF007AFF),
    pillInactiveBg = Color(0xFFE5E5EA),
    antiDeleteRed = Color(0xFFFF3B30),
    warningAmber = Color(0xFFFF9500),
    quotedBg = Color(0x14000000),
    divider = Color(0xFFC6C6C8),
    mediaScrim = Color(0x66000000),
    doodleColor = Color(0xFF8E8E93),
    doodleTintAlpha = 0.10f
)

fun colorsForTheme(
    mode: WuppThemeMode,
    accent: WuppAccentColor = WuppAccentColor.DEFAULT,
    pureBlackOled: Boolean = false,
    customOutgoingBubbleArgb: Long = 0L,
    customIncomingBubbleArgb: Long = 0L,
    dynamicScheme: ColorScheme? = null
): WaColors {
    val base = when (mode) {
        WuppThemeMode.LIGHT -> WaLightColors
        WuppThemeMode.DARK -> WaDarkColors
        WuppThemeMode.AMOLED -> WaAmoledColors
        WuppThemeMode.AURORA -> WaAuroraColors
        WuppThemeMode.SYNTHWAVE -> WaSynthwaveColors
        WuppThemeMode.SAKURA -> WaSakuraColors
        WuppThemeMode.NOIR_GOLD -> WaNoirGoldColors
        WuppThemeMode.PAPER_INK -> WaPaperInkColors
        WuppThemeMode.MATCHA -> WaMatchaColors
        WuppThemeMode.GLACIER -> WaGlacierColors
        WuppThemeMode.IOS -> WaiOSColors
    }
    var resolved = when {
        dynamicScheme != null -> applyDynamicColorSchemeToWaColors(base, dynamicScheme)
        accent == WuppAccentColor.DEFAULT -> base
        else -> {
            val accentColor = Color(accent.accentArgb)
            val brightColor = Color(accent.brightArgb)
            val outgoing = if (base.isDark) {
                blendRgb(base.appSurface, accentColor, 0.36f)
            } else {
                blendRgb(Color.White, brightColor, 0.22f)
            }
            val pillBg = if (base.isDark) {
                blendRgb(base.appSurface, accentColor, 0.24f)
            } else {
                blendRgb(Color.White, brightColor, 0.20f)
            }
            val pillText = if (base.isDark) brightColor else accentColor
            base.copy(
                accentGreen = accentColor,
                brightGreen = brightColor,
                outgoingBubble = outgoing,
                pillActiveBg = pillBg,
                pillActiveText = pillText
            )
        }
    }

    if (pureBlackOled) {
        // Pure Black OLED forces true #000000 canvases/bars and crisp high-contrast dark surfaces
        resolved = resolved.copy(
            isDark = true,
            chatCanvas = Color.Black,
            appSurface = Color.Black,
            elevatedBar = Color(0xFF07090B),
            incomingBubble = if (resolved.isDark) Color(0xFF101317) else Color(0xFF12161A),
            outgoingBubble = if (resolved.isDark) resolved.outgoingBubble else blendRgb(Color.Black, resolved.accentGreen, 0.38f),
            textPrimary = Color(0xFFF5F7F9),
            textSecondary = Color(0xFF8C98A4),
            pillActiveBg = blendRgb(Color.Black, resolved.accentGreen, 0.22f),
            pillActiveText = resolved.brightGreen,
            pillInactiveBg = Color(0xFF12161B),
            divider = Color(0xFF161B20),
            doodleTintAlpha = 0.045f
        )
    }

    if (customOutgoingBubbleArgb != 0L) {
        resolved = resolved.copy(outgoingBubble = Color(customOutgoingBubbleArgb))
    }
    if (customIncomingBubbleArgb != 0L) {
        resolved = resolved.copy(incomingBubble = Color(customIncomingBubbleArgb))
    }
    return resolved
}

val LocalWaColors = staticCompositionLocalOf { WaAmoledColors }
val LocalWaMonoFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
val LocalWuppShowSeconds = staticCompositionLocalOf { false }
val LocalWuppUse24Hour = staticCompositionLocalOf { false }
val LocalWuppChatFontScale = staticCompositionLocalOf { 1.0f }
val LocalWuppClassicUi = staticCompositionLocalOf { false }
val LocalWuppBubbleRadiusDp = staticCompositionLocalOf { 15 }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsAppTheme(
    themeMode: WuppThemeMode = WuppThemeMode.AMOLED,
    accentColor: WuppAccentColor = WuppAccentColor.DEFAULT,
    pureBlackOled: Boolean = false,
    dynamicColors: Boolean = false,
    useSystemFont: Boolean = false,
    fontStyle: WuppFontStyle = if (useSystemFont) WuppFontStyle.SYSTEM else WuppFontStyle.ROBO_CLEAN,
    customFontPath: String = "",
    bubbleCornerRadiusDp: Int = 15,
    customOutgoingBubbleArgb: Long = 0L,
    customIncomingBubbleArgb: Long = 0L,
    showSecondsInTimestamps: Boolean = false,
    use24HourFormat: Boolean = false,
    chatFontScale: Float = 1.0f,
    classicUi: Boolean = false,
    assetManager: AssetManager,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseIsDark = pureBlackOled || colorsForTheme(themeMode).isDark
    val dynamicScheme = remember(dynamicColors, baseIsDark, context) {
        if (dynamicColors) resolvePlatformDynamicColorScheme(context, baseIsDark) else null
    }
    val waColors = colorsForTheme(
        mode = themeMode,
        accent = accentColor,
        pureBlackOled = pureBlackOled,
        customOutgoingBubbleArgb = customOutgoingBubbleArgb,
        customIncomingBubbleArgb = customIncomingBubbleArgb,
        dynamicScheme = dynamicScheme
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !waColors.isDark
            controller.isAppearanceLightNavigationBars = !waColors.isDark
        }
    }

    val effectiveFontStyle = if (useSystemFont && fontStyle == WuppFontStyle.ROBO_CLEAN) {
        WuppFontStyle.SYSTEM
    } else {
        fontStyle
    }
    val sansFamily: FontFamily = remember(assetManager, effectiveFontStyle, customFontPath) {
        when (effectiveFontStyle) {
            WuppFontStyle.SYSTEM -> FontFamily.Default
            WuppFontStyle.SERIF_EDITORIAL -> FontFamily.Serif
            WuppFontStyle.MONO_TECH -> FontFamily.Monospace
            WuppFontStyle.CUSTOM -> {
                val f = customFontPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                if (f != null && f.exists() && f.length() > 0L) {
                    runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull() ?: FontFamily.SansSerif
                } else {
                    FontFamily.SansSerif
                }
            }
            WuppFontStyle.ROBO_CLEAN -> runCatching {
                FontFamily(Typeface.createFromAsset(assetManager, "fonts/Roboto-Medium.ttf"))
            }.getOrDefault(FontFamily.SansSerif)
        }
    }

    val typography = remember(sansFamily) {
        Typography(
            displaySmall = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.4).sp
            ),
            headlineMedium = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.3).sp
            ),
            titleLarge = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.2).sp
            ),
            titleMedium = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.5.sp,
                lineHeight = 22.sp
            ),
            labelLarge = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.5.sp,
                lineHeight = 20.5.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            labelSmall = TextStyle(
                fontFamily = sansFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFeatureSettings = "tnum"
            )
        )
    }

    val expressiveShapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
    }

    val m3Scheme = when {
        dynamicScheme != null -> dynamicScheme.copy(
            primary = waColors.accentGreen,
            onPrimary = waColors.accentGreen.contrastingContentColor(),
            primaryContainer = waColors.pillActiveBg,
            onPrimaryContainer = waColors.pillActiveText,
            secondary = waColors.brightGreen,
            onSecondary = waColors.brightGreen.contrastingContentColor(),
            secondaryContainer = waColors.elevatedBar,
            onSecondaryContainer = waColors.textPrimary,
            tertiary = waColors.blueTick,
            background = waColors.appSurface,
            surface = waColors.appSurface,
            surfaceVariant = waColors.elevatedBar,
            surfaceContainerLow = waColors.appSurface,
            surfaceContainer = waColors.pillInactiveBg,
            surfaceContainerHigh = waColors.elevatedBar,
            surfaceContainerHighest = waColors.pillInactiveBg,
            outline = waColors.divider,
            outlineVariant = waColors.divider.copy(alpha = 0.7f),
            onBackground = waColors.textPrimary,
            onSurface = waColors.textPrimary,
            onSurfaceVariant = waColors.textSecondary,
            error = waColors.antiDeleteRed
        )
        waColors.isDark -> darkColorScheme(
            primary = waColors.accentGreen,
            onPrimary = waColors.accentGreen.contrastingContentColor(),
            primaryContainer = waColors.pillActiveBg,
            onPrimaryContainer = waColors.pillActiveText,
            secondary = waColors.brightGreen,
            onSecondary = waColors.brightGreen.contrastingContentColor(),
            secondaryContainer = waColors.elevatedBar,
            onSecondaryContainer = waColors.textPrimary,
            tertiary = waColors.blueTick,
            background = waColors.appSurface,
            surface = waColors.appSurface,
            surfaceVariant = waColors.elevatedBar,
            surfaceContainerLow = waColors.appSurface,
            surfaceContainer = waColors.elevatedBar,
            surfaceContainerHigh = waColors.incomingBubble,
            surfaceContainerHighest = waColors.pillInactiveBg,
            outline = waColors.divider,
            outlineVariant = waColors.divider.copy(alpha = 0.65f),
            onBackground = waColors.textPrimary,
            onSurface = waColors.textPrimary,
            onSurfaceVariant = waColors.textSecondary,
            error = waColors.antiDeleteRed
        )
        else -> lightColorScheme(
            primary = waColors.accentGreen,
            onPrimary = waColors.accentGreen.contrastingContentColor(),
            primaryContainer = waColors.pillActiveBg,
            onPrimaryContainer = waColors.pillActiveText,
            secondary = waColors.brightGreen,
            onSecondary = waColors.brightGreen.contrastingContentColor(),
            secondaryContainer = waColors.elevatedBar,
            onSecondaryContainer = waColors.textPrimary,
            tertiary = waColors.blueTick,
            background = waColors.appSurface,
            surface = waColors.appSurface,
            surfaceVariant = waColors.elevatedBar,
            surfaceContainerLow = waColors.appSurface,
            surfaceContainer = waColors.elevatedBar,
            surfaceContainerHigh = Color(0xFFE7ECEF),
            surfaceContainerHighest = waColors.pillInactiveBg,
            outline = waColors.divider,
            outlineVariant = waColors.divider.copy(alpha = 0.7f),
            onBackground = waColors.textPrimary,
            onSurface = waColors.textPrimary,
            onSurfaceVariant = waColors.textSecondary,
            error = waColors.antiDeleteRed
        )
    }

    CompositionLocalProvider(
        LocalWaColors provides waColors,
        LocalWaMonoFamily provides sansFamily,
        LocalWuppShowSeconds provides showSecondsInTimestamps,
        LocalWuppUse24Hour provides use24HourFormat,
        LocalWuppChatFontScale provides chatFontScale.coerceIn(0.85f, 1.25f),
        LocalWuppClassicUi provides classicUi,
        LocalWuppBubbleRadiusDp provides bubbleCornerRadiusDp.coerceIn(4, 24)
    ) {
        MaterialExpressiveTheme(
            colorScheme = m3Scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = expressiveShapes,
            typography = typography,
            content = content
        )
    }
}
