package com.wa.beastclient.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupParticipantAndReactionBadgeTest {

    @Test
    fun formatReactionBadgeSummaryShowsTotalCountForMultipleReactions() {
        assertEquals(
            "👍 3",
            formatReactionBadgeSummary(
                legacySummary = "👍",
                counts = listOf("👍" to 3)
            )
        )
        assertEquals(
            "👍❤️ 4",
            formatReactionBadgeSummary(
                legacySummary = "👍 ❤️",
                counts = listOf("👍" to 2, "❤️" to 2)
            )
        )
    }

    @Test
    fun formatReactionBadgeSummaryKeepsSingleEmojiCompactAndFallsBackToLegacy() {
        assertEquals(
            "🔥",
            formatReactionBadgeSummary(
                legacySummary = "🔥",
                counts = listOf("🔥" to 1)
            )
        )
        assertEquals(
            "🔥 💯",
            formatReactionBadgeSummary(
                legacySummary = "🔥 💯",
                counts = emptyList()
            )
        )
    }
}
