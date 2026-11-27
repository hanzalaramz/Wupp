package com.wa.beastclient.domain.appearance

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeLayoutPreset(val key: String, val label: String, val subtitle: String) {
    CLASSIC("classic", "Classic", "Balanced header, docked bar & bottom New Chat"),
    IOS_INSPIRED("ios_inspired", "iOS-inspired", "Large title, inline search & floating glass pill"),
    COMPACT("compact", "Compact", "Dense rows, slim header & maximum chats on screen");

    companion object {
        fun fromKey(raw: String?): HomeLayoutPreset =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: IOS_INSPIRED
    }
}

enum class HomeAccentStyle(val key: String, val label: String, val darkColor: Color, val lightColor: Color) {
    EMERALD("emerald", "Wupp Mint", Color(0xFF25D366), Color(0xFF128C4A)),
    OCEAN("ocean", "Ocean", Color(0xFF38BDF8), Color(0xFF0284C7)),
    VIOLET("violet", "Iris", Color(0xFFA78BFA), Color(0xFF7C3AED)),
    AMBER("amber", "Amber", Color(0xFFFBBF24), Color(0xFFD97706)),
    CORAL("coral", "Coral", Color(0xFFFB7185), Color(0xFFE11D48));

    fun resolve(isDark: Boolean): Color = if (isDark) darkColor else lightColor

    companion object {
        fun fromKey(raw: String?): HomeAccentStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: EMERALD
    }
}

enum class HomeBadgeStyle(val key: String, val label: String) {
    SOLID_PILL("solid_pill", "Solid pill"),
    SOFT_TINT("soft_tint", "Soft tint"),
    MINIMAL_DOT("minimal_dot", "Dot only");

    companion object {
        fun fromKey(raw: String?): HomeBadgeStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SOLID_PILL
    }
}

enum class HomeSurfaceTone(val key: String, val label: String) {
    ADAPTIVE("adaptive", "Theme default"),
    OLED_PURE("oled_pure", "Pure black"),
    ELEVATED_CARDS("elevated_cards", "Soft cards");

    companion object {
        fun fromKey(raw: String?): HomeSurfaceTone =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: ADAPTIVE
    }
}

enum class HomeRowDensity(val key: String, val label: String, val verticalPadDp: Float) {
    COZY("cozy", "Cozy", 13f),
    STANDARD("standard", "Standard", 9.5f),
    COMPACT("compact", "Compact", 6f);

    companion object {
        fun fromKey(raw: String?): HomeRowDensity =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: STANDARD
    }
}

enum class HomeSectionKind(val key: String, val label: String) {
    SEARCH_BAR("search_bar", "Search bar"),
    FILTER_CHIPS("filter_chips", "Filter chips"),
    ARCHIVED_BANNER("archived_banner", "Archived row");

    companion object {
        val DEFAULT_ORDER = listOf(SEARCH_BAR, FILTER_CHIPS, ARCHIVED_BANNER)

        fun fromKey(raw: String?): HomeSectionKind? =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }
    }
}

enum class HomeNavStyle(val key: String, val label: String) {
    FLOATING_PILL("floating_pill", "Floating pill"),
    DOCKED_BAR("docked_bar", "Docked bar"),
    COMPACT_ICONS("compact_icons", "Slim icons");

    companion object {
        fun fromKey(raw: String?): HomeNavStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: FLOATING_PILL
    }
}

enum class HomeNewChatStyle(val key: String, val label: String) {
    TOP_BAR_ONLY("top_bar_only", "Top bar +"),
    FLOATING_CIRCLE("floating_circle", "Floating circle"),
    EXTENDED_PILL("extended_pill", "Extended pill"),
    BOTH("both", "Top bar + FAB");

    companion object {
        fun fromKey(raw: String?): HomeNewChatStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: TOP_BAR_ONLY
    }
}

