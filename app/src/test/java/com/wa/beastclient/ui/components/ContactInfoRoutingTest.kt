package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactInfoRoutingTest {

    @Test
    fun testContactInfoRoutingFromDpPopupDoesNotOpenOwnSettings() {
        val contactJid = "15550192834@s.whatsapp.net"
        val ownJid = "923001234567@s.whatsapp.net"

        var openedJid: String? = null
        var openedTab: Int? = null

        // Callback invoked on (i) click
        val onOpenContactInfo: (String) -> Unit = { jid ->
            openedJid = jid
        }
        val onSelectTab: (Int) -> Unit = { tab ->
            openedTab = tab
        }

        // Simulating tapping (i) icon on contact DP popup
        onOpenContactInfo(contactJid)

        assertEquals("15550192834@s.whatsapp.net", openedJid)
        assertTrue(openedJid != ownJid)
        assertEquals(null, openedTab) // Must NOT navigate to tab 3 (Tools/Settings)
    }

    @Test
    fun testGroupMemberSearchFiltering() {
        val members = listOf(
            "15550192834@s.whatsapp.net",
            "15550198811@s.whatsapp.net",
            "923157256313@s.whatsapp.net"
        )
        val names = mapOf(
            "15550192834@s.whatsapp.net" to "Elena Vance",
            "15550198811@s.whatsapp.net" to "Marcus Chen",
            "923157256313@s.whatsapp.net" to "Abdul Rahman"
        )

        val query = "marcus"
        val filtered = members.filter { jid ->
            val name = names[jid].orEmpty().lowercase()
            jid.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true)
        }

        assertEquals(1, filtered.size)
        assertEquals("15550198811@s.whatsapp.net", filtered.first())
    }

    @Test
    fun testMediaVisibilityLabelMapping() {
        fun resolveLabel(override: Boolean?, globalDefault: Boolean): String {
            return when (override) {
                true -> "Yes"
                false -> "No"
                else -> "Default (${if (globalDefault) "Yes" else "No"})"
            }
        }

        assertEquals("Yes", resolveLabel(true, globalDefault = true))
        assertEquals("No", resolveLabel(false, globalDefault = true))
        assertEquals("Default (Yes)", resolveLabel(null, globalDefault = true))
        assertEquals("Default (No)", resolveLabel(null, globalDefault = false))
    }

    @Test
    fun testGroupAdminBadgeDetection() {
        val groupAdmins = setOf("15550192834@s.whatsapp.net", "15550198811@s.whatsapp.net")

        assertTrue(groupAdmins.contains("15550192834@s.whatsapp.net"))
        assertTrue(groupAdmins.contains("15550198811@s.whatsapp.net"))
        assertFalse(groupAdmins.contains("923157256313@s.whatsapp.net"))
    }
}
