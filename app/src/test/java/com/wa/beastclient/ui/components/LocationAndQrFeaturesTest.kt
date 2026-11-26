package com.wa.beastclient.ui.components

import com.wa.beastclient.ui.screens.resolveCallMessageIsVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndQrFeaturesTest {

    @Test
    fun parseLocationPayloadParsesLabeledAndUnlabeledCoordinates() {
        val labeled = parseLocationPayload(
            "31.49058, 74.29698 (Islamic Research Center, Quaid-i-Azam Campus, Lahore, Pakistan)"
        )
        assertNotNull(labeled)
        assertEquals(31.49058, labeled!!.latitude, 0.0001)
        assertEquals(74.29698, labeled.longitude, 0.0001)
        assertEquals("Islamic Research Center", labeled.title)
        assertTrue(labeled.address.contains("Lahore"))
        assertFalse(labeled.isLive)

        val dashSplit = parseLocationPayload(
            "31.49058, 74.29698 (Home — Street 4, Lahore)"
        )
        assertNotNull(dashSplit)
        assertEquals("Home", dashSplit!!.title)
        assertEquals("Street 4, Lahore", dashSplit.address)

        val bare = parseLocationPayload("24.86070, 67.00110")
        assertNotNull(bare)
        assertEquals("Shared location", bare!!.title)
        assertEquals("24.86070, 67.00110", bare.address)

        val live = parseLocationPayload("24.86070, 67.00110 (Live location)", "application/x-wa-location-live")
        assertNotNull(live)
        assertTrue(live!!.isLive)

        assertNull(parseLocationPayload("Hello world"))
        assertNull(parseLocationPayload("999.0, 200.0 (Invalid)"))
    }

    @Test
    fun parseLocationInputExtractsMapsLinksAndRawCoordinates() {
        val fromLink = parseLocationInput("https://maps.google.com/?q=31.49058,74.29698")
        assertNotNull(fromLink)
        assertEquals(31.49058, fromLink!!.latitude, 0.0001)
        assertEquals(74.29698, fromLink.longitude, 0.0001)

        val fromGeo = parseLocationInput("geo:31.5204,74.3587?q=31.5204,74.3587(Lahore)")
        assertNotNull(fromGeo)
        assertEquals(31.5204, fromGeo!!.latitude, 0.001)
        assertEquals(74.3587, fromGeo.longitude, 0.001)
    }

    @Test
    fun buildLocationBodyTextFormatsCleanSummary() {
        assertEquals(
            "31.49058, 74.29698 (Campus — Lahore)",
            buildLocationBodyText(31.49058, 74.29698, "Campus", "Lahore")
        )
        assertEquals(
            "31.49058, 74.29698",
            buildLocationBodyText(31.49058, 74.29698, "", "")
        )
    }

    @Test
    fun parseContactQrPayloadSupportsWaMeVCardMecardAndPhone() {
        val waMe = parseContactQrPayload("https://wa.me/923001234567?text=hi")
        assertNotNull(waMe)
        assertEquals("923001234567", waMe!!.phoneDigits)
        assertEquals("+923001234567", waMe.displayPhone)
        assertEquals("923001234567@s.whatsapp.net", waMe.jid)

        val vcard = parseContactQrPayload(
            "BEGIN:VCARD\nVERSION:3.0\nFN:Ali Khan\nTEL;TYPE=CELL:+92 300 123 4567\nEND:VCARD"
        )
        assertNotNull(vcard)
        assertEquals("Ali Khan", vcard!!.name)
        assertEquals("923001234567", vcard.phoneDigits)

        val mecard = parseContactQrPayload("MECARD:N:Sara;TEL:+14155552671;;")
        assertNotNull(mecard)
        assertEquals("Sara", mecard!!.name)
        assertEquals("14155552671", mecard.phoneDigits)

        val rawPhone = parseContactQrPayload("+92 321 7654321")
        assertNotNull(rawPhone)
        assertEquals("923217654321", rawPhone!!.phoneDigits)

        assertNull(parseContactQrPayload("https://example.com/about"))
        assertNull(parseContactQrPayload(""))
    }

    @Test
    fun buildMyContactQrContentBuildsWaMeLinkFromPhoneOrJid() {
        assertEquals("https://wa.me/923001234567", buildMyContactQrContent("923001234567@s.whatsapp.net"))
        assertEquals("", buildMyContactQrContent("invalid"))
    }

    @Test
    fun resolveCallMessageIsVideoDetectsVoiceVsVideoCalls() {
        assertFalse(resolveCallMessageIsVideo("Voice call|No answer"))
        assertFalse(resolveCallMessageIsVideo("Missed voice call|"))
        assertTrue(resolveCallMessageIsVideo("Video call|2 min"))
        assertTrue(resolveCallMessageIsVideo("Missed video call|No answer"))
    }
}