data class HomeAppearanceConfig(
    val layoutPreset: HomeLayoutPreset = HomeLayoutPreset.IOS_INSPIRED,
    val accentStyle: HomeAccentStyle = HomeAccentStyle.EMERALD,
    val unreadBadgeStyle: HomeBadgeStyle = HomeBadgeStyle.SOLID_PILL,
    val surfaceTone: HomeSurfaceTone = HomeSurfaceTone.ADAPTIVE,
    val titleScale: Float = 1.0f,
    val subtitleScale: Float = 1.0f,
    val boldUnreadTitles: Boolean = true,
    val rowDensity: HomeRowDensity = HomeRowDensity.STANDARD,
    val avatarSizeDp: Int = 48,
    val showRowDividers: Boolean = false,
    val previewMaxLines: Int = 1,
    val sectionOrder: List<HomeSectionKind> = HomeSectionKind.DEFAULT_ORDER,
    val showSearchBar: Boolean = true,
    val showFilterChips: Boolean = true,
    val navBarStyle: HomeNavStyle = HomeNavStyle.FLOATING_PILL,
    val showNavLabels: Boolean = true,
    val newChatButtonStyle: HomeNewChatStyle = HomeNewChatStyle.TOP_BAR_ONLY,
    val reducedMotion: Boolean = false,
    val hiddenNavTabs: Set<Int> = emptySet()
)

fun presetDefaultsFor(layout: HomeLayoutPreset): HomeAppearanceConfig = when (layout) {
    HomeLayoutPreset.CLASSIC -> HomeAppearanceConfig(
        layoutPreset = HomeLayoutPreset.CLASSIC,
        accentStyle = HomeAccentStyle.EMERALD,
        unreadBadgeStyle = HomeBadgeStyle.SOLID_PILL,
        surfaceTone = HomeSurfaceTone.ADAPTIVE,
        titleScale = 1.0f,
        subtitleScale = 1.0f,
        boldUnreadTitles = true,
        rowDensity = HomeRowDensity.STANDARD,
        avatarSizeDp = 48,
        showRowDividers = true,
        previewMaxLines = 1,
        sectionOrder = listOf(
            HomeSectionKind.SEARCH_BAR,
            HomeSectionKind.FILTER_CHIPS,
            HomeSectionKind.ARCHIVED_BANNER
        ),
        showSearchBar = true,
        showFilterChips = true,
        navBarStyle = HomeNavStyle.DOCKED_BAR,
        showNavLabels = true,
        newChatButtonStyle = HomeNewChatStyle.FLOATING_CIRCLE,
        reducedMotion = false
    )

    HomeLayoutPreset.IOS_INSPIRED -> HomeAppearanceConfig(
        layoutPreset = HomeLayoutPreset.IOS_INSPIRED,
        accentStyle = HomeAccentStyle.EMERALD,
        unreadBadgeStyle = HomeBadgeStyle.SOLID_PILL,
        surfaceTone = HomeSurfaceTone.ADAPTIVE,
        titleScale = 1.0f,
        subtitleScale = 1.0f,
        boldUnreadTitles = true,
        rowDensity = HomeRowDensity.COZY,
        avatarSizeDp = 50,
        showRowDividers = false,
        previewMaxLines = 1,
        sectionOrder = listOf(
            HomeSectionKind.SEARCH_BAR,
            HomeSectionKind.FILTER_CHIPS,
            HomeSectionKind.ARCHIVED_BANNER
        ),
        showSearchBar = true,
        showFilterChips = true,
        navBarStyle = HomeNavStyle.FLOATING_PILL,
        showNavLabels = true,
        newChatButtonStyle = HomeNewChatStyle.TOP_BAR_ONLY,
        reducedMotion = false
    )

    HomeLayoutPreset.COMPACT -> HomeAppearanceConfig(
        layoutPreset = HomeLayoutPreset.COMPACT,
        accentStyle = HomeAccentStyle.EMERALD,
        unreadBadgeStyle = HomeBadgeStyle.SOFT_TINT,
        surfaceTone = HomeSurfaceTone.OLED_PURE,
        titleScale = 0.92f,
        subtitleScale = 0.92f,
        boldUnreadTitles = true,
        rowDensity = HomeRowDensity.COMPACT,
        avatarSizeDp = 42,
        showRowDividers = false,
        previewMaxLines = 1,
        sectionOrder = listOf(
            HomeSectionKind.FILTER_CHIPS,
            HomeSectionKind.SEARCH_BAR,
            HomeSectionKind.ARCHIVED_BANNER
        ),
        showSearchBar = true,
        showFilterChips = true,
        navBarStyle = HomeNavStyle.COMPACT_ICONS,
        showNavLabels = false,
        newChatButtonStyle = HomeNewChatStyle.TOP_BAR_ONLY,
        reducedMotion = false
    )
}

