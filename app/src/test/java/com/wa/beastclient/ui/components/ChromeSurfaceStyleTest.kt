package com.wa.beastclient.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.WuppAccentColor
import com.wa.beastclient.ui.theme.WuppThemeMode
import com.wa.beastclient.ui.theme.colorsForTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeSurfaceStyleTest {

    @Test
    fun resolveChromeSurfaceStyle_switchesAllSurfacesBetweenGlassAndClassic() {
        assertEquals(ChromeSurfaceStyle.CLASSIC_FLAT, resolveChromeSurfaceStyle(classicMode = true))
        assertEquals(ChromeSurfaceStyle.LIQUID_GLASS, resolveChromeSurfaceStyle(classicMode = false))
    }

    @Test
    fun resolveLiquidGlassSurfaceAlpha_adaptsForApiBlurSupport() {
        val darkGlass = resolveLiquidGlassSurfaceAlpha(isDark = true, renderEffectSupported = true)
        val darkFallback = resolveLiquidGlassSurfaceAlpha(isDark = true, renderEffectSupported = false)
        assertTrue(darkGlass < 0.6f)
        assertTrue(darkFallback > 0.8f)

        val lightGlass = resolveLiquidGlassSurfaceAlpha(isDark = false, renderEffectSupported = true)
        val lightFallback = resolveLiquidGlassSurfaceAlpha(isDark = false, renderEffectSupported = false)
        assertTrue(lightGlass < 0.7f)
        assertTrue(lightFallback > 0.9f)
    }

    @Test
    fun resolveChromeBarTintAndAlpha_matchThemeSurfaceAndBlurSupport() {
        val amoled = colorsForTheme(WuppThemeMode.AMOLED, WuppAccentColor.DEFAULT)
        val light = colorsForTheme(WuppThemeMode.LIGHT, WuppAccentColor.DEFAULT)
        assertEquals(amoled.appSurface, resolveChromeBarTintColor(amoled))
        assertEquals(light.appSurface, resolveChromeBarTintColor(light))

        assertEquals(
            1f,
            resolveChromeBarSurfaceAlpha(isDark = true, renderEffectSupported = true, classicMode = true),
            0.001f
        )
        assertTrue(
            resolveChromeBarSurfaceAlpha(isDark = true, renderEffectSupported = true, classicMode = false) < 0.7f
        )
        assertTrue(
            resolveChromeBarSurfaceAlpha(isDark = false, renderEffectSupported = false, classicMode = false) > 0.85f
        )
    }

    @Test
    fun colorsForTheme_appliesCustomAccentOverrideCleanly() {
        val defaultDark = colorsForTheme(WuppThemeMode.DARK, WuppAccentColor.DEFAULT)
        val oceanDark = colorsForTheme(WuppThemeMode.DARK, WuppAccentColor.OCEAN)

        assertNotEquals(defaultDark.brightGreen, oceanDark.brightGreen)
        assertEquals(Color(WuppAccentColor.OCEAN.brightArgb), oceanDark.brightGreen)
        assertEquals(Color(WuppAccentColor.OCEAN.accentArgb), oceanDark.accentGreen)
        assertEquals(WuppAccentColor.OCEAN, WuppAccentColor.fromStorageKey("ocean"))
        assertEquals(WuppAccentColor.DEFAULT, WuppAccentColor.fromStorageKey("unknown_key"))
    }

    @Test
    fun resolveLightboxPages_preservesGalleryAndResolvesStartIndex() {
        val m1 = MessageEntity(id = "1", chatJid = "c", senderJid = "s", timestamp = 100L, msgType = "IMAGE")
        val m2 = MessageEntity(id = "2", chatJid = "c", senderJid = "s", timestamp = 200L, msgType = "VIDEO")
        val m3 = MessageEntity(id = "3", chatJid = "c", senderJid = "s", timestamp = 300L, msgType = "IMAGE")
        val standalone = MessageEntity(id = "vo", chatJid = "c", senderJid = "s", timestamp = 400L, isViewOnce = true)

        val galleryPages = resolveLightboxPages(m2, listOf(m1, m2, m3))
        assertEquals(listOf("1", "2", "3"), galleryPages.map { it.id })
        assertEquals(1, resolveLightboxStartIndex(galleryPages, "2"))

        val singlePage = resolveLightboxPages(standalone, listOf(m1, m2, m3))
        assertEquals(listOf("vo"), singlePage.map { it.id })
        assertEquals(0, resolveLightboxStartIndex(singlePage, "vo"))
        assertEquals("vo", resolveLightboxPageKey(singlePage, 0))
        assertEquals("page_1", resolveLightboxPageKey(singlePage, 1))
    }

    @Test
    fun computeLiquidNavSpan_clampsBetweenMinAndMaxSpan() {
        assertEquals(1.0f, computeLiquidNavSpan(0f, 1f), 0.001f)
        assertEquals(1.35f, computeLiquidNavSpan(0.5f, 1.85f), 0.001f)
        // Upper clamp
        assertEquals(2.5f, computeLiquidNavSpan(0f, 3.5f), 0.001f)
        // Lower clamp
        assertEquals(0.85f, computeLiquidNavSpan(1.2f, 1.4f), 0.001f)
    }

    @Test
    fun computeLiquidNavSquash_squashesSlightlyWhenStretched() {
        // At rest, span is 1f -> vertical scale is 1f (no squash)
        assertEquals(1.0f, computeLiquidNavSquash(1.0f), 0.001f)

        // Stretched in transit (e.g. span = 1.35f) -> subtle vertical squash (~5-6%)
        val squash = computeLiquidNavSquash(1.35f)
        assertTrue("Squash must be less than 1.0f when stretched", squash < 1.0f)
        assertTrue("Squash must stay subtle above 0.85f", squash > 0.85f)
    }

    @Test
    fun computeLiquidNavBounds_stretchesPillAndClampsInsideParentCapsule() {
        val tabWidth = 70.dp
        val maxWidth = 280.dp

        // Resting at tab 0
        val (restOffset, restWidth) = computeLiquidNavBounds(tabWidth, 0f, 1f, maxWidth)
        assertEquals(0.dp, restOffset)
        assertEquals(70.dp, restWidth)

        // Mid-transit stretching across tabs (left at 0.4, right at 1.7 -> span 1.3)
        val (stretchOffset, stretchWidth) = computeLiquidNavBounds(tabWidth, 0.4f, 1.7f, maxWidth)
        assertEquals(28f, stretchOffset.value, 0.01f)
        assertEquals(91f, stretchWidth.value, 0.01f)

        // Near end clamp: offset 230dp + width 70dp exceeds maxWidth 280dp -> clamped to remaining 50dp
        val (clampedOffset, clampedWidth) = computeLiquidNavBounds(70.dp, 3.2857f, 4.5f, maxWidth)
        assertTrue(clampedOffset + clampedWidth <= maxWidth)
    }
}

