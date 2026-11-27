package com.wa.beastclient.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogPowerPolicyTest {

    @Test
    fun connectedScreenOffStretchesWatchdogPollToThreeMinutes() {
        val delay = WaRealtimeService.computeWatchdogPollDelayMs(
            connectionState = "CONNECTED",
            isInteractive = false,
            skipBackgroundReconnect = false
        )
        assertEquals(180_000L, delay)
    }

    @Test
    fun connectedScreenOnPollsEveryMinute() {
        val delay = WaRealtimeService.computeWatchdogPollDelayMs(
            connectionState = "CONNECTED",
            isInteractive = true,
            skipBackgroundReconnect = false
        )
        assertEquals(60_000L, delay)
    }

    @Test
    fun disconnectedOnlinePollsAtFortyFiveSeconds() {
        val delay = WaRealtimeService.computeWatchdogPollDelayMs(
            connectionState = "RECONNECTING",
            isInteractive = false,
            skipBackgroundReconnect = false
        )
        assertEquals(45_000L, delay)
    }

    @Test
    fun offlineOrHaltedBacksOffToThreeMinutesEvenIfDisconnected() {
        val delay = WaRealtimeService.computeWatchdogPollDelayMs(
            connectionState = "OFFLINE_PAUSED",
            isInteractive = true,
            skipBackgroundReconnect = true
        )
        assertEquals(180_000L, delay)
    }
}
