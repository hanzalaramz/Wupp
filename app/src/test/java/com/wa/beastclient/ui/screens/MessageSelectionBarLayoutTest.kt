package com.wa.beastclient.ui.screens

import com.wa.beastclient.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSelectionBarLayoutTest {

    private fun msg(
        isFromMe: Boolean = false,
        msgType: String = "TEXT",
        body: String = "hello",
        localMediaPath: String? = null,
        isEdited: Boolean = false
    ) = MessageEntity(
        id = "m1",
        chatJid = "12345@s.whatsapp.net",
        senderJid = if (isFromMe) "me@s.whatsapp.net" else "12345@s.whatsapp.net",
        senderName = "Sender",
        isFromMe = isFromMe,
        timestamp = 1000L,
        body = body,
        msgType = msgType,
        localMediaPath = localMediaPath,
        isEdited = isEdited
    )

    // MESSAGE_INFO is now placed right after FORWARD so it appears directly in the top bar.
    // For short lists (<=6) every action is direct; for longer lists the first 4 non-Delete
    // actions are direct plus Delete, the rest overflow.

    @Test
    fun outgoingTextInDmHasMessageInfoDirectlyVisible() {
        // available: REPLY, FORWARD, MESSAGE_INFO, PIN_UNPIN, EDIT, COPY, DELETE (7 items → overflow)
        val layout = resolveSelectionBarLayout(
            msg = msg(isFromMe = true, msgType = "TEXT", body = "hi"),
            isChannel = false,
            isGroupAnnounceLocked = false,
            isGroup = false,
            isDocument = false
        )
        assertEquals(
            listOf(
                SelectionBarAction.REPLY,
                SelectionBarAction.FORWARD,
                SelectionBarAction.MESSAGE_INFO,
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertEquals(
            listOf(
                SelectionBarAction.EDIT,
                SelectionBarAction.COPY
            ),
            layout.overflowActions
        )
    }

    @Test
    fun incomingMediaWithoutCaptionShowsMessageInfoDirectly() {
        // available: REPLY, FORWARD, MESSAGE_INFO, PIN_UNPIN, SAVE_MEDIA, DELETE (6 items → all direct)
        val layout = resolveSelectionBarLayout(
            msg = msg(isFromMe = false, msgType = "IMAGE", body = "", localMediaPath = "/tmp/a.jpg"),
            isChannel = false,
            isGroupAnnounceLocked = false,
            isGroup = false,
            isDocument = false
        )
        assertEquals(
            listOf(
                SelectionBarAction.REPLY,
                SelectionBarAction.FORWARD,
                SelectionBarAction.MESSAGE_INFO,
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.SAVE_MEDIA,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertTrue(layout.overflowActions.isEmpty())
    }

    @Test
    fun groupEditedOutgoingTextKeepsFiveDirectIconsIncludingDeleteAndOverflowsExtras() {
        // available: REPLY, FORWARD, MESSAGE_INFO, PIN_UNPIN, EDIT, COPY, EDIT_HISTORY, DELETE (8 items)
        val layout = resolveSelectionBarLayout(
            msg = msg(isFromMe = true, msgType = "TEXT", body = "edited", isEdited = true),
            isChannel = false,
            isGroupAnnounceLocked = false,
            isGroup = true,
            isDocument = false
        )
        assertEquals(
            listOf(
                SelectionBarAction.REPLY,
                SelectionBarAction.FORWARD,
                SelectionBarAction.MESSAGE_INFO,
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertEquals(
            listOf(
                SelectionBarAction.EDIT,
                SelectionBarAction.COPY,
                SelectionBarAction.EDIT_HISTORY
            ),
            layout.overflowActions
        )
    }

    @Test
    fun documentWithCaptionInDmKeepsDirectBarCappedAndOverflowsLowerPriorityActions() {
        // available: REPLY, FORWARD, MESSAGE_INFO, PIN_UNPIN, SAVE_MEDIA, COPY, OPEN_DOCUMENT, DELETE (8 items)
        val layout = resolveSelectionBarLayout(
            msg = msg(isFromMe = false, msgType = "DOCUMENT", body = "report.pdf", localMediaPath = "/tmp/report.pdf"),
            isChannel = false,
            isGroupAnnounceLocked = false,
            isGroup = false,
            isDocument = true
        )
        assertEquals(
            listOf(
                SelectionBarAction.REPLY,
                SelectionBarAction.FORWARD,
                SelectionBarAction.MESSAGE_INFO,
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertEquals(
            listOf(
                SelectionBarAction.SAVE_MEDIA,
                SelectionBarAction.COPY,
                SelectionBarAction.OPEN_DOCUMENT
            ),
            layout.overflowActions
        )
    }
}
