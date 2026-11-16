package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
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
}

