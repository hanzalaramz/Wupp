package com.wa.beastclient.core.service

import com.wa.beastclient.data.db.WaDao
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadSyncResolutionTest {

    @Test
    fun omittedUnreadCountPreservesExistingUnread() {
        val resolved = WaDao.resolveHistorySyncUnreadCount(
            isCurrentlyOpen = false,
            incomingUnreadCount = -1,
            incomingLastTimestamp = 2_000L,
            existingUnreadCount = 5,
            existingLastFromMe = false,
            existingLastTimestamp = 2_000L
        )
        assertEquals(5, resolved)
    }

    @Test
    fun explicitZeroUnreadCountClearsExistingUnread() {
        val resolved = WaDao.resolveHistorySyncUnreadCount(
            isCurrentlyOpen = false,
            incomingUnreadCount = 0,
            incomingLastTimestamp = 3_000L,
            existingUnreadCount = 5,
            existingLastFromMe = false,
            existingLastTimestamp = 2_000L
        )
        assertEquals(0, resolved)
    }

    @Test
    fun staleSyncBatchDoesNotReinflateUnreadWhenLatestMessageIsFromMe() {
        val resolved = WaDao.resolveHistorySyncUnreadCount(
            isCurrentlyOpen = false,
            incomingUnreadCount = 1,
            incomingLastTimestamp = 2_000L,
            existingUnreadCount = 0,
            existingLastFromMe = true,
            existingLastTimestamp = 2_500L
        )
        assertEquals(0, resolved)
    }

    @Test
    fun currentlyOpenChatAlwaysClearsUnread() {
        val resolved = WaDao.resolveHistorySyncUnreadCount(
            isCurrentlyOpen = true,
            incomingUnreadCount = 4,
            incomingLastTimestamp = 3_000L,
            existingUnreadCount = 2,
            existingLastFromMe = false,
            existingLastTimestamp = 2_000L
        )
        assertEquals(0, resolved)
    }
}
