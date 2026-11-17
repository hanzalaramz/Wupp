package com.wa.beastclient.ui

import com.wa.beastclient.data.db.StatusEntity
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
}
