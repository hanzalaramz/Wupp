package com.wa.beastclient.domain.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceContactFallbackResolverTest {

    @Test
    fun serverNameMissingDetectsEmptyRawJidsAndPhoneNumbersOnly() {
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("923001234567", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("+923001234567", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("+92 300 1234567", "112233@lid", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("~ User · …4567", "112233@lid"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing(".", "112233@lid"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("...", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("-", "923001234567@s.whatsapp.net"))
        assertTrue(DeviceContactFallbackResolver.isServerNameMissing("~", "923001234567@s.whatsapp.net"))

        assertFalse(DeviceContactFallbackResolver.isServerNameMissing("Alice Khan", "923001234567@s.whatsapp.net"))
        assertFalse(DeviceContactFallbackResolver.isServerNameMissing("Mom", "923001234567@s.whatsapp.net"))
        assertFalse(DeviceContactFallbackResolver.isServerNameMissing("Mramzan1", "923001234567@s.whatsapp.net"))
    }

    @Test
    fun deviceContactIndexMatchesExactNationalLeadingZeroAndInternationalNumbers() {
        val index = DeviceContactIndex.fromEntries(
            listOf(
                "0300 1234567" to "Mom",
                "+1 (415) 555-0199" to "Sam Work"
            )
        )
        assertEquals("Mom", index.lookup("923001234567@s.whatsapp.net"))
        assertEquals("Mom", index.lookup("+923001234567"))
        assertEquals("Sam Work", index.lookup("14155550199@s.whatsapp.net"))
        assertNull(index.lookup("929998887777@s.whatsapp.net"))
        assertNull(index.lookup("12345678901234@lid"))
    }

    @Test
    fun deviceContactIndexRefusesAmbiguousSuffixCollisions() {
        val index = DeviceContactIndex.fromEntries(
            listOf(
                "+1 300 1234567" to "US Person",
                "+44 300 1234567" to "UK Person"
            )
        )
        assertEquals("US Person", index.lookup("13001234567@s.whatsapp.net"))
        assertEquals("UK Person", index.lookup("443001234567@s.whatsapp.net"))
        assertNull(index.lookup("923001234567@s.whatsapp.net"))
    }

    @Test
    fun groupJidLikeIdentifierDetectsGroupJidsAndRejectsRealNames() {
        assertTrue(
            DeviceContactFallbackResolver.isGroupJidLikeIdentifier(
                "120363401989542982@g.us",
                "120363401989542982@g.us"
            )
        )
        assertTrue(
            DeviceContactFallbackResolver.isGroupJidLikeIdentifier(
                "120363401989542982",
                "120363401989542982@g.us"
            )
        )
        assertTrue(
            DeviceContactFallbackResolver.isGroupJidLikeIdentifier(
                "923348882974-1595838950",
                "923348882974-1595838950@g.us"
            )
        )
        assertTrue(
            DeviceContactFallbackResolver.isGroupJidLikeIdentifier(
                "923348882974-1595838950"
            )
        )
        assertFalse(DeviceContactFallbackResolver.isGroupJidLikeIdentifier("Talha", "120363401989542982@g.us"))
        assertFalse(DeviceContactFallbackResolver.isGroupJidLikeIdentifier("Mom"))
        assertFalse(DeviceContactFallbackResolver.isGroupJidLikeIdentifier("923001234567"))
    }

    @Test
    fun formatIntlPhoneCompactFormatsNumbersAndRejectsInvalidInput() {
        assertEquals("+92 300 1234567", DeviceContactFallbackResolver.formatIntlPhoneCompact("923001234567"))
        assertEquals("+92 300 1234567", DeviceContactFallbackResolver.formatIntlPhoneCompact("+92 300-1234567"))
        assertEquals("+1 (415) 555-0199", DeviceContactFallbackResolver.formatIntlPhoneCompact("14155550199"))
        assertEquals("", DeviceContactFallbackResolver.formatIntlPhoneCompact("123"))
        assertEquals("", DeviceContactFallbackResolver.formatIntlPhoneCompact(""))
    }
}
