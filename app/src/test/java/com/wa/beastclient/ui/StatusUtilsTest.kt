package com.wa.beastclient.ui

import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.ui.components.buildContactStatusSummaries
import com.wa.beastclient.ui.components.isOwnStatusSender
import com.wa.beastclient.ui.components.isStatusMediaType
import com.wa.beastclient.ui.components.partitionOwnAndContactStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusUtilsTest {

    @Test
    fun isOwnStatusSender_matchesDeviceSuffixedAndNormalizedJids() {
        val own = "923001234567:0@s.whatsapp.net"
        assertTrue(isOwnStatusSender("923001234567@s.whatsapp.net", own))
        assertTrue(isOwnStatusSender("923001234567:24@s.whatsapp.net", own))
        assertTrue(isOwnStatusSender("me@s.whatsapp.net", ""))
        assertFalse(isOwnStatusSender("923009999999@s.whatsapp.net", own))
        assertFalse(isOwnStatusSender("923001234567@s.whatsapp.net", ""))
    }

    @Test
    fun partitionOwnAndContactStatuses_keepsOwnStoriesOutOfContactFeed() {
        val ownJid = "6281234567890@s.whatsapp.net"
        val mine = StatusEntity(
            id = "s1",
            senderJid = "6281234567890:15@s.whatsapp.net",
            senderName = "@osyawicane",
            caption = "v2",
            msgType = "VIDEO",
            mimeType = "video/mp4",
            timestamp = 2000L
        )
        val friend = StatusEntity(
            id = "s2",
            senderJid = "6289876543210@s.whatsapp.net",
            senderName = "Alice",
            caption = "Hello",
            msgType = "IMAGE",
            mimeType = "image/jpeg",
            timestamp = 1500L
        )

        val (own, contacts) = partitionOwnAndContactStatuses(listOf(mine, friend), ownJid)
        assertEquals(listOf("s1"), own.map { it.id })
        assertEquals(listOf("s2"), contacts.map { it.id })
    }

    @Test
    fun partitionOwnAndContactStatuses_filtersOutExpiredStatusesWhenRequested() {
        val ownJid = "6281234567890@s.whatsapp.net"
        val now = 1_700_000_000_000L
        val recentOwn = StatusEntity(
            id = "s_recent_own",
            senderJid = "6281234567890:15@s.whatsapp.net",
            senderName = "Me",
            timestamp = now - 3_600_000L // 1 hour ago (active)
        )
        val expiredOwn = StatusEntity(
            id = "s_expired_own",
            senderJid = "6281234567890@s.whatsapp.net",
            senderName = "Me",
            timestamp = now - 90_000_000L // 25 hours ago (expired)
        )
        val recentFriend = StatusEntity(
            id = "s_recent_friend",
            senderJid = "6289876543210@s.whatsapp.net",
            senderName = "Alice",
            timestamp = now - 10_000_000L // ~2.7 hours ago (active)
        )
        val expiredFriend = StatusEntity(
            id = "s_expired_friend",
            senderJid = "6289876543210@s.whatsapp.net",
            senderName = "Alice",
            timestamp = now - 100_000_000L // 27.7 hours ago (expired)
        )

        val (own, contacts) = partitionOwnAndContactStatuses(
            statuses = listOf(recentOwn, expiredOwn, recentFriend, expiredFriend),
            ownJid = ownJid,
            filterExpired = true,
            now = now
        )

        assertEquals(listOf("s_recent_own"), own.map { it.id })
        assertEquals(listOf("s_recent_friend"), contacts.map { it.id })
    }

    @Test
    fun isStatusMediaType_separatesMediaFromTextStatus() {
        assertTrue(isStatusMediaType("VIDEO", "video/mp4"))
        assertTrue(isStatusMediaType("IMAGE", ""))
        assertTrue(isStatusMediaType("", "video/quicktime"))
        assertFalse(isStatusMediaType("TEXT", ""))
        assertFalse(isStatusMediaType("TEXT", "text/plain"))
    }

    @Test
    fun normalizeStatusSenderJid_stripsCompanionDeviceSuffixForChatRouting() {
        assertEquals(
            "923001234567@s.whatsapp.net",
            com.wa.beastclient.ui.screens.normalizeStatusSenderJid("923001234567:15@s.whatsapp.net")
        )
        assertEquals(
            "923001234567@s.whatsapp.net",
            com.wa.beastclient.ui.screens.normalizeStatusSenderJid("923001234567@s.whatsapp.net")
        )
    }

    @Test
    fun resolveStatusQuoteFallback_formatsAppropriatePlaceholder() {
        val imageStatus = StatusEntity(
            id = "s1",
            senderJid = "user@s.whatsapp.net",
            caption = "",
            msgType = "IMAGE",
            timestamp = 1000L
        )
        val videoStatus = StatusEntity(
            id = "s2",
            senderJid = "user@s.whatsapp.net",
            caption = "",
            msgType = "VIDEO",
            timestamp = 2000L
        )
        val captionedStatus = StatusEntity(
            id = "s3",
            senderJid = "user@s.whatsapp.net",
            caption = "Sunset at the beach",
            msgType = "IMAGE",
            timestamp = 3000L
        )

        assertEquals("📷 Photo status", com.wa.beastclient.ui.components.resolveStatusQuoteFallback(imageStatus))
        assertEquals("📹 Video status", com.wa.beastclient.ui.components.resolveStatusQuoteFallback(videoStatus))
        assertEquals("Sunset at the beach", com.wa.beastclient.ui.components.resolveStatusQuoteFallback(captionedStatus))
    }

    @Test
    fun formatDeletedLabel_prefersDeletionTimestampAndFallsBackToPostedTimestamp() {
        val postedAt = 1_700_000_000_000L
        val deletedAt = 1_700_000_600_000L
        val withDeletedAt = com.wa.beastclient.ui.components.formatDeletedLabel(
            deletedAtMs = deletedAt,
            fallbackTimestampMs = postedAt,
            includeSeconds = false,
            use24Hour = true
        )
        val expectedDeletedClock = com.wa.beastclient.ui.components.formatMessageClock(
            epochMs = deletedAt,
            includeSeconds = false,
            use24Hour = true
        )
        assertEquals("Deleted · $expectedDeletedClock", withDeletedAt)

        val fallback = com.wa.beastclient.ui.components.formatDeletedLabel(
            deletedAtMs = 0L,
            fallbackTimestampMs = postedAt,
            includeSeconds = true,
            use24Hour = true
        )
        val expectedPostedClock = com.wa.beastclient.ui.components.formatMessageClock(
            epochMs = postedAt,
            includeSeconds = true,
            use24Hour = true
        )
        assertEquals("Deleted · $expectedPostedClock", fallback)

        assertEquals(
            "Deleted",
            com.wa.beastclient.ui.components.formatDeletedLabel(
                deletedAtMs = 0L,
                fallbackTimestampMs = 0L,
                includeSeconds = false,
                use24Hour = true
            )
        )
    }

    @Test
    fun buildContactStatusSummaries_prioritizesUnviewedContactsAndStartsOnFirstUnviewedStory() {
        val seenContact = StatusEntity(
            id = "seen_latest",
            senderJid = "923001111111@s.whatsapp.net",
            senderName = "Seen Friend",
            timestamp = 5000L,
            isViewed = true
        )
        val partialFirstViewed = StatusEntity(
            id = "partial_1",
            senderJid = "923002222222:1@s.whatsapp.net",
            senderName = "Partial Friend",
            timestamp = 2000L,
            isViewed = true
        )
        val partialSecondUnviewed = StatusEntity(
            id = "partial_2",
            senderJid = "923002222222:2@s.whatsapp.net",
            senderName = "Partial Friend",
            timestamp = 3000L,
            isViewed = false
        )
        val partialThirdUnviewed = StatusEntity(
            id = "partial_3",
            senderJid = "923002222222@s.whatsapp.net",
            senderName = "Partial Friend",
            timestamp = 4000L,
            isViewed = false
        )

        val summaries = buildContactStatusSummaries(
            listOf(seenContact, partialThirdUnviewed, partialFirstViewed, partialSecondUnviewed)
        )

        assertEquals(2, summaries.size)
        // Unviewed contact appears before fully-seen contact even when seen contact posted later
        assertEquals("923002222222@s.whatsapp.net", summaries[0].senderKey)
        assertTrue(summaries[0].hasUnviewed)
        assertEquals("partial_2", summaries[0].representativeStatus.id)
        assertEquals(listOf("partial_1", "partial_2", "partial_3"), summaries[0].items.map { it.id })

        assertEquals("923001111111@s.whatsapp.net", summaries[1].senderKey)
        assertFalse(summaries[1].hasUnviewed)
        assertEquals("seen_latest", summaries[1].representativeStatus.id)
    }
}
