package com.wa.beastclient.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAdminComposerTest {

    @Test
    fun matchesOwnJidIgnoringDeviceSuffix() {
        assertTrue(
            isSelfInGroupAdminSet(
                adminJids = setOf("15551234567:2@s.whatsapp.net"),
                ownJid = "15551234567@s.whatsapp.net"
            )
        )
    }

    @Test
    fun returnsFalseWhenNotInAdminSet() {
        assertFalse(
            isSelfInGroupAdminSet(
                adminJids = setOf("15559998888@s.whatsapp.net"),
                ownJid = "15551234567@s.whatsapp.net"
            )
        )
    }

    @Test
    fun returnsFalseWhenOwnJidIsBlank() {
        assertFalse(
            isSelfInGroupAdminSet(
                adminJids = setOf("15551234567@s.whatsapp.net"),
                ownJid = ""
            )
        )
    }
}
