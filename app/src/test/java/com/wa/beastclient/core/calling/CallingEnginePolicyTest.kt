package com.wa.beastclient.core.calling

import com.wa.beastclient.domain.stealth.CallBackendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingEnginePolicyTest {

    @Test
    fun buildChromiumMatchedDesktopUaPreservesInstalledChromeToken() {
        val deviceUa =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP3A) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.135 Mobile Safari/537.36"
        val desktopUa = buildChromiumMatchedDesktopUa(deviceUa)

        assertTrue(desktopUa.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(desktopUa.contains("Chrome/134.0.6998.135"))
        assertFalse(desktopUa.contains("Android"))
    }

    @Test
    fun buildChromiumMatchedDesktopUaFallsBackWhenTokenMissing() {
        val desktopUa = buildChromiumMatchedDesktopUa("CustomWebView/1.0")
        assertTrue(desktopUa.contains("Chrome/131.0.0.0"))
    }

    @Test
    fun isAllowedWhatsAppPermissionOriginAllowsOnlyHttpsWhatsAppHosts() {
        assertTrue(isAllowedWhatsAppPermissionOrigin("https://web.whatsapp.com"))
        assertTrue(isAllowedWhatsAppPermissionOrigin("https://web.whatsapp.com/"))
        assertTrue(isAllowedWhatsAppPermissionOrigin("https://whatsapp.com"))
        assertTrue(isAllowedWhatsAppPermissionOrigin("https://dyn.web.whatsapp.com"))

        assertFalse(isAllowedWhatsAppPermissionOrigin(null))
        assertFalse(isAllowedWhatsAppPermissionOrigin(""))
        assertFalse(isAllowedWhatsAppPermissionOrigin("http://web.whatsapp.com"))
        assertFalse(isAllowedWhatsAppPermissionOrigin("https://whatsapp.com.attacker.test"))
        assertFalse(isAllowedWhatsAppPermissionOrigin("https://notwhatsapp.com"))
    }

    @Test
    fun extractDialablePhoneDigitsPrefersPnJidAndFallsBackPastLid() {
        assertEquals(
            "15551234567",
            extractDialablePhoneDigits("15551234567@s.whatsapp.net")
        )
        assertEquals(
            "447700900123",
            extractDialablePhoneDigits(
                primaryJid = "1029384756@lid",
                altJid = "447700900123:2@s.whatsapp.net"
            )
        )
        assertEquals(
            "923001234567",
            extractDialablePhoneDigits(
                primaryJid = "1029384756@lid",
                resolvedPhone = "+92 300 1234567"
            )
        )
        assertEquals(
            "",
            extractDialablePhoneDigits(
                primaryJid = "1029384756@lid",
                resolvedPhone = "1029384756@lid"
            )
        )
        assertEquals(
            "",
            extractDialablePhoneDigits(primaryJid = "120363012345678901@g.us")
        )
    }

    @Test
    fun isColdCallBlockedProtectsUnsavedZeroHistoryDirectChats() {
        assertTrue(isColdCallBlocked(isSavedContact = false, messageCount = 0, isGroup = false))
        assertFalse(isColdCallBlocked(isSavedContact = true, messageCount = 0, isGroup = false))
        assertFalse(isColdCallBlocked(isSavedContact = false, messageCount = 1, isGroup = false))
        assertFalse(isColdCallBlocked(isSavedContact = false, messageCount = 0, isGroup = true))
    }

    @Test
    fun formatCallTimerFormatsMinutesAndHoursCleanly() {
        assertEquals("00:00", formatCallTimer(-5))
        assertEquals("00:00", formatCallTimer(0))
        assertEquals("01:05", formatCallTimer(65))
        assertEquals("1:01:01", formatCallTimer(3661))
    }

    @Test
    fun callBackendModeParsesStorageKeysWithSafeDefault() {
        assertEquals(CallBackendMode.OFF, CallBackendMode.fromStorageKey(null))
        assertEquals(CallBackendMode.OFF, CallBackendMode.fromStorageKey("unknown"))
        assertEquals(CallBackendMode.SMART_WARM, CallBackendMode.fromStorageKey("SMART_WARM"))
        assertEquals(CallBackendMode.ALWAYS_READY, CallBackendMode.fromStorageKey("always_ready"))
        assertTrue(CallBackendMode.ALWAYS_READY.title.contains("Recommended"))
    }

    @Test
    fun shouldDismissCallFromGoSignalMatchesCallIdCorrectly() {
        val idle = CallUiState(phase = CallPhase.IDLE, callId = "call-1")
        assertFalse(shouldDismissCallFromGoSignal(idle, "call-1"))

        val ringing = CallUiState(phase = CallPhase.INCOMING_RINGING, callId = "call-42")
        assertTrue(shouldDismissCallFromGoSignal(ringing, "call-42"))
        assertFalse(shouldDismissCallFromGoSignal(ringing, "call-99"))
        assertTrue(shouldDismissCallFromGoSignal(ringing, ""))
        assertTrue(shouldDismissCallFromGoSignal(ringing.copy(callId = ""), "call-99"))
    }
}