internal fun sanitizeSectionOrder(rawOrder: List<HomeSectionKind>): List<HomeSectionKind> {
    val deduped = rawOrder.distinct().toMutableList()
    for (kind in HomeSectionKind.DEFAULT_ORDER) {
        if (kind !in deduped) deduped.add(kind)
    }
    return deduped
}

internal fun moveSectionInOrder(
    order: List<HomeSectionKind>,
    kind: HomeSectionKind,
    delta: Int
): List<HomeSectionKind> {
    val clean = sanitizeSectionOrder(order).toMutableList()
    val idx = clean.indexOf(kind)
    if (idx < 0) return clean
    val target = (idx + delta).coerceIn(0, clean.lastIndex)
    if (target == idx) return clean
    clean.removeAt(idx)
    clean.add(target, kind)
    return clean
}

internal val VALID_NAV_TAB_IDS = setOf(0, 4, 1, 6, 2, 5, 3)

internal fun sanitizeHiddenNavTabs(raw: Set<Int>): Set<Int> {
    val filtered = raw.filterTo(LinkedHashSet()) { it in VALID_NAV_TAB_IDS }
    // Never allow every tab to be hidden; keep Chats (0) visible if all valid IDs were hidden.
    if (filtered.containsAll(VALID_NAV_TAB_IDS)) {
        filtered.remove(0)
    }
    return filtered
}

internal fun serializeHomeAppearance(config: HomeAppearanceConfig): String {
    val orderStr = sanitizeSectionOrder(config.sectionOrder).joinToString(",") { it.key }
    val hiddenTabsStr = sanitizeHiddenNavTabs(config.hiddenNavTabs).sorted().joinToString(",")
    return buildString {
        appendLine("layoutPreset=${config.layoutPreset.key}")
        appendLine("accentStyle=${config.accentStyle.key}")
        appendLine("unreadBadgeStyle=${config.unreadBadgeStyle.key}")
        appendLine("surfaceTone=${config.surfaceTone.key}")
        appendLine("titleScale=${config.titleScale}")
        appendLine("subtitleScale=${config.subtitleScale}")
        appendLine("boldUnreadTitles=${config.boldUnreadTitles}")
        appendLine("rowDensity=${config.rowDensity.key}")
        appendLine("avatarSizeDp=${config.avatarSizeDp}")
        appendLine("showRowDividers=${config.showRowDividers}")
        appendLine("previewMaxLines=${config.previewMaxLines}")
        appendLine("sectionOrder=$orderStr")
        appendLine("showSearchBar=${config.showSearchBar}")
        appendLine("showFilterChips=${config.showFilterChips}")
        appendLine("navBarStyle=${config.navBarStyle.key}")
        appendLine("showNavLabels=${config.showNavLabels}")
        appendLine("newChatButtonStyle=${config.newChatButtonStyle.key}")
        appendLine("reducedMotion=${config.reducedMotion}")
        append("hiddenNavTabs=$hiddenTabsStr")
    }
}

