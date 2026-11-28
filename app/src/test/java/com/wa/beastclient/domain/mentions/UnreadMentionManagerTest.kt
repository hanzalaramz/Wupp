package com.wa.beastclient.domain.mentions

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnreadMentionManagerTest {

    private val groupJid = "120363011122233344@g.us"
    private val ownJid = "15551234567@s.whatsapp.net"
    private val ownLid = "240987654321@lid"

    @Before
    fun setUp() {
        UnreadMentionManager.resetForTest()
    }

    @After
    fun tearDown() {
        UnreadMentionManager.resetForTest()
    }

    @Test
    fun detectsDirectMentionByPhoneJidOrLid() {
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "15559990000@s.whatsapp.net,$ownJid",
                isFromMe = false,
                ownJid = ownJid,
                ownLid = ownLid
            )
        )
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = ownLid,
                isFromMe = false,
                ownJid = ownJid,
                ownLid = ownLid
            )
        )
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "999888777666@lid",
                isFromMe = false,
                ownJid = ownJid,
                ownLid = "",
                lidToPn = mapOf("999888777666" to "15551234567")
            )
        )
    }

    @Test
    fun detectsEveryoneAndGroupMentions() {
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = groupJid,
                isFromMe = false,
                ownJid = ownJid
            )
        )
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "everyone",
                isFromMe = false,
                ownJid = ownJid
            )
        )
        assertTrue(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "all",
                isFromMe = false,
                ownJid = ownJid
            )
        )
    }

    @Test
    fun ignoresOwnMessagesDmsAndUnrelatedMentions() {
        assertFalse(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = ownJid,
                isFromMe = true,
                ownJid = ownJid
            )
        )
        assertFalse(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = "15559990000@s.whatsapp.net",
                mentionsCsv = ownJid,
                isFromMe = false,
                ownJid = ownJid
            )
        )
        assertFalse(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "15559990000@s.whatsapp.net,111222333444@lid",
                isFromMe = false,
                ownJid = ownJid,
                ownLid = ownLid
            )
        )
        assertFalse(
            UnreadMentionManager.doesMessageMentionMe(
                chatJid = groupJid,
                mentionsCsv = "   ",
                isFromMe = false,
                ownJid = ownJid
            )
        )
    }

    @Test
    fun tracksOrdersDeduplicatesAndClearsUnreadMentions() {
        UnreadMentionManager.recordUnreadMention(groupJid, "msg-2", 2000L)
        UnreadMentionManager.recordUnreadMention(groupJid, "msg-1", 1000L)
        UnreadMentionManager.recordUnreadMention(groupJid, "msg-2", 2000L)
        UnreadMentionManager.recordUnreadMention(groupJid, "msg-3", 3000L)
        UnreadMentionManager.recordUnreadMention("15559990000@s.whatsapp.net", "dm-1", 1000L)

        assertEquals(
            listOf("msg-1", "msg-2", "msg-3"),
            UnreadMentionManager.getUnreadMentions(groupJid).map { it.messageId }
        )
        assertTrue(UnreadMentionManager.getUnreadMentions("15559990000@s.whatsapp.net").isEmpty())

        UnreadMentionManager.markMentionSeen(groupJid, "msg-1")
        assertEquals(
            listOf("msg-2", "msg-3"),
            UnreadMentionManager.getUnreadMentions(groupJid).map { it.messageId }
        )

        UnreadMentionManager.markMentionsSeenUpTo(groupJid, 2000L)
        assertEquals(
            listOf("msg-3"),
            UnreadMentionManager.getUnreadMentions(groupJid).map { it.messageId }
        )

        UnreadMentionManager.clearChatMentions(groupJid)
        assertTrue(UnreadMentionManager.getUnreadMentions(groupJid).isEmpty())
    }

    @Test
    fun capsStoredMentionsPerChatAtFifty() {
        for (i in 1..60) {
            UnreadMentionManager.recordUnreadMention(groupJid, "msg-$i", i * 1000L)
        }
        val stored = UnreadMentionManager.getUnreadMentions(groupJid)
        assertEquals(50, stored.size)
        assertEquals("msg-11", stored.first().messageId)
        assertEquals("msg-60", stored.last().messageId)
    }

    @Test
    fun encodeAndDecodeRoundTripPreservesOrderAndIgnoresMalformedLines() {
        val entries = listOf(
            UnreadMentionEntry("3EB0ABC", 1700000001000L),
            UnreadMentionEntry("3EB0DEF", 1700000005000L)
        )
        val encoded = UnreadMentionManager.encodeEntries(entries)
        assertEquals(entries, UnreadMentionManager.decodeEntries(encoded))

        val noisy = "corrupt-line\n1700000005000|3EB0DEF\n|missing-ts\n1700000001000|3EB0ABC\n1700000001000|3EB0ABC\n"
        assertEquals(entries, UnreadMentionManager.decodeEntries(noisy))
    }
}
