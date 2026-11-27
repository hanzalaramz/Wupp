package com.wa.beastclient.ui.screens

import com.wa.beastclient.ui.components.resolveMentionsInText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroupMentionHelperTest {

    @Test
    fun extractActiveMentionQueryDetectsAtStartAndAfterSpace() {
        val atStart = extractActiveMentionQuery("@moh", 4)
        assertNotNull(atStart)
        assertEquals(0, atStart!!.startIndex)
        assertEquals("moh", atStart.query)

        val afterSpace = extractActiveMentionQuery("hello @ali", 10)
        assertNotNull(afterSpace)
        assertEquals(6, afterSpace!!.startIndex)
        assertEquals("ali", afterSpace.query)

        assertNull(extractActiveMentionQuery("email@domain", 12))
    }

    @Test
    fun applyMentionSelectionReplacesQueryAndMovesCursor() {
        val (updated, cursor) = applyMentionSelection(
            text = "Hey @mo how are you",
            startIndex = 4,
            cursorIndex = 7,
            displayName = "Mohammed"
        )
        assertEquals("Hey @Mohammed  how are you", updated)
        assertEquals(14, cursor)
    }

    @Test
    fun buildOutboundMentionPayloadMapsDisplayTokenToProtocolJidUser() {
        val (wireText, csv) = buildOutboundMentionPayload(
            text = "Hi @Mohammed check this",
            selectedMentions = mapOf("@Mohammed" to "245281300001@lid")
        )
        assertEquals("Hi @245281300001 check this", wireText)
        assertEquals("245281300001@lid", csv)
    }

    @Test
    fun resolveMentionsInTextResolvesGroupEveryoneMention() {
        val resolved = resolveMentionsInText(
            text = "Attention @120363044556677889 please",
            mentionsCsv = "120363044556677889@g.us",
            resolveName = { null }
        )
        assertEquals("Attention @everyone please", resolved)
    }
}
