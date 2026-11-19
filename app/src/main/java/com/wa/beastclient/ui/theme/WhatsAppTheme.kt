package com.wa.beastclient.ui.theme

import android.app.Activity
import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    DARK("dark", "Dark", "Classic WhatsApp slate charcoal"),
    AMOLED("amoled", "AMOLED", "True #000000 OLED pitch black"),
    AURORA("aurora", "Velvet Aurora", "Midnight indigo & glowing mint");

    companion object {
        fun fromStorageKey(key: String?, legacyDarkFallback: Boolean): WuppThemeMode {
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) }?.let { return it }
            return if (legacyDarkFallback) DARK else LIGHT
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
    chatCanvas = Color(0xFF0B141A),
    appSurface = Color(0xFF0B141A),
    elevatedBar = Color(0xFF1F2C34),
    outgoingBubble = Color(0xFF005C4B),
    incomingBubble = Color(0xFF202C33),
    accentGreen = Color(0xFF00A884),
    brightGreen = Color(0xFF25D366),
    blueTick = Color(0xFF53BDEB),
    textPrimary = Color(0xFFE9EDEF),
    textSecondary = Color(0xFF8696A0),
    pillActiveBg = Color(0xFF0A332C),
    pillActiveText = Color(0xFF25D366),
    pillInactiveBg = Color(0xFF202C33),
    antiDeleteRed = Color(0xFFF15C6D),
    warningAmber = Color(0xFFFFB74D),
    quotedBg = Color(0x28000000),
    divider = Color(0xFF1E2A30),
    mediaScrim = Color(0x66000000),
    doodleColor = Color.White,
    doodleTintAlpha = 0.065f
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
    elevatedBar = Color(0xFF0B0F12),
    outgoingBubble = Color(0xFF004D40),
    incomingBubble = Color(0xFF12171B),
    accentGreen = Color(0xFF00C49A),
    brightGreen = Color(0xFF25D366),
    blueTick = Color(0xFF53BDEB),
    textPrimary = Color(0xFFF3F6F8),
    textSecondary = Color(0xFF82919C),
    pillActiveBg = Color(0xFF072B24),
    pillActiveText = Color(0xFF2FE676),
    pillInactiveBg = Color(0xFF12171B),
    antiDeleteRed = Color(0xFFFF5C72),
    warningAmber = Color(0xFFFFB74D),
    quotedBg = Color(0x38000000),
    divider = Color(0xFF182026),
    mediaScrim = Color(0x80000000),
    doodleColor = Color(0xFFCBD5E1),
    doodleTintAlpha = 0.045f
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

fun colorsForTheme(
    mode: WuppThemeMode,
    accent: WuppAccentColor = WuppAccentColor.DEFAULT
): WaColors {
    val base = when (mode) {
        WuppThemeMode.LIGHT -> WaLightColors
        WuppThemeMode.DARK -> WaDarkColors
        WuppThemeMode.AMOLED -> WaAmoledColors
        WuppThemeMode.AURORA -> WaAuroraColors
    }
    if (accent == WuppAccentColor.DEFAULT) return base
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
    return base.copy(
        accentGreen = accentColor,
        brightGreen = brightColor,
        outgoingBubble = outgoing,
        pillActiveBg = pillBg,
        pillActiveText = pillText
    )
}

val LocalWaColors = staticCompositionLocalOf { WaDarkColors }
val LocalWaMonoFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
val LocalWuppShowSeconds = staticCompositionLocalOf { false }
val LocalWuppUse24Hour = staticCompositionLocalOf { false }
val LocalWuppChatFontScale = staticCompositionLocalOf { 1.0f }
val LocalWuppClassicUi = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsAppTheme(
    themeMode: WuppThemeMode = WuppThemeMode.DARK,
    accentColor: WuppAccentColor = WuppAccentColor.DEFAULT,
    useSystemFont: Boolean = false,
    showSecondsInTimestamps: Boolean = false,
    use24HourFormat: Boolean = false,
    chatFontScale: Float = 1.0f,
    classicUi: Boolean = false,
    assetManager: AssetManager,
    content: @Composable () -> Unit
) {
    val waColors = colorsForTheme(themeMode, accentColor)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !waColors.isDark
            controller.isAppearanceLightNavigationBars = !waColors.isDark
        }
    }

    val sansFamily: FontFamily = remember(assetManager, useSystemFont) {
        if (useSystemFont) {
            FontFamily.Default
        } else {
            runCatching {
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

    val m3Scheme = if (waColors.isDark) {
        darkColorScheme(
            primary = waColors.accentGreen,
            onPrimary = if (waColors.themeMode == WuppThemeMode.AURORA) Color(0xFF06131F) else Color.White,
            primaryContainer = waColors.pillActiveBg,
            onPrimaryContainer = waColors.pillActiveText,
            secondary = waColors.brightGreen,
            onSecondary = Color(0xFF061310),
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
    } else {
        lightColorScheme(
            primary = waColors.accentGreen,
            onPrimary = Color.White,
            primaryContainer = waColors.pillActiveBg,
            onPrimaryContainer = waColors.pillActiveText,
            secondary = waColors.brightGreen,
            onSecondary = Color.White,
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
        LocalWuppClassicUi provides classicUi
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
