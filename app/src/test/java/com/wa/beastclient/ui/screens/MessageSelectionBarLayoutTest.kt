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

    @Test
    fun outgoingTextInDmShowsAllSixIconsDirectlyWithoutOverflow() {
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
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.EDIT,
                SelectionBarAction.COPY,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertTrue(layout.overflowActions.isEmpty())
    }

    @Test
    fun incomingMediaWithoutCaptionShowsForwardPinAndSaveDirectly() {
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
                SelectionBarAction.PIN_UNPIN,
                SelectionBarAction.EDIT,
                SelectionBarAction.DELETE
            ),
            layout.directActions
        )
        assertEquals(
            listOf(
                SelectionBarAction.COPY,
                SelectionBarAction.MESSAGE_INFO,
                SelectionBarAction.EDIT_HISTORY
            ),
            layout.overflowActions
        )
    }

    @Test
    fun documentWithCaptionInDmKeepsDirectBarCappedAndIncludesForward() {
        val layout = resolveSelectionBarLayout(
            msg = msg(isFromMe = false, msgType = "DOCUMENT", body = "report.pdf", localMediaPath = "/tmp/report.pdf"),
            isChannel = false,
            isGroupAnnounceLocked = false,
            isGroup = false,
            isDocument = true
        )
        assertEquals(5, layout.directActions.size)
        assertTrue(layout.directActions.contains(SelectionBarAction.FORWARD))
        assertTrue(layout.directActions.contains(SelectionBarAction.SAVE_MEDIA))
        assertEquals(listOf(SelectionBarAction.COPY, SelectionBarAction.OPEN_DOCUMENT), layout.overflowActions)
    }
}
