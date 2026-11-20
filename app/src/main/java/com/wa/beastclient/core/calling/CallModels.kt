package com.wa.beastclient.core.calling

import java.net.URI
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

enum class WebLinkState {
    UNINITIALIZED,
    LOADING,
    NEEDS_PAIRING_CODE,
    PAIRING_CODE_READY,
    AUTHENTICATED,
    ERROR
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
    val audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    val availableRoutes: Set<CallAudioRoute> = setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER),
    val canAnswerInApp: Boolean = false,
    val connectedAtEpochMs: Long = 0L,
    val durationSec: Int = 0,
    val statusText: String = "",
    val errorReason: String? = null
) {
    val isInProgress: Boolean
        get() = phase != CallPhase.IDLE
}

private val CHROME_VERSION_REGEX = Regex("""Chrome/\d+(?:\.\d+){0,3}""")
private const val FALLBACK_CHROME_TOKEN = "Chrome/131.0.0.0"
private val FIREFOX_VERSION_REGEX = Regex("""Firefox/(\d+(?:\.\d+)?)""")
private const val FALLBACK_FIREFOX_VERSION = "160.0"

/**
 * Constructs a Windows Desktop User-Agent embedding the exact Chromium token from the local
 * WebView binary so User-Agent, Sec-CH-UA client hints, and TLS fingerprints stay consistent.
 */
fun buildChromiumMatchedDesktopUa(defaultWebViewUa: String): String {
    val chromeToken = CHROME_VERSION_REGEX.find(defaultWebViewUa)?.value ?: FALLBACK_CHROME_TOKEN
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeToken Safari/537.36"
}

/**
 * Constructs a Windows Desktop Firefox User-Agent matching the embedded GeckoView major version.
 */
fun buildDesktopFirefoxUa(defaultGeckoUa: String = ""): String {
    val ver = FIREFOX_VERSION_REGEX.find(defaultGeckoUa)?.groupValues?.getOrNull(1) ?: FALLBACK_FIREFOX_VERSION
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:$ver) Gecko/20100101 Firefox/$ver"
}

/**
 * Authorizes WebRTC microphone/camera PermissionRequest strictly for HTTPS WhatsApp origins.
 */
fun isAllowedWhatsAppPermissionOrigin(originUri: String?): Boolean {
    val clean = originUri?.trim().orEmpty()
    if (clean.isEmpty()) return false
    val parsed = runCatching { URI(clean) }.getOrNull() ?: return false
    if (!parsed.scheme.equals("https", ignoreCase = true)) return false
    val host = parsed.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
    return host == "web.whatsapp.com" || host == "whatsapp.com" || host.endsWith(".whatsapp.com")
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

/**
 * Converts a WhatsApp JID + resolved phone digits into WhatsApp Web's canonical WID string
 * (`<group>@g.us`, `<digits>@c.us`, or `<lid>@lid`) for direct VoIP / chat routing.
 */
fun toWhatsAppWebChatWid(
    peerJid: String,
    phoneDigits: String = "",
    isGroup: Boolean = false
): String {
    val rawJid = peerJid.trim()
    val cleanUser = rawJid.substringBefore("@").substringBefore(":").trim()
    val server = rawJid.substringAfter("@", "").lowercase(Locale.ROOT)
    if (isGroup || server == "g.us") {
        return if (cleanUser.isNotEmpty()) "$cleanUser@g.us" else ""
    }
    val digits = phoneDigits.filter { it.isDigit() }
    if (digits.length in 7..16) {
        return "$digits@c.us"
    }
    if (server == "lid" && cleanUser.isNotEmpty()) {
        return "$cleanUser@lid"
    }
    if ((server == "s.whatsapp.net" || server == "c.us") && cleanUser.isNotEmpty()) {
        val jidDigits = cleanUser.filter { it.isDigit() }
        if (jidDigits.length in 7..16) {
            return "$jidDigits@c.us"
        }
    }
    return ""
}

/**
 * Blocks cold calls to unsaved 1:1 numbers with zero prior message history to avoid spam flags.
 */
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
