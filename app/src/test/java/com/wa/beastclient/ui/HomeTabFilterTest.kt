package com.wa.beastclient.ui

import com.wa.beastclient.data.db.ChatEntity
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
}
