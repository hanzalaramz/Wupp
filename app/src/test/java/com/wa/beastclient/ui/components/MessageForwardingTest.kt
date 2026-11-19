package com.wa.beastclient.ui.components

import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageForwardingTest {

    private fun msg(
        msgType: String,
        body: String = "hello",
        isViewOnce: Boolean = false,
        isDeleted: Boolean = false
    ) = MessageEntity(
        id = "m1",
        chatJid = "123@s.whatsapp.net",
        senderJid = "123@s.whatsapp.net",
        senderName = "User",
        isFromMe = false,
        timestamp = 1000L,
        body = body,
        msgType = msgType,
        isViewOnce = isViewOnce,
        isDeletedBySender = isDeleted
    )

    @Test
    fun forwardableMessageTypeAllowsStandardMediaTextDocsAndBlocksViewOnceDeletedOrCalls() {
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("TEXT", "Hi")))
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("IMAGE", "")))
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("VIDEO", "")))
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("DOCUMENT", "app.apk")))
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("STICKER", "")))
        assertTrue(WaRealtimeService.isForwardableMessageType(msg("PTT", "")))

        assertFalse(WaRealtimeService.isForwardableMessageType(msg("TEXT", "   ")))
        assertFalse(WaRealtimeService.isForwardableMessageType(msg("IMAGE", "", isViewOnce = true)))
        assertFalse(WaRealtimeService.isForwardableMessageType(msg("TEXT", "deleted", isDeleted = true)))
        assertFalse(WaRealtimeService.isForwardableMessageType(msg("CALL", "Missed call")))
        assertFalse(WaRealtimeService.isForwardableMessageType(msg("UNDECRYPTABLE", "")))
    }

    @Test
    fun normalizeForwardTargetsDeduplicatesSkipsChannelsBroadcastsAndCapsAtFive() {
        val normalized = WaRealtimeService.normalizeForwardTargetJids(
            listOf(
                "111@s.whatsapp.net",
                "status@broadcast",
                "chan@newsletter",
                "111@s.whatsapp.net",
                "222@g.us",
                "333@s.whatsapp.net",
                "444@s.whatsapp.net",
                "555@s.whatsapp.net",
                "666@s.whatsapp.net"
            )
        )
        assertEquals(
            listOf(
                "111@s.whatsapp.net",
                "222@g.us",
                "333@s.whatsapp.net",
                "444@s.whatsapp.net",
                "555@s.whatsapp.net"
            ),
            normalized
        )
    }

    @Test
    fun filterForwardTargetChatsExcludesChannelsCommunitiesAndLockedGroups() {
        val chats = listOf(
            ChatEntity(jid = "alice@s.whatsapp.net", name = "Alice", lastMessageTimestamp = 300),
            ChatEntity(jid = "locked@g.us", name = "Announcements", isGroup = true, lastMessageTimestamp = 400),
            ChatEntity(jid = "friends@g.us", name = "Friends", isGroup = true, lastMessageTimestamp = 500),
            ChatEntity(jid = "news@newsletter", name = "News", lastMessageTimestamp = 600),
            ChatEntity(jid = "comm@g.us", name = "Community", isGroup = true, isCommunityParent = true, lastMessageTimestamp = 700)
        )
        val filtered = filterForwardTargetChats(
            allChats = chats,
            announceLockedGroups = setOf("locked@g.us"),
            query = ""
        )
        assertEquals(listOf("friends@g.us", "alice@s.whatsapp.net"), filtered.map { it.jid })

        val searched = filterForwardTargetChats(
            allChats = chats,
            announceLockedGroups = setOf("locked@g.us"),
            query = "ali"
        )
        assertEquals(listOf("alice@s.whatsapp.net"), searched.map { it.jid })
    }
}