internal fun deserializeHomeAppearance(raw: String?): HomeAppearanceConfig {
    if (raw.isNullOrBlank()) return HomeAppearanceConfig()
    return runCatching {
        val map = raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
        val preset = HomeLayoutPreset.fromKey(map["layoutPreset"])
        val base = presetDefaultsFor(preset)
        val parsedOrder = map["sectionOrder"]
            ?.split(',')
            ?.mapNotNull { HomeSectionKind.fromKey(it.trim()) }
            .orEmpty()
        val parsedHiddenTabs = map["hiddenNavTabs"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        base.copy(
            accentStyle = HomeAccentStyle.fromKey(map["accentStyle"] ?: base.accentStyle.key),
            unreadBadgeStyle = HomeBadgeStyle.fromKey(map["unreadBadgeStyle"] ?: base.unreadBadgeStyle.key),
            surfaceTone = HomeSurfaceTone.fromKey(map["surfaceTone"] ?: base.surfaceTone.key),
            titleScale = map["titleScale"]?.toFloatOrNull()?.coerceIn(0.85f, 1.20f) ?: base.titleScale,
            subtitleScale = map["subtitleScale"]?.toFloatOrNull()?.coerceIn(0.85f, 1.15f) ?: base.subtitleScale,
            boldUnreadTitles = map["boldUnreadTitles"]?.toBooleanStrictOrNull() ?: base.boldUnreadTitles,
            rowDensity = HomeRowDensity.fromKey(map["rowDensity"] ?: base.rowDensity.key),
            avatarSizeDp = map["avatarSizeDp"]?.toIntOrNull()?.coerceIn(38, 58) ?: base.avatarSizeDp,
            showRowDividers = map["showRowDividers"]?.toBooleanStrictOrNull() ?: base.showRowDividers,
            previewMaxLines = map["previewMaxLines"]?.toIntOrNull()?.coerceIn(1, 2) ?: base.previewMaxLines,
            sectionOrder = sanitizeSectionOrder(if (parsedOrder.isEmpty()) base.sectionOrder else parsedOrder),
            showSearchBar = map["showSearchBar"]?.toBooleanStrictOrNull() ?: base.showSearchBar,
            showFilterChips = map["showFilterChips"]?.toBooleanStrictOrNull() ?: base.showFilterChips,
            navBarStyle = HomeNavStyle.fromKey(map["navBarStyle"] ?: base.navBarStyle.key),
            showNavLabels = map["showNavLabels"]?.toBooleanStrictOrNull() ?: base.showNavLabels,
            newChatButtonStyle = HomeNewChatStyle.fromKey(map["newChatButtonStyle"] ?: base.newChatButtonStyle.key),
            reducedMotion = map["reducedMotion"]?.toBooleanStrictOrNull() ?: base.reducedMotion,
            hiddenNavTabs = sanitizeHiddenNavTabs(parsedHiddenTabs)
        )
    }.getOrDefault(HomeAppearanceConfig())
}

/**
 * Persists Home Screen layout presets and visual customization preferences.
 */
object HomeAppearanceManager {
    private const val PREFS_NAME = "wupp_home_appearance"
    private const val KEY_CONFIG_JSON = "home_appearance_json"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(HomeAppearanceConfig())
    val config: StateFlow<HomeAppearanceConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val raw = prefs?.getString(KEY_CONFIG_JSON, null)
        _config.value = deserializeHomeAppearance(raw)
    }

    fun update(transform: (HomeAppearanceConfig) -> HomeAppearanceConfig) {
        val next = transform(_config.value).let {
            it.copy(
                titleScale = it.titleScale.coerceIn(0.85f, 1.20f),
                subtitleScale = it.subtitleScale.coerceIn(0.85f, 1.15f),
                avatarSizeDp = it.avatarSizeDp.coerceIn(38, 58),
                previewMaxLines = it.previewMaxLines.coerceIn(1, 2),
                sectionOrder = sanitizeSectionOrder(it.sectionOrder),
                hiddenNavTabs = sanitizeHiddenNavTabs(it.hiddenNavTabs)
            )
        }
        _config.value = next
        prefs?.edit()?.putString(KEY_CONFIG_JSON, serializeHomeAppearance(next))?.apply()
    }

    fun applyLayoutPreset(preset: HomeLayoutPreset) {
        val defaults = presetDefaultsFor(preset)
        update { cur -> defaults.copy(hiddenNavTabs = cur.hiddenNavTabs) }
    }

    fun resetCurrentLayout() {
        applyLayoutPreset(_config.value.layoutPreset)
    }

    fun isReducedMotionEffective(context: Context, cfg: HomeAppearanceConfig = _config.value): Boolean {
        if (cfg.reducedMotion) return true
        val animScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        }.getOrDefault(1.0f)
        return animScale <= 0f
    }
}
