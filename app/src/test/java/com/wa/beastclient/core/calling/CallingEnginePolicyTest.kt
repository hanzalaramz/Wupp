package com.wa.beastclient.core.calling

import com.wa.beastclient.core.logging.AppLogManager
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.CallPipTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallingEnginePolicyTest {
    @Test
    fun missingOrientationPreservesLastFrameAndExplicitZeroResetsIt() {
        assertEquals(270, resolveRemoteOrientation(270, -1))
        assertEquals(0, resolveRemoteOrientation(270, 0))
        assertEquals(90, resolveRemoteOrientation(0, 3))
    }

    @Test
    fun effectiveRemoteRotationProtectsAlreadyPortraitFramesFromQuarterTurnFlip() {
        assertEquals(
            0,
            resolveEffectiveRemoteDisplayRotation(
                videoWidth = 480,
                videoHeight = 640,
                wireOrientationDegrees = 90
            )
        )
        assertEquals(
            270,
            resolveEffectiveRemoteDisplayRotation(
                videoWidth = 640,
                videoHeight = 480,
                wireOrientationDegrees = 270
            )
        )
        assertEquals(
            90,
            resolveEffectiveRemoteDisplayRotation(
                videoWidth = 480,
                videoHeight = 640,
                wireOrientationDegrees = 90,
                manualOffsetDegrees = 90
            )
        )
    }

    @Test
    fun callPipTapActionParsesStorageKeysWithSwapLayoutDefault() {
        assertEquals(CallPipTapAction.SWAP_LAYOUT, CallPipTapAction.fromStorageKey(null))
        assertEquals(CallPipTapAction.SWAP_LAYOUT, CallPipTapAction.fromStorageKey("invalid"))
        assertEquals(CallPipTapAction.FLIP_CAMERA, CallPipTapAction.fromStorageKey("flip_camera"))
    }

    @Test
    fun diagnosticReportHeaderIncludesVersionAndDeviceMetadata() {
        val header = AppLogManager.formatDiagnosticReportHeader(
            versionName = "0.3.10-pre.6",
            versionCode = 10,
            manufacturer = "Realme",
            model = "RMX3660",
            androidRelease = "14",
            sdkInt = 34,
            abisCsv = "arm64-v8a",
            generatedAtFormatted = "2026-11-26 20:00:00.000"
        )
        assertTrue(header.contains("0.3.10-pre.6"))
        assertTrue(header.contains("Realme RMX3660"))
        assertTrue(header.contains("API 34"))
    }


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
    fun resolveIncomingAntiCallActionHonorsAllModesForSavedAndUnknownCallers() {
        assertEquals(
            IncomingCallPolicyAction.ALLOW,
            resolveIncomingAntiCallAction(com.wa.beastclient.domain.stealth.AntiCallMode.OFF, isSavedContact = false)
        )
        assertEquals(
            IncomingCallPolicyAction.SILENCE,
            resolveIncomingAntiCallAction(com.wa.beastclient.domain.stealth.AntiCallMode.SILENCE_ALL, isSavedContact = true)
        )
        assertEquals(
            IncomingCallPolicyAction.REJECT,
            resolveIncomingAntiCallAction(com.wa.beastclient.domain.stealth.AntiCallMode.REJECT_ALL, isSavedContact = true)
        )
        assertEquals(
            IncomingCallPolicyAction.REJECT,
            resolveIncomingAntiCallAction(com.wa.beastclient.domain.stealth.AntiCallMode.REJECT_UNSAVED, isSavedContact = false)
        )
        assertEquals(
            IncomingCallPolicyAction.ALLOW,
            resolveIncomingAntiCallAction(com.wa.beastclient.domain.stealth.AntiCallMode.REJECT_UNSAVED, isSavedContact = true)
        )
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

    @Test
    fun resolveIncomingCallTargetChatJidPrefersGroupJidForGroupCalls() {
        val groupJid = "120363012345678901@g.us"
        val callerJid = "923001112233@s.whatsapp.net"

        assertEquals(
            groupJid,
            resolveIncomingCallTargetChatJid(
                chatJid = groupJid,
                callerJid = callerJid,
                fromJid = callerJid,
                isGroup = true
            )
        )
        assertEquals(
            groupJid,
            resolveIncomingCallTargetChatJid(
                chatJid = groupJid,
                callerJid = callerJid,
                fromJid = callerJid,
                isGroup = false
            )
        )
        assertEquals(
            callerJid,
            resolveIncomingCallTargetChatJid(
                chatJid = "",
                callerJid = callerJid,
                fromJid = callerJid,
                isGroup = false
            )
        )
    }

    @Test
    fun shouldPersistLocalCallLogBlocksGroupCallFallbackIntoOneToOneDm() {
        assertTrue(shouldPersistLocalCallLog("120363012345678901@g.us", isGroup = true))
        assertFalse(shouldPersistLocalCallLog("923001112233@s.whatsapp.net", isGroup = true))
        assertTrue(shouldPersistLocalCallLog("923001112233@s.whatsapp.net", isGroup = false))
        assertFalse(shouldPersistLocalCallLog("", isGroup = false))
    }

    @Test
    fun resolveCameraRelayTransformAvoidsDoubleRotationWhenStMatrixIncludesSensorTurn() {
        // Simulated SurfaceTexture matrix with 90/270 sensor turn already baked in (m00=0, m10=1, m01=1, m11=0)
        val rotatedSt = FloatArray(16).apply {
            this[1] = 1f
            this[4] = 1f
            this[10] = 1f
            this[15] = 1f
        }
        val portrait = resolveCameraRelayTransform(
            camWidth = 640,
            camHeight = 480,
            sensorDeg = 270,
            displayDeg = 0,
            isFrontCamera = true,
            stMatrix = rotatedSt
        )
        assertEquals(0, portrait.cwRotationDeg)
        assertEquals(480, portrait.effectiveSourceWidth)
        assertEquals(640, portrait.effectiveSourceHeight)

        val landscape90 = resolveCameraRelayTransform(
            camWidth = 640,
            camHeight = 480,
            sensorDeg = 270,
            displayDeg = 90,
            isFrontCamera = true,
            stMatrix = rotatedSt
        )
        assertEquals(270, landscape90.cwRotationDeg)
        assertEquals(480, landscape90.effectiveSourceWidth)
        assertEquals(640, landscape90.effectiveSourceHeight)
    }

    @Test
    fun resolveCameraRelayTransformAppliesSensorRotationWhenStMatrixIsUnrotated() {
        // Simulated identity/Y-flipped SurfaceTexture matrix without quarter turn (m00=1, m11=-1)
        val unrotatedSt = FloatArray(16).apply {
            this[0] = 1f
            this[5] = -1f
            this[10] = 1f
            this[15] = 1f
        }
        val frontPortrait = resolveCameraRelayTransform(
            camWidth = 640,
            camHeight = 480,
            sensorDeg = 270,
            displayDeg = 0,
            isFrontCamera = true,
            stMatrix = unrotatedSt
        )
        assertEquals(270, frontPortrait.cwRotationDeg)
        assertEquals(640, frontPortrait.effectiveSourceWidth)
        assertEquals(480, frontPortrait.effectiveSourceHeight)

        val backPortrait = resolveCameraRelayTransform(
            camWidth = 640,
            camHeight = 480,
            sensorDeg = 90,
            displayDeg = 0,
            isFrontCamera = false,
            stMatrix = unrotatedSt
        )
        assertEquals(90, backPortrait.cwRotationDeg)
        assertEquals(640, backPortrait.effectiveSourceWidth)
        assertEquals(480, backPortrait.effectiveSourceHeight)
    }

    @Test
    fun outgoingCallCanStartFromIdleOrFailedButNotActivePhases() {
        assertTrue(isCallStartAllowed(CallPhase.IDLE))
        assertTrue(isCallStartAllowed(CallPhase.FAILED))
        assertFalse(isCallStartAllowed(CallPhase.DIALING))
        assertFalse(isCallStartAllowed(CallPhase.INCOMING_RINGING))
        assertFalse(isCallStartAllowed(CallPhase.CONNECTING))
        assertFalse(isCallStartAllowed(CallPhase.ACTIVE))
        assertFalse(isCallStartAllowed(CallPhase.ENDING))
    }

    @Test
    fun foregroundServiceTypeFallbackLadderDowngradesCameraThenMicThenPhoneCallToZero() {
        val phoneBit = 4
        val micBit = 128
        val camBit = 64
        val allBits = phoneBit or micBit or camBit
        val ladder = buildForegroundServiceTypeFallbackLadder(
            desiredMask = allBits,
            phoneCallBit = phoneBit,
            micBit = micBit,
            cameraBit = camBit
        )
        assertEquals(listOf(allBits, phoneBit or micBit, phoneBit, 0), ladder)
        assertEquals(listOf(0), buildForegroundServiceTypeFallbackLadder(0, 0, 0, 0))
    }

    @Test
    fun metaAiAndSystemEndpointsAreBlockedFromCalling() {
        assertTrue(isMetaAiCallBlocked("13135550002@s.whatsapp.net"))
        assertTrue(isMetaAiCallBlocked("13135550002@lid"))
        assertTrue(isMetaAiCallBlocked("123456@bot"))
        assertTrue(isMetaAiCallBlocked("1203630123456@newsletter"))
        assertTrue(isMetaAiCallBlocked("status@broadcast"))
        assertFalse(isMetaAiCallBlocked("923001112233@s.whatsapp.net"))
        assertFalse(isMetaAiCallBlocked("120363012345678901@g.us"))
    }

    @Test
    fun localPreviewIsFullScreenUntilRemoteVideoArrivesOrWhenSwapped() {
        assertFalse(shouldShowLocalPreviewAsPip(hasRemoteVideo = false, effectiveSwapped = false))
        assertFalse(shouldShowLocalPreviewAsPip(hasRemoteVideo = false, effectiveSwapped = true))
        assertTrue(shouldShowLocalPreviewAsPip(hasRemoteVideo = true, effectiveSwapped = false))
        assertFalse(shouldShowLocalPreviewAsPip(hasRemoteVideo = true, effectiveSwapped = true))
    }
}
