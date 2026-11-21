package com.wa.beastclient.ui

import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.ui.screens.resolveChatSubtitlePreview
import com.wa.beastclient.ui.screens.resolveHomeTabOrder
import com.wa.beastclient.ui.screens.resolvePagerIndexForTab
import com.wa.beastclient.ui.screens.resolveTabIdForPagerIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabFilterTest {

    private val dm1 = ChatEntity(jid = "111@s.whatsapp.net", name = "Alice", isGroup = false, unreadCount = 2, lastMessagePreview = "Hey")
    private val dmLocked = ChatEntity(jid = "222@s.whatsapp.net", name = "Secret DM", isGroup = false, isLocked = true, lastMessagePreview = "Secret")
    private val phantomDm = ChatEntity(jid = "333@lid", name = "333", isGroup = false)
    private val grp1 = ChatEntity(jid = "999@g.us", name = "Dev Group", isGroup = true, unreadCount = 5, lastMessagePreview = "Build ok")
    private val grpLocked = ChatEntity(jid = "888@g.us", name = "Hidden Group", isGroup = true, isLocked = true, lastMessagePreview = "Locked")
    private val channel = ChatEntity(jid = "123@newsletter", name = "Tech News", isGroup = false, lastMessagePreview = "Update")
    private val communityParent = ChatEntity(jid = "777@g.us", name = "Community", isGroup = true, isCommunityParent = true)

    private val sampleList = listOf(dm1, dmLocked, phantomDm, grp1, grpLocked, channel, communityParent)

    @Test
    fun unifiedMode_returnsDmsAndGroupsTogether() {
        val result = filterHomeThreads(sampleList, HomeFilterChip.ALL, query = "", hideLocked = true, groupFilter = null)
        assertEquals(listOf(dm1, grp1), result)

        val groupsOnlyChip = filterHomeThreads(sampleList, HomeFilterChip.GROUPS, query = "", hideLocked = false, groupFilter = null)
        assertEquals(listOf(grp1, grpLocked), groupsOnlyChip)
    }

    @Test
    fun separatedMode_splitsDmsAndGroupsCleanly() {
        val dmsOnly = filterHomeThreads(sampleList, HomeFilterChip.ALL, query = "", hideLocked = true, groupFilter = false)
        assertEquals(listOf(dm1), dmsOnly)

        val dmsWithGroupsChipFallsBack = filterHomeThreads(sampleList, HomeFilterChip.GROUPS, query = "", hideLocked = false, groupFilter = false)
        assertEquals(listOf(dm1, dmLocked), dmsWithGroupsChipFallsBack)

        val groupsOnly = filterHomeThreads(sampleList, HomeFilterChip.ALL, query = "", hideLocked = true, groupFilter = true)
        assertEquals(listOf(grp1), groupsOnly)

        val groupsUnread = filterHomeThreads(sampleList, HomeFilterChip.UNREAD, query = "dev", hideLocked = false, groupFilter = true)
        assertEquals(listOf(grp1), groupsUnread)
    }

    @Test
    fun homeTabPagerMapping_handlesUnifiedAndSeparatedLayouts() {
        val unified = resolveHomeTabOrder(separateGroupsAndChats = false)
        assertEquals(listOf(0, 1, 2, 5, 3), unified)
        assertEquals(1, resolvePagerIndexForTab(unified, 1))
        assertEquals(0, resolvePagerIndexForTab(unified, 4))
        assertEquals(3, resolvePagerIndexForTab(unified, 5))
        assertEquals(4, resolvePagerIndexForTab(unified, 3))
        assertEquals(2, resolveTabIdForPagerIndex(unified, 2))
        assertEquals(5, resolveTabIdForPagerIndex(unified, 3))
        assertEquals(0, resolveTabIdForPagerIndex(unified, 99))

        val separated = resolveHomeTabOrder(separateGroupsAndChats = true)
        assertEquals(listOf(0, 4, 1, 2, 5, 3), separated)
        assertEquals(1, resolvePagerIndexForTab(separated, 4))
        assertEquals(2, resolvePagerIndexForTab(separated, 1))
        assertEquals(4, resolvePagerIndexForTab(separated, 5))
        assertEquals(5, resolvePagerIndexForTab(separated, 3))
        assertEquals(4, resolveTabIdForPagerIndex(separated, 1))
        assertEquals(5, resolveTabIdForPagerIndex(separated, 4))
        assertEquals(3, resolveTabIdForPagerIndex(separated, 5))
    }

    @Test
    fun filterHomeThreads_matchesGroupLastMessageSenderName() {
        val grpWithSender = grp1.copy(lastMessageSenderName = "Sarah Jenkins", lastMessagePreview = "Meeting at 3")
        val sample = listOf(dm1, grpWithSender)

        val matchSender = filterHomeThreads(sample, HomeFilterChip.ALL, query = "sarah", hideLocked = true)
        assertEquals(listOf(grpWithSender), matchSender)

        val noMatch = filterHomeThreads(sample, HomeFilterChip.ALL, query = "robert", hideLocked = true)
        assertEquals(emptyList<ChatEntity>(), noMatch)
    }

    @Test
    fun resolveChatSubtitlePreview_prefixesGroupSenderNameAccurately() {
        val grpFromOther = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Hey team",
            lastMessageFromMe = false
        )
        assertEquals("Sarah: Hey team", resolveChatSubtitlePreview(grpFromOther))

        val grpFromMe = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Hey team",
            lastMessageFromMe = true
        )
        assertEquals("Hey team", resolveChatSubtitlePreview(grpFromMe))

        val grpWithPresence = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Hey team",
            chatPresence = "typing..."
        )
        assertEquals("typing...", resolveChatSubtitlePreview(grpWithPresence))

        val dm = dm1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Hey Alice",
            isGroup = false
        )
        assertEquals("Hey Alice", resolveChatSubtitlePreview(dm))

        val grpReaction = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Sarah reacted 👍 to \"Meeting at 3\"",
            lastMessageFromMe = false
        )
        assertEquals("Sarah reacted 👍 to \"Meeting at 3\"", resolveChatSubtitlePreview(grpReaction))

        val grpPhotoFallback = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessageType = "IMAGE",
            lastMessagePreview = "",
            lastMessageFromMe = false
        )
        assertEquals("Sarah: 📷 Photo", resolveChatSubtitlePreview(grpPhotoFallback))

        val grpAlreadyPrefixed = grp1.copy(
            lastMessageSenderName = "Sarah",
            lastMessagePreview = "Sarah: Already prefixed",
            lastMessageFromMe = false
        )
        assertEquals("Sarah: Already prefixed", resolveChatSubtitlePreview(grpAlreadyPrefixed))
    }

    @Test
    fun filterHomeThreads_ordersByPinThenLatestMessageIgnoringStaleUnpinnedPinOrder() {
        val olderWithStalePinOrder = ChatEntity(
            jid = "111@s.whatsapp.net",
            name = "Zarr",
            isPinned = false,
            pinOrder = 9_999_999_999L,
            lastMessageTimestamp = 1_700_000_100_000L,
            lastMessagePreview = "Earlier msg"
        )
        val newestUnpinned = ChatEntity(
            jid = "222@g.us",
            name = "M A Q S A D",
            isGroup = true,
            isPinned = false,
            pinOrder = 0L,
            lastMessageTimestamp = 1_700_000_900_000L,
            lastMessagePreview = "Latest response"
        )
        val midUnpinned = ChatEntity(
            jid = "333@s.whatsapp.net",
            name = "Ufone",
            isPinned = false,
            pinOrder = 5_000_000L,
            lastMessageTimestamp = 1_700_000_500_000L,
            lastMessagePreview = "Voice message"
        )
        val pinnedOlder = ChatEntity(
            jid = "444@g.us",
            name = "Pinned Group",
            isGroup = true,
            isPinned = true,
            pinOrder = 100L,
            lastMessageTimestamp = 1_699_000_000_000L,
            lastMessagePreview = "Pinned"
        )

        val sorted = filterHomeThreads(
            listOf(olderWithStalePinOrder, midUnpinned, newestUnpinned, pinnedOlder),
            HomeFilterChip.ALL,
            query = "",
            hideLocked = true
        )
        assertEquals(
            listOf(pinnedOlder, newestUnpinned, midUnpinned, olderWithStalePinOrder),
            sorted
        )
    }

    @Test
    fun canPinMoreChats_enforcesThreePinnedChatLimitAcrossDmsAndGroups() {
        val pinned1 = dm1.copy(jid = "1@s.whatsapp.net", isPinned = true)
        val pinned2 = grp1.copy(jid = "2@g.us", isPinned = true)
        val pinned3 = dm1.copy(jid = "3@s.whatsapp.net", isPinned = true)
        val pinnedChannel = channel.copy(jid = "123@newsletter", isPinned = true)
        val unpinned4 = dm1.copy(jid = "4@s.whatsapp.net", isPinned = false)
        val unpinned5 = dm1.copy(jid = "5@s.whatsapp.net", isPinned = false)

        // Two pinned chats + a pinned channel still leaves 1 regular pin slot.
        val twoPinned = listOf(pinned1, pinned2, pinnedChannel, unpinned4, unpinned5)
        assertEquals(true, canPinMoreChats(twoPinned, listOf("4@s.whatsapp.net")))
        assertEquals(false, canPinMoreChats(twoPinned, listOf("4@s.whatsapp.net", "5@s.whatsapp.net")))

        // Three regular pinned chats block a 4th chat pin while still allowing channels / re-pinning existing.
        val threePinned = listOf(pinned1, pinned2, pinned3, pinnedChannel, unpinned4)
        assertEquals(false, canPinMoreChats(threePinned, listOf("4@s.whatsapp.net")))
        assertEquals(true, canPinMoreChats(threePinned, listOf("1@s.whatsapp.net")))
        assertEquals(true, canPinMoreChats(threePinned, listOf("999@newsletter")))
    }

    @Test
    fun resolveChatSubtitlePreview_stripsGroupJidsAndRawIdentifiersFromSenderPrefix() {
        val groupWith120363 = ChatEntity(
            jid = "120363401989542982@g.us",
            name = "LUMS Hotline",
            isGroup = true,
            lastMessageSenderName = "120363401989542982",
            lastMessagePreview = "There's a reason",
            lastMessageFromMe = false
        )
        assertEquals("There's a reason", resolveChatSubtitlePreview(groupWith120363))

        val legacyGroup = ChatEntity(
            jid = "923348882974-1595838950@g.us",
            name = "FUKREY",
            isGroup = true,
            lastMessageSenderName = "923348882974-1595838950",
            lastMessagePreview = "Dhulan to dhikha",
            lastMessageFromMe = false
        )
        assertEquals("Dhulan to dhikha", resolveChatSubtitlePreview(legacyGroup))

        val lidSender = grp1.copy(
            lastMessageSenderName = "1122334455@lid",
            lastMessagePreview = "Hello",
            lastMessageFromMe = false
        )
        assertEquals("Hello", resolveChatSubtitlePreview(lidSender))

        val realSender = groupWith120363.copy(
            lastMessageSenderName = "Ahmad Akhtar",
            lastMessagePreview = "Photo",
            lastMessageFromMe = false
        )
        assertEquals("Ahmad Akhtar: Photo", resolveChatSubtitlePreview(realSender))
    }
}
