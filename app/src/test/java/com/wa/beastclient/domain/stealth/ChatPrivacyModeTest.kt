package com.wa.beastclient.domain.stealth

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPrivacyModeTest {

    @Test
    fun maskChatTitleIfHiddenMasksDmGroupAndChannelOnlyWhenEnabled() {
        assertEquals(
            "Alice",
            StealthMatrixManager.maskChatTitleIfHidden("Alice", isGroup = false, isChannel = false, hideNames = false)
        )
        assertEquals(
            "Private Chat",
            StealthMatrixManager.maskChatTitleIfHidden("Alice", isGroup = false, isChannel = false, hideNames = true)
        )
        assertEquals(
            "Private Group",
            StealthMatrixManager.maskChatTitleIfHidden("Dev Team", isGroup = true, isChannel = false, hideNames = true)
        )
        assertEquals(
            "Private Channel",
            StealthMatrixManager.maskChatTitleIfHidden("News", isGroup = false, isChannel = true, hideNames = true)
        )
    }

    @Test
    fun maskGroupSenderIfHiddenReplacesSenderNameWhenEnabled() {
        assertEquals("Bob", StealthMatrixManager.maskGroupSenderIfHidden("Bob", hideNames = false))
        assertEquals("Member", StealthMatrixManager.maskGroupSenderIfHidden("Bob", hideNames = true))
    }
}
