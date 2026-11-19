package com.wa.beastclient.ui.screens

import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflineModeToggleTest {

    @Test
    fun offlineModeDefaultsToDisabled() {
        assertFalse(GlobalStealthConfig().offlineMode)
    }

    @Test
    fun badgeShowsLiveWhenConnectedAndNotOffline() {
        val badge = resolveTopBarConnectionBadge(
            connectionState = "CONNECTED",
            offlineMode = false
        )
        assertEquals("Live", badge.label)
        assertEquals(TopBarStatusTone.LIVE, badge.tone)
    }

    @Test
    fun badgeShowsOfflineWhenOfflineModeEnabledOrPausedState() {
        val fromToggle = resolveTopBarConnectionBadge(
            connectionState = "CONNECTED",
            offlineMode = true
        )
        assertEquals("Offline", fromToggle.label)
        assertEquals(TopBarStatusTone.OFFLINE, fromToggle.tone)

        val fromServiceState = resolveTopBarConnectionBadge(
            connectionState = "OFFLINE_PAUSED",
            offlineMode = false
        )
        assertEquals("Offline", fromServiceState.label)
        assertEquals(TopBarStatusTone.OFFLINE, fromServiceState.tone)
    }

    @Test
    fun badgePreservesSyncingLinkingAndIdleStatesWhenOnline() {
        assertEquals(
            TopBarConnectionBadge("Syncing", TopBarStatusTone.SYNCING),
            resolveTopBarConnectionBadge("SYNCING", offlineMode = false)
        )
        assertEquals(
            TopBarConnectionBadge("Linking", TopBarStatusTone.SYNCING),
            resolveTopBarConnectionBadge("CONNECTING", offlineMode = false)
        )
        assertEquals(
            TopBarConnectionBadge("Idle", TopBarStatusTone.IDLE),
            resolveTopBarConnectionBadge("DISCONNECTED", offlineMode = false)
        )
    }

    @Test
    fun outboundGuardAllowsWhenOnlineAndRejectsWithClearMessageWhenOffline() {
        WaRealtimeService.ensureOnlineForOutbound(offlineMode = false)
        val err = assertThrows(IllegalStateException::class.java) {
            WaRealtimeService.ensureOnlineForOutbound(offlineMode = true)
        }
        assertEquals(WaRealtimeService.OFFLINE_OUTBOUND_ERROR, err.message)
    }
}
