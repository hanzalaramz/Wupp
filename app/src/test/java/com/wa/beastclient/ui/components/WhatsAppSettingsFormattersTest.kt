package com.wa.beastclient.ui.components

import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.WuppClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WhatsAppSettingsFormattersTest {

    @Test
    fun formatBytesCompact_formatsUnitsCleanly() {
        assertEquals("0 B", formatBytesCompact(-5))
        assertEquals("512 B", formatBytesCompact(512))
        assertEquals("1.5 KB", formatBytesCompact(1536))
        assertEquals("2.0 MB", formatBytesCompact(2L * 1024L * 1024L))
    }

    @Test
    fun formatPrivacyValueLabel_mapsServerValues() {
        assertEquals("Everyone", formatPrivacyValueLabel("all"))
        assertEquals("My contacts", formatPrivacyValueLabel("contacts"))
        assertEquals("Same as last seen", formatPrivacyValueLabel("match_last_seen"))
        assertEquals("Silence unknown callers", formatPrivacyValueLabel("known"))
        assertEquals("Nobody", formatPrivacyValueLabel("none"))
    }

    @Test
    fun formatDisappearingTimerLabel_andFontScaleLabel_formatPresets() {
        assertEquals("Off", formatDisappearingTimerLabel(0))
        assertEquals("24 hours", formatDisappearingTimerLabel(86_400))
        assertEquals("7 days", formatDisappearingTimerLabel(604_800))
        assertEquals("90 days", formatDisappearingTimerLabel(7_776_000))

        assertEquals("Small", formatFontScaleLabel(0.85f))
        assertEquals("Medium", formatFontScaleLabel(1.0f))
        assertEquals("Large", formatFontScaleLabel(1.15f))
    }

    @Test
    fun clockFormat_resolvesSystemAndExplicitOverrides() {
        assertEquals(WuppClockFormat.SYSTEM, WuppClockFormat.fromStorageKey(null))
        assertEquals(WuppClockFormat.HOUR_24, WuppClockFormat.fromStorageKey("24h"))
        assertEquals(WuppClockFormat.HOUR_12, WuppClockFormat.fromStorageKey("12h"))

        assertTrue(StealthMatrixManager.resolveUse24Hour(WuppClockFormat.SYSTEM, systemIs24Hour = true))
        assertFalse(StealthMatrixManager.resolveUse24Hour(WuppClockFormat.SYSTEM, systemIs24Hour = false))
        assertTrue(StealthMatrixManager.resolveUse24Hour(WuppClockFormat.HOUR_24, systemIs24Hour = false))
        assertFalse(StealthMatrixManager.resolveUse24Hour(WuppClockFormat.HOUR_12, systemIs24Hour = true))
    }

    @Test
    fun formatMessageClock_formats12hAnd24hWithAndWithoutSeconds() {
        val prevTz = TimeZone.getDefault()
        val prevLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            Locale.setDefault(Locale.US)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.NOVEMBER, 19, 14, 5, 9)
                set(Calendar.MILLISECOND, 0)
            }
            val epochMs = cal.timeInMillis

            assertEquals("2:05 PM", formatMessageClock(epochMs, includeSeconds = false, use24Hour = false))
            assertEquals("2:05:09 PM", formatMessageClock(epochMs, includeSeconds = true, use24Hour = false))
            assertEquals("14:05", formatMessageClock(epochMs, includeSeconds = false, use24Hour = true))
            assertEquals("14:05:09", formatMessageClock(epochMs, includeSeconds = true, use24Hour = true))
            assertEquals("", formatMessageClock(0L, includeSeconds = false, use24Hour = true))
        } finally {
            TimeZone.setDefault(prevTz)
            Locale.setDefault(prevLocale)
        }
    }
}
