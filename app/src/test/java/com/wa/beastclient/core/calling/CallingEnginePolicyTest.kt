package com.wa.beastclient.core.calling

import com.wa.beastclient.domain.stealth.CallBackendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingEnginePolicyTest {

    @Test
    fun extractDialablePhoneDigitsPrefersPnJidAndFallsBackPastLid() {
        assertEquals(
            "15551234567",
            extractDialablePhoneDigits("15551234567@s.whatsapp.net")
        )
        assertEquals(
            "923001234567",
            extractDialablePhoneDigits(
                primaryJid = "1234567890@lid",
                altJid = "923001234567:2@s.whatsapp.net"
            )
        )
        assertEquals(
            "447911123456",
            extractDialablePhoneDigits(
                primaryJid = "1234567890@lid",
                resolvedPhone = "+44 7911 123456"
            )
        )
        assertEquals(
            "",
            extractDialablePhoneDigits(primaryJid = "987654321012345@lid")
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
    fun callBackendModeParsesStorageKeysWithNativeVoipDefault() {
        assertEquals(CallBackendMode.ALWAYS_READY, CallBackendMode.fromStorageKey(null))
        assertEquals(CallBackendMode.ALWAYS_READY, CallBackendMode.fromStorageKey("unknown"))
        assertEquals(CallBackendMode.OFF, CallBackendMode.fromStorageKey("off"))
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

    @Test
    fun whatsappOrientationQuarterTurnsMapsToClockwiseDisplayDegrees() {
        assertEquals(0, whatsappOrientationToClockwiseDegrees(0))
        assertEquals(270, whatsappOrientationToClockwiseDegrees(1))
        assertEquals(180, whatsappOrientationToClockwiseDegrees(2))
        assertEquals(90, whatsappOrientationToClockwiseDegrees(3))
        assertEquals(270, whatsappOrientationToClockwiseDegrees(5))
    }

    @Test
    fun outgoingCallProgressesCallingToRingingToConnectingBeforeTimerStarts() {
        val dialing = CallUiState(
            phase = CallPhase.DIALING,
            callId = "",
            direction = CallDirection.OUTGOING,
            statusText = "Calling…"
        )

        val ringingDecision = resolveNativeCallStateUpdate(dialing, callId = "call-out-1", state = "ringing", isVideo = false)
        assertTrue(ringingDecision is CallStateUpdateDecision.SyncState)
        val ringingState = (ringingDecision as CallStateUpdateDecision.SyncState).newState
        assertEquals(CallPhase.DIALING, ringingState.phase)
        assertEquals("call-out-1", ringingState.callId)
        assertEquals("Ringing…", ringingState.statusText)
        assertEquals(0L, ringingState.connectedAtEpochMs)
        assertEquals(0, ringingState.durationSec)
        assertFalse(ringingState.peerAccepted)

        // Mismatched callId updates must be ignored cleanly.
        assertEquals(
            CallStateUpdateDecision.Ignore,
            resolveNativeCallStateUpdate(ringingState, callId = "other-call", state = "accepted", isVideo = false)
        )

        // Peer answering puts outgoing call into Connecting… before MediaConnected activates the timer.
        val acceptedDecision = resolveNativeCallStateUpdate(ringingState, callId = "call-out-1", state = "accepted", isVideo = false)
        assertTrue(acceptedDecision is CallStateUpdateDecision.SyncState)
        val connectingState = (acceptedDecision as CallStateUpdateDecision.SyncState).newState
        assertEquals(CallPhase.CONNECTING, connectingState.phase)
        assertEquals("Connecting…", connectingState.statusText)
        assertTrue(connectingState.peerAccepted)
        assertEquals(0, connectingState.durationSec)

        // Duplicate PreAccept after peer already accepted must not revert Connecting… back to Ringing…
        assertEquals(
            CallStateUpdateDecision.Ignore,
            resolveNativeCallStateUpdate(connectingState, callId = "call-out-1", state = "ringing", isVideo = false)
        )

        val connectedDecision = resolveNativeCallStateUpdate(connectingState, callId = "call-out-1", state = "connected", isVideo = true)
        assertEquals(
            CallStateUpdateDecision.ActivateCall(effectiveCallId = "call-out-1", remoteVideo = true),
            connectedDecision
        )
    }

    @Test
    fun incomingRingingCallIgnoresConnectedAndRingingUntilAnswered() {
        val incoming = CallUiState(
            phase = CallPhase.INCOMING_RINGING,
            callId = "call-in-1",
            direction = CallDirection.INCOMING,
            statusText = "Incoming voice call…"
        )
        assertEquals(
            CallStateUpdateDecision.Ignore,
            resolveNativeCallStateUpdate(incoming, callId = "call-in-1", state = "ringing", isVideo = false)
        )
        assertEquals(
            CallStateUpdateDecision.Ignore,
            resolveNativeCallStateUpdate(incoming, callId = "call-in-1", state = "connected", isVideo = false)
        )

        val answering = incoming.copy(phase = CallPhase.CONNECTING, statusText = "Connecting…")
        assertEquals(
            CallStateUpdateDecision.ActivateCall(effectiveCallId = "call-in-1", remoteVideo = false),
            resolveNativeCallStateUpdate(answering, callId = "call-in-1", state = "connected", isVideo = false)
        )
    }

    @Test
    fun shouldRingLocallyForIncomingCallSuppressesOnlyWhenEnabledAndOfficialInstalled() {
        assertTrue(shouldRingLocallyForIncomingCall(suppressWhenOfficialInstalled = false, officialAppInstalled = false))
        assertTrue(shouldRingLocallyForIncomingCall(suppressWhenOfficialInstalled = false, officialAppInstalled = true))
        assertTrue(shouldRingLocallyForIncomingCall(suppressWhenOfficialInstalled = true, officialAppInstalled = false))
        assertFalse(shouldRingLocallyForIncomingCall(suppressWhenOfficialInstalled = true, officialAppInstalled = true))
    }

    @Test
    fun shouldPromptOfficialCallCoexistenceRequiresInstalledOfficialAndUnresolvedChoice() {
        assertTrue(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = true,
                isAppLocked = false,
                alreadyPrompted = false,
                officialAppInstalled = true,
                officialMuteConfirmed = false,
                suppressWhenOfficialInstalled = false
            )
        )
        assertFalse(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = true,
                isAppLocked = false,
                alreadyPrompted = false,
                officialAppInstalled = false,
                officialMuteConfirmed = false,
                suppressWhenOfficialInstalled = false
            )
        )
        assertFalse(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = false,
                isAppLocked = false,
                alreadyPrompted = false,
                officialAppInstalled = true,
                officialMuteConfirmed = false,
                suppressWhenOfficialInstalled = false
            )
        )
        assertFalse(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = true,
                isAppLocked = false,
                alreadyPrompted = false,
                officialAppInstalled = true,
                officialMuteConfirmed = true,
                suppressWhenOfficialInstalled = false
            )
        )
        assertFalse(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = true,
                isAppLocked = false,
                alreadyPrompted = false,
                officialAppInstalled = true,
                officialMuteConfirmed = false,
                suppressWhenOfficialInstalled = true
            )
        )
        assertFalse(
            shouldPromptOfficialCallCoexistence(
                setupDone = true,
                isLoggedIn = true,
                isAppLocked = false,
                alreadyPrompted = true,
                officialAppInstalled = true,
                officialMuteConfirmed = false,
                suppressWhenOfficialInstalled = false
            )
        )
    }
}
