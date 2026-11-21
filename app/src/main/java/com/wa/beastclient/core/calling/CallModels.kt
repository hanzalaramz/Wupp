package com.wa.beastclient.core.calling

import java.util.Locale

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class CallPhase {
    IDLE,
    DIALING,
    INCOMING_RINGING,
    CONNECTING,
    ACTIVE,
    ENDING,
    FAILED
}

enum class CallAudioRoute(val label: String) {
    EARPIECE("Earpiece"),
    SPEAKER("Speaker"),
    BLUETOOTH("Bluetooth"),
    WIRED_HEADSET("Headset")
}

data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val callId: String = "",
    val peerJid: String = "",
    val peerFromJid: String = "",
    val peerPhoneDigits: String = "",
    val peerName: String = "",
    val peerAvatarPath: String? = null,
    val direction: CallDirection = CallDirection.OUTGOING,
    val isVideo: Boolean = false,
    val isGroup: Boolean = false,
    val isMuted: Boolean = false,
    val isCameraOff: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val remoteOrientationDegrees: Int = 0,
    val remoteVideoWidth: Int = 640,
    val remoteVideoHeight: Int = 480,
    val pendingVideoUpgrade: Boolean = false,
    val audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    val availableRoutes: Set<CallAudioRoute> = setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER),
    val canAnswerInApp: Boolean = false,
    val peerAccepted: Boolean = false,
    val connectedAtEpochMs: Long = 0L,
    val durationSec: Int = 0,
    val statusText: String = "",
    val errorReason: String? = null
) {
    val isInProgress: Boolean
        get() = phase != CallPhase.IDLE
}

/**
 * Converts WhatsApp `<video device_orientation="0..3">` quarter turns to clockwise UI rotation
 * degrees (matching `wa-rs` / WhatsApp Web where 1 = 90° CCW = 270° CW, 3 = 90° CW).
 */
internal fun whatsappOrientationToClockwiseDegrees(orientationQuarterTurns: Int): Int =
    when (orientationQuarterTurns and 0x03) {
        1 -> 270
        2 -> 180
        3 -> 90
        else -> 0
    }

/**
 * Extracts dialable phone digits from PN JIDs (`@s.whatsapp.net`) or resolved phone strings while
 * rejecting unmapped `@lid`, group, or broadcast identifiers.
 */
fun extractDialablePhoneDigits(
    primaryJid: String,
    altJid: String = "",
    resolvedPhone: String = ""
): String {
    fun digitsFromUser(candidate: String): String {
        val clean = candidate.trim()
        if (clean.isEmpty()) return ""
        val bare = clean.substringBefore("@").substringBefore(":")
        val digits = bare.filter { it.isDigit() }
        return if (digits.length in 7..16) digits else ""
    }

    val primaryClean = primaryJid.trim()
    val altClean = altJid.trim()
    if (primaryClean.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        val d = digitsFromUser(primaryClean)
        if (d.isNotEmpty()) return d
    }
    if (altClean.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        val d = digitsFromUser(altClean)
        if (d.isNotEmpty()) return d
    }
    val explicit = resolvedPhone.trim()
    if (explicit.isNotEmpty() && !explicit.contains("@lid", ignoreCase = true) && !explicit.contains("@g.us", ignoreCase = true)) {
        val d = digitsFromUser(explicit)
        if (d.isNotEmpty()) return d
    }
    return ""
}

fun isColdCallBlocked(
    isSavedContact: Boolean,
    messageCount: Int,
    isGroup: Boolean
): Boolean = !isGroup && !isSavedContact && messageCount <= 0

/** Formats active call duration as `MM:SS` or `HH:MM:SS`. */
fun formatCallTimer(durationSec: Int): String {
    val safe = durationSec.coerceAtLeast(0)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

internal fun shouldDismissCallFromGoSignal(
    current: CallUiState,
    signalCallId: String
): Boolean {
    if (current.phase == CallPhase.IDLE) return false
    if (signalCallId.isBlank() || current.callId.isBlank()) return true
    return current.callId == signalCallId
}

internal sealed interface CallStateUpdateDecision {
    data object Ignore : CallStateUpdateDecision
    data class SyncState(val newState: CallUiState) : CallStateUpdateDecision
    data class ActivateCall(val effectiveCallId: String, val remoteVideo: Boolean) : CallStateUpdateDecision
}

/**
 * Maps native `CALL_STATE_UPDATE` signaling transitions cleanly so outgoing calls stay in
 * `DIALING` (`Calling…` -> `Ringing…`) and `CONNECTING` (`Connecting…`) until media connects.
 */
internal fun resolveNativeCallStateUpdate(
    current: CallUiState,
    callId: String,
    state: String,
    isVideo: Boolean
): CallStateUpdateDecision {
    if (current.phase == CallPhase.IDLE || current.phase == CallPhase.FAILED) {
        return CallStateUpdateDecision.Ignore
    }
    if (callId.isNotBlank() && current.callId.isNotBlank() && current.callId != callId) {
        return CallStateUpdateDecision.Ignore
    }

    val effectiveId = current.callId.ifBlank { callId }
    return when (state.trim().lowercase(Locale.US)) {
        "ringing" -> {
            if (current.direction == CallDirection.OUTGOING &&
                current.phase == CallPhase.DIALING &&
                !current.peerAccepted
            ) {
                CallStateUpdateDecision.SyncState(
                    current.copy(
                        callId = effectiveId,
                        statusText = "Ringing…"
                    )
                )
            } else {
                CallStateUpdateDecision.Ignore
            }
        }
        "accepted" -> {
            when {
                current.direction == CallDirection.OUTGOING &&
                    (current.phase == CallPhase.DIALING || current.phase == CallPhase.CONNECTING) -> {
                    CallStateUpdateDecision.SyncState(
                        current.copy(
                            callId = effectiveId,
                            phase = CallPhase.CONNECTING,
                            peerAccepted = true,
                            statusText = "Connecting…"
                        )
                    )
                }
                current.direction == CallDirection.INCOMING && current.phase == CallPhase.CONNECTING -> {
                    CallStateUpdateDecision.SyncState(
                        current.copy(
                            callId = effectiveId,
                            peerAccepted = true
                        )
                    )
                }
                else -> CallStateUpdateDecision.Ignore
            }
        }
        "connecting" -> {
            if (current.phase == CallPhase.DIALING || current.phase == CallPhase.INCOMING_RINGING) {
                CallStateUpdateDecision.SyncState(
                    current.copy(
                        callId = effectiveId,
                        phase = CallPhase.CONNECTING,
                        statusText = "Connecting…"
                    )
                )
            } else {
                CallStateUpdateDecision.Ignore
            }
        }
        "connected", "active" -> {
            if (current.direction == CallDirection.INCOMING && current.phase == CallPhase.INCOMING_RINGING) {
                CallStateUpdateDecision.Ignore
            } else {
                CallStateUpdateDecision.ActivateCall(effectiveId, isVideo)
            }
        }
        else -> CallStateUpdateDecision.Ignore
    }
}
