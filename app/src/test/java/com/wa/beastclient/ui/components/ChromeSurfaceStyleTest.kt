package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeSurfaceStyleTest {

    @Test
    fun resolveChromeSurfaceStyle_switchesAllSurfacesBetweenGlassAndClassic() {
        assertEquals(ChromeSurfaceStyle.CLASSIC_FLAT, resolveChromeSurfaceStyle(classicMode = true))
        assertEquals(ChromeSurfaceStyle.LIQUID_GLASS, resolveChromeSurfaceStyle(classicMode = false))
    }
}
