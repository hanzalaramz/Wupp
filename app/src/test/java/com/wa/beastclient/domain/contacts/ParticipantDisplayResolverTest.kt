package com.wa.beastclient.domain.contacts

import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.GroupReceiptEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.MessageReactionEntity
import com.wa.beastclient.ui.ResolvedParticipantUi
import com.wa.beastclient.ui.components.resolvePeekSenderLabel
import com.wa.beastclient.ui.screens.resolveStatusDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantDisplayResolverTest {

    @Test
    fun resolve_mapsLidToSavedContactViaLidToPnAlias() {
        val lid = "26123456789092@lid"
        val pn = "923001234567@s.whatsapp.net"
        val contact = ContactEntity(jid = pn, name = "Ali Khan", isSaved = true)

        val result = ParticipantDisplayResolver.resolve(
            rawJidOrText = lid,
            explicitName = "26123456789092",
            contactsByJid = mapOf(pn to contact),
            lidToPn = mapOf(lid to pn),
            prefixPushWithTilde = false
        )

        assertEquals("Ali Khan", result.primary)
        assertEquals("+92 300 1234567", result.secondaryPhone)
        assertEquals(pn, result.canonicalJid)
        assertEquals(lid, result.altJid)
        assertFalse(result.isUnresolvedLid)
    }

    @Test
    fun resolve_fallsBackToPushNameThenPhoneAndNeverExposesRawLid() {
        val lid = "26123456789092@lid"
        val pn = "923001234567@s.whatsapp.net"

        // 1. Push name from participant meta
        val withPush = ParticipantDisplayResolver.resolve(
            rawJidOrText = lid,
            explicitName = "26123456789092",
            participantMeta = mapOf(
                pn to ResolvedParticipantUi(
                    jid = pn,
                    altJid = lid,
                    pushName = "Mo7amed",
                    phone = "923001234567"
                )
            ),
            lidToPn = mapOf(lid to pn),
            prefixPushWithTilde = false
        )
        assertEquals("Mo7amed", withPush.primary)
        assertEquals("+92 300 1234567", withPush.secondaryPhone)

        // 2. Only PN alias known -> formatted international phone
        val withPhoneOnly = ParticipantDisplayResolver.resolve(
            rawJidOrText = lid,
            explicitName = "26123456789092",
            lidToPn = mapOf(lid to pn),
            prefixPushWithTilde = false
        )
        assertEquals("+92 300 1234567", withPhoneOnly.primary)
        assertFalse(withPhoneOnly.isUnresolvedLid)

        // 3. Completely unresolved LID -> clean masked label, NEVER raw 14-digit LID
        val unresolved = ParticipantDisplayResolver.resolve(
            rawJidOrText = lid,
            explicitName = "26123456789092",
            prefixPushWithTilde = false
        )
        assertEquals("~ User · …9092", unresolved.primary)
        assertTrue(unresolved.isUnresolvedLid)
    }

    @Test
    fun deduplicateGroupReceipts_mergesLidAndPnKeepingHigherRankAndBetterName() {
        val lid = "26123456789092@lid"
        val pn = "923001234567@s.whatsapp.net"
        val receipts = listOf(
            GroupReceiptEntity("m1", lid, "26123456789092", "DELIVERED", 1000L),
            GroupReceiptEntity("m1", pn, "Sara", "READ", 2000L),
            GroupReceiptEntity("m1", "19112233445577@lid", "", "DELIVERED", 1500L)
        )

        val deduped = ParticipantDisplayResolver.deduplicateGroupReceipts(
            receipts = receipts,
            lidToPn = mapOf(lid to pn)
        )

        assertEquals(2, deduped.size)
        assertEquals(pn, deduped[0].participantJid)
        assertEquals("Sara", deduped[0].participantName)
        assertEquals("READ", deduped[0].status)
        assertEquals(2000L, deduped[0].timestamp)
        assertEquals("19112233445577@lid", deduped[1].participantJid)
    }

    @Test
    fun deduplicateReactions_mergesOwnLidWithMeActorAndDeduplicatesPeers() {
        val ownPn = "923001112222@s.whatsapp.net"
        val ownLid = "11000000000001@lid"
        val peerPn = "923009998888@s.whatsapp.net"
        val peerLid = "22000000000002@lid"

        val reactions = listOf(
            MessageReactionEntity("m1", ownLid, "👍", 1000L),
            MessageReactionEntity("m1", MessageReactionEntity.ME_ACTOR, "❤️", 2000L),
            MessageReactionEntity("m1", peerLid, "😂", 1500L),
            MessageReactionEntity("m1", peerPn, "🔥", 2500L)
        )

        val deduped = ParticipantDisplayResolver.deduplicateReactions(
            reactions = reactions,
            lidToPn = mapOf(ownLid to ownPn, peerLid to peerPn),
            ownJid = ownPn,
            ownLid = ownLid
        )

        assertEquals(2, deduped.size)
        val meRow = deduped.first { it.actor == MessageReactionEntity.ME_ACTOR }
        assertEquals("❤️", meRow.emoji)
        val peerRow = deduped.first { it.actor == peerPn }
        assertEquals("🔥", peerRow.emoji)
    }

    @Test
    fun resolve_detectsSelfViaOwnLidOrAlias() {
        val ownPn = "923001112222@s.whatsapp.net"
        val ownLid = "11000000000001@lid"

        val result = ParticipantDisplayResolver.resolve(
            rawJidOrText = ownLid,
            ownJid = ownPn,
            ownLid = ownLid,
            prefixPushWithTilde = false
        )

        assertTrue(result.isSelf)
        assertEquals("You", result.primary)
    }

    @Test
    fun peekAndStatusHelpers_neverLeakRawNumericLids() {
        val lid = "26123456789092@lid"
        val pn = "923001234567@s.whatsapp.net"
        val msg = MessageEntity(
            id = "m_peek",
            chatJid = "120363000000@g.us",
            senderJid = lid,
            senderName = "",
            isFromMe = false,
            timestamp = 1000L,
            body = "hi"
        )

        assertEquals(
            "~ User · …9092",
            resolvePeekSenderLabel(msg, isGroupChat = true, lidToPn = emptyMap())
        )
        assertEquals(
            "Ali",
            resolvePeekSenderLabel(
                msg,
                isGroupChat = true,
                chatsByJid = mapOf(pn to ChatEntity(jid = pn, name = "Ali")),
                lidToPn = mapOf(lid to pn)
            )
        )
        assertEquals(
            "~ User · …9092",
            resolveStatusDisplayName(
                senderJid = lid,
                storedName = "26123456789092",
                senderChat = null
            )
        )
    }
}
