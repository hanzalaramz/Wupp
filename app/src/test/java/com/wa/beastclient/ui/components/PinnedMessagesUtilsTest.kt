package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedMessagesUtilsTest {

    @Test
    fun standardPinDurations_matchWhatsAppConstants() {
        val duration24h = 24 * 3600L
        val duration7d = 7 * 24 * 3600L
        val duration30d = 30 * 24 * 3600L

        assertEquals(86400L, duration24h)
        assertEquals(604800L, duration7d)
        assertEquals(2592000L, duration30d)
    }

    @Test
    fun isPinExpired_checksTimestampCorrectly() {
        val now = 1000000000L
        // 0 means no expiry (permanent / until manually unpinned)
        assertFalse(isPinExpired(pinnedUntil = 0L, currentTime = now))

        // Expiry in future
        assertFalse(isPinExpired(pinnedUntil = now + 10000L, currentTime = now))

        // Expiry in past
        assertTrue(isPinExpired(pinnedUntil = now - 1L, currentTime = now))
        assertTrue(isPinExpired(pinnedUntil = now, currentTime = now))
    }

    @Test
    fun cyclePinnedIndex_loopsCorrectlyThroughPinnedMessages() {
        val pinnedCount = 3
        assertEquals(1, cyclePinnedIndex(currentIndex = 0, totalPins = pinnedCount))
        assertEquals(2, cyclePinnedIndex(currentIndex = 1, totalPins = pinnedCount))
        assertEquals(0, cyclePinnedIndex(currentIndex = 2, totalPins = pinnedCount))

        // Edge case: single pin
        assertEquals(0, cyclePinnedIndex(currentIndex = 0, totalPins = 1))
        // Edge case: zero pins
        assertEquals(0, cyclePinnedIndex(currentIndex = 0, totalPins = 0))
    }

    companion object {
        fun isPinExpired(pinnedUntil: Long, currentTime: Long): Boolean {
            return pinnedUntil > 0L && currentTime >= pinnedUntil
        }

        fun cyclePinnedIndex(currentIndex: Int, totalPins: Int): Int {
            if (totalPins <= 1) return 0
            val safe = currentIndex.coerceIn(0, totalPins - 1)
            return (safe + 1) % totalPins
        }
    }
}
