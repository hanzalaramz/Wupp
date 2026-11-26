package com.wa.beastclient.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppearanceManagerTest {

    @Test
    fun presetsProduceThreeDistinctHomeLayouts() {
        val classic = presetDefaultsFor(HomeLayoutPreset.CLASSIC)
        val ios = presetDefaultsFor(HomeLayoutPreset.IOS_INSPIRED)
        val compact = presetDefaultsFor(HomeLayoutPreset.COMPACT)

        assertEquals(HomeNavStyle.DOCKED_BAR, classic.navBarStyle)
        assertEquals(HomeNewChatStyle.FLOATING_CIRCLE, classic.newChatButtonStyle)
        assertTrue(classic.showRowDividers)

        assertEquals(HomeNavStyle.FLOATING_PILL, ios.navBarStyle)
        assertEquals(HomeNewChatStyle.TOP_BAR_ONLY, ios.newChatButtonStyle)
        assertEquals(HomeRowDensity.COZY, ios.rowDensity)

        assertEquals(HomeNavStyle.COMPACT_ICONS, compact.navBarStyle)
        assertEquals(HomeRowDensity.COMPACT, compact.rowDensity)
        assertFalse(compact.showNavLabels)
        assertEquals(HomeSectionKind.FILTER_CHIPS, compact.sectionOrder.first())
    }

    @Test
    fun moveSectionAndSanitizePreserveAllSections() {
        val initial = HomeSectionKind.DEFAULT_ORDER
        val movedUp = moveSectionInOrder(initial, HomeSectionKind.ARCHIVED_BANNER, -1)
        assertEquals(
            listOf(
                HomeSectionKind.SEARCH_BAR,
                HomeSectionKind.ARCHIVED_BANNER,
                HomeSectionKind.FILTER_CHIPS
            ),
            movedUp
        )

        val sanitized = sanitizeSectionOrder(listOf(HomeSectionKind.FILTER_CHIPS, HomeSectionKind.FILTER_CHIPS))
        assertEquals(
            listOf(
                HomeSectionKind.FILTER_CHIPS,
                HomeSectionKind.SEARCH_BAR,
                HomeSectionKind.ARCHIVED_BANNER
            ),
            sanitized
        )
    }

    @Test
    fun serializeAndDeserializeRoundTripsAllCustomizationFields() {
        val custom = HomeAppearanceConfig(
            layoutPreset = HomeLayoutPreset.CLASSIC,
            accentStyle = HomeAccentStyle.VIOLET,
            unreadBadgeStyle = HomeBadgeStyle.SOFT_TINT,
            surfaceTone = HomeSurfaceTone.ELEVATED_CARDS,
            titleScale = 1.12f,
            subtitleScale = 0.95f,
            boldUnreadTitles = false,
            rowDensity = HomeRowDensity.COZY,
            avatarSizeDp = 54,
            showRowDividers = true,
            previewMaxLines = 2,
            sectionOrder = listOf(
                HomeSectionKind.FILTER_CHIPS,
                HomeSectionKind.ARCHIVED_BANNER,
                HomeSectionKind.SEARCH_BAR
            ),
            showSearchBar = false,
            showFilterChips = true,
            navBarStyle = HomeNavStyle.FLOATING_PILL,
            showNavLabels = false,
            newChatButtonStyle = HomeNewChatStyle.EXTENDED_PILL,
            reducedMotion = true
        )
        val restored = deserializeHomeAppearance(serializeHomeAppearance(custom))
        assertEquals(custom, restored)
    }
}
