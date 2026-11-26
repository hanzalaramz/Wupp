package com.wa.beastclient.domain.drafts

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDraftManagerTest {

    @Test
    fun normalizeDraftTextClearsWhitespaceOnlyAndPreservesNonBlankDrafts() {
        assertEquals("", normalizeDraftText(""))
        assertEquals("", normalizeDraftText("   \n\t  "))
        assertEquals("Hey there ", normalizeDraftText("Hey there "))
        assertEquals("Line 1\nLine 2", normalizeDraftText("Line 1\nLine 2"))
    }
}
