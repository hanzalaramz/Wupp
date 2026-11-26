package com.wa.beastclient.ui.components

import com.wa.beastclient.data.db.ChatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextSelectionMenuTest {

    private fun chat(
        jid: String,
        name: String = "Contact",
        isPinned: Boolean = false,
        isMuted: Boolean = false,
        isArchived: Boolean = false,
        isFavorite: Boolean = false,
        unreadCount: Int = 0
    ) = ChatEntity(
        jid = jid,
        name = name,
        isPinned = isPinned,
        isMuted = isMuted,
        isArchived = isArchived,
        isFavorite = isFavorite,
        unreadCount = unreadCount
    )

    @Test
    fun longPressStartsSingleSelectionAndDirectTapEntersMultiSelect() {
        val alice = chat("111@s.whatsapp.net", "Alice")
        val bob = chat("222@s.whatsapp.net", "Bob")
        val carol = chat("333@s.whatsapp.net", "Carol")
        val map = listOf(alice, bob, carol).associateBy { it.jid }

        val first = applyChatLongPress(ChatSelectionState(), alice, map)
        assertEquals(alice.jid, first.singleTarget?.jid)
        assertTrue(first.multiSelectedJids.isEmpty())
        assertEquals(1, first.selectedCount)
        assertTrue(first.isSelected(alice.jid))

        val second = applyChatTapInSelection(first, bob, map)
        assertNull(second.singleTarget)
        assertEquals(setOf(alice.jid, bob.jid), second.multiSelectedJids)
        assertEquals(2, second.selectedCount)

        val third = applyChatLongPress(second, carol, map)
        assertNull(third.singleTarget)
        assertEquals(setOf(alice.jid, bob.jid, carol.jid), third.multiSelectedJids)
        assertEquals(3, third.selectedCount)
    }

    @Test
    fun untappingDownToOneRestoresSingleSelectionAndUntappingLastClears() {
        val alice = chat("111@s.whatsapp.net", "Alice")
        val bob = chat("222@s.whatsapp.net", "Bob")
        val map = listOf(alice, bob).associateBy { it.jid }

        val twoSelected = ChatSelectionState(multiSelectedJids = setOf(alice.jid, bob.jid))
        val backToOne = applyChatTapInSelection(twoSelected, bob, map)
        assertEquals(alice.jid, backToOne.singleTarget?.jid)
        assertTrue(backToOne.multiSelectedJids.isEmpty())
        assertEquals(1, backToOne.selectedCount)

        val cleared = applyChatTapInSelection(backToOne, alice, map)
        assertNull(cleared.singleTarget)
        assertTrue(cleared.multiSelectedJids.isEmpty())
        assertFalse(cleared.hasAnySelection)
    }

    @Test
    fun channelMenuOmitsQuickControlsWhileDmMenuIncludesAllActions() {
        val dmLayout = resolveChatContextMenuLayout(chat("111@s.whatsapp.net", "Alice"))
        assertTrue(ChatContextMenuAction.QUICK_CONTROLS in dmLayout.moreActions)
        assertTrue(ChatContextMenuAction.DELETE in dmLayout.primaryActions)
        assertTrue(ChatContextMenuAction.INFO in dmLayout.primaryActions)

        val channelLayout = resolveChatContextMenuLayout(chat("news1@newsletter", "Channel"))
        assertFalse(ChatContextMenuAction.QUICK_CONTROLS in channelLayout.primaryActions)
        assertFalse(ChatContextMenuAction.QUICK_CONTROLS in channelLayout.moreActions)
        assertTrue(ChatContextMenuAction.ARCHIVE_UNARCHIVE in channelLayout.moreActions)
        assertTrue(ChatContextMenuAction.FAVORITE_UNFAVORITE in channelLayout.moreActions)
    }
}
