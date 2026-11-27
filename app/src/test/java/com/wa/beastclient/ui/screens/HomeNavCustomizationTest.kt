package com.wa.beastclient.ui.screens

import com.wa.beastclient.domain.appearance.HomeAppearanceConfig
import com.wa.beastclient.domain.appearance.deserializeHomeAppearance
import com.wa.beastclient.domain.appearance.sanitizeHiddenNavTabs
import com.wa.beastclient.domain.appearance.serializeHomeAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeNavCustomizationTest {

    @Test
    fun hidingTabsRemovesThemFromTabOrder() {
        val order = resolveHomeTabOrder(
            separateGroupsAndChats = true,
            separateChannelsTab = false,
            hiddenNavTabs = setOf(2, 5) // hide Communities and Calls
        )
        assertEquals(listOf(0, 4, 1, 3), order)
    }

    @Test
    fun hidingAllTabsFallsBackToChatsTab() {
        val order = resolveHomeTabOrder(
            separateGroupsAndChats = true,
            separateChannelsTab = false,
            hiddenNavTabs = setOf(0, 4, 1, 2, 5, 3)
        )
        assertEquals(listOf(0), order)
    }

    @Test
    fun sanitizeHiddenNavTabsNeverAllowsAllSevenTabsHidden() {
        val sanitized = sanitizeHiddenNavTabs(setOf(0, 4, 1, 6, 2, 5, 3, 99))
        assertFalse(0 in sanitized)
        assertFalse(99 in sanitized)
    }

    @Test
    fun serializeAndDeserializePreservesHiddenNavTabs() {
        val cfg = HomeAppearanceConfig(hiddenNavTabs = setOf(1, 2, 5))
        val raw = serializeHomeAppearance(cfg)
        val restored = deserializeHomeAppearance(raw)
        assertEquals(setOf(1, 2, 5), restored.hiddenNavTabs)
    }
}
