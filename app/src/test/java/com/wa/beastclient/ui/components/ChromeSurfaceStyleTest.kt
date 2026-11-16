package com.wa.beastclient.ui.components

import androidx.compose.ui.graphics.Color
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
    }
}

