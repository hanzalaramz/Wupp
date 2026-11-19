package com.wa.beastclient.ui.screens

import com.wa.beastclient.ui.ResolvedParticipantUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMemberResolutionTest {

    @Test
    fun `resolveMemberChatJid prefers canonical phone JID over LID`() {
        val meta = ResolvedParticipantUi(
            jid = "249182736451234@lid",
            altJid = "15551234567@s.whatsapp.net",
            savedName = "Ali",
            phone = "+15551234567"
        )
        assertEquals(
            "15551234567@s.whatsapp.net",
            resolveMemberChatJid("249182736451234@lid", meta)
        )
    }

    @Test
    fun `resolveMemberChatJid reconstructs phone JID from meta phone when altJid missing`() {
        val meta = ResolvedParticipantUi(
            jid = "249182736451234@lid",
            phone = "+92 300-1234567"
        )
        assertEquals(
            "923001234567@s.whatsapp.net",
            resolveMemberChatJid("249182736451234@lid", meta)
        )
    }

    @Test
    fun `resolveMemberDisplayPhone never formats raw LID digits as plus number`() {
        assertEquals(
            "",
            resolveMemberDisplayPhone("249182736451234@lid", null)
        )
        assertEquals(
            "+15551234567",
            resolveMemberDisplayPhone(
                "249182736451234@lid",
                ResolvedParticipantUi(
                    jid = "249182736451234@lid",
                    altJid = "15551234567:2@s.whatsapp.net"
                )
            )
        )
    }

    @Test
    fun `isGroupMemberAdmin matches across canonical and alternate JIDs`() {
        val meta = ResolvedParticipantUi(
            jid = "249182736451234@lid",
            altJid = "15551234567@s.whatsapp.net"
        )
        val admins = setOf("249182736451234@lid")
        assertTrue(
            isGroupMemberAdmin(
                participantJid = "15551234567@s.whatsapp.net",
                resolvedChatJid = "15551234567@s.whatsapp.net",
                meta = meta.copy(jid = "15551234567@s.whatsapp.net", altJid = "249182736451234@lid"),
                groupAdmins = admins
            )
        )
        assertFalse(
            isGroupMemberAdmin(
                participantJid = "15559998888@s.whatsapp.net",
                resolvedChatJid = "15559998888@s.whatsapp.net",
                meta = null,
                groupAdmins = admins
            )
        )
    }
}
