package com.wa.bridge.whatsbridge

/**
 * Kotlin JNI singleton binding directly to `libwhatsbridge_rs.so` (`whatsapp-rust` + native VoIP).
 *
 * Public methods carry `@JvmStatic` so both direct Kotlin call sites and existing reflection-based
 * helpers inside `WaRealtimeService` resolve without extra wrappers.
 */
object Whatsbridge {
    @Volatile
    var isNativeLoaded: Boolean = false
        private set

    @Volatile
    var nativeLoadError: Throwable? = null
        private set

    init {
        try {
            System.loadLibrary("whatsbridge_rs")
            isNativeLoaded = true
        } catch (t: Throwable) {
            nativeLoadError = t
            isNativeLoaded = false
            android.util.Log.e("Whatsbridge", "Failed to load native library whatsbridge_rs: ${t.message}", t)
        }
    }

    @JvmStatic
    fun initClient(filesDir: String, callback: NativeBridgeCallback) =
        nativeInitClient(filesDir, callback)

    @JvmStatic
    fun setGhostPresence(enabled: Boolean) = nativeSetGhostPresence(enabled)

    @JvmStatic
    fun connectClient() = nativeConnectClient()

    @JvmStatic
    fun disconnectClient() = nativeDisconnectClient()

    @JvmStatic
    fun shutdownClient() = nativeShutdownClient()

    @JvmStatic
    fun resetSocket() = nativeResetSocket()

    @JvmStatic
    fun isLoggedIn(): Boolean = nativeIsLoggedIn()

    @JvmStatic
    fun isConnected(): Boolean = nativeIsConnected()

    @JvmStatic
    fun getOwnJID(): String = nativeGetOwnJID().orEmpty()

    @JvmStatic
    fun pairWithPhoneNumber(phone: String): String = nativePairWithPhoneNumber(phone).orEmpty()

    @JvmStatic
    fun sendTextMessage(
        chatJid: String,
        text: String,
        quotedId: String,
        quotedSender: String,
        quotedBody: String,
        mentionsCsv: String,
        ghostTyping: Boolean
    ): String = nativeSendTextMessage(
        chatJid,
        text,
        quotedId,
        quotedSender,
        quotedBody,
        mentionsCsv,
        ghostTyping
    ).orEmpty()

    @JvmStatic
    fun sendContactMessage(
        chatJid: String,
        displayName: String,
        vcard: String,
        quotedId: String = "",
        quotedSender: String = "",
        quotedBody: String = ""
    ): String = nativeSendContactMessage(
        chatJid,
        displayName,
        vcard,
        quotedId,
        quotedSender,
        quotedBody
    ).orEmpty()

    @JvmStatic
    fun sendLocationMessage(
        chatJid: String,
        latitude: Double,
        longitude: Double,
        name: String = "",
        address: String = "",
        url: String = "",
        quotedId: String = "",
        quotedSender: String = "",
        quotedBody: String = ""
    ): String = nativeSendLocationMessage(
        chatJid,
        latitude,
        longitude,
        name,
        address,
        url,
        quotedId,
        quotedSender,
        quotedBody
    ).orEmpty()

    @JvmStatic
    fun sendMediaMessage(
        chatJid: String,
        filePath: String,
        mimeType: String,
        caption: String,
        qualityMode: String,
        isVoiceNote: Boolean,
        durationSec: Long,
        waveformCsv: String
    ): String = nativeSendMediaMessage(
        chatJid,
        filePath,
        mimeType,
        caption,
        qualityMode,
        isVoiceNote,
        durationSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        waveformCsv
    ).orEmpty()

    @JvmStatic
    fun sendGifMessage(
        chatJid: String,
        filePath: String,
        caption: String,
        durationSec: Int
    ): String = nativeSendGifMessage(chatJid, filePath, caption, durationSec).orEmpty()

    @JvmStatic
    fun editMessage(chatJid: String, messageId: String, newText: String) =
        nativeEditMessage(chatJid, messageId, newText)

    @JvmStatic
    fun revokeMessage(chatJid: String, messageId: String) =
        nativeRevokeMessage(chatJid, messageId)

    @JvmStatic
    fun deleteChatRemote(
        chatJid: String,
        lastMsgId: String,
        lastMsgFromMe: Boolean,
        lastMsgParticipant: String,
        lastMsgTimestampMs: Long,
        deleteMedia: Boolean
    ) = nativeDeleteChatRemote(
        chatJid,
        lastMsgId,
        lastMsgFromMe,
        lastMsgParticipant,
        lastMsgTimestampMs,
        deleteMedia
    )

    @JvmStatic
    fun pinChat(chatJid: String, pinned: Boolean) = nativePinChat(chatJid, pinned)

    @JvmStatic
    fun muteChat(chatJid: String, muted: Boolean) = nativeMuteChat(chatJid, muted)

    @JvmStatic
    fun archiveChat(chatJid: String, archived: Boolean) = nativeArchiveChat(chatJid, archived)

    @JvmStatic
    fun markReadManual(chatJid: String, senderJid: String, messageIdsCsv: String) =
        nativeMarkReadManual(chatJid, senderJid, messageIdsCsv)

    @JvmStatic
    fun sendPlayedReceiptManual(chatJid: String, senderJid: String, messageId: String) =
        nativeSendPlayedReceiptManual(chatJid, senderJid, messageId)

    @JvmStatic
    fun sendChatPresenceManual(
        chatJid: String,
        state: String,
        media: String,
        blockedByGhost: Boolean
    ) = nativeSendChatPresenceManual(chatJid, state, media, blockedByGhost)

    @JvmStatic
    fun subscribeContactPresence(jidStr: String) = nativeSubscribeContactPresence(jidStr)

    @JvmStatic
    fun resolveParticipantNamesJson(jidsCsv: String): String =
        nativeResolveParticipantNamesJson(jidsCsv) ?: "{}"

    @JvmStatic
    fun resolveLocalDmAliasesJson(jidsCsv: String): String =
        nativeResolveLocalDmAliasesJson(jidsCsv) ?: "{}"

    @JvmStatic
    fun getGroupParticipantsJson(groupJid: String): String =
        nativeGetGroupParticipantsJson(groupJid) ?: "[]"

    @JvmStatic
    fun hasCachedMediaMeta(messageId: String): Boolean = nativeHasCachedMediaMeta(messageId)

    @JvmStatic
    fun requestMediaDownload(
        chatJid: String,
        senderJid: String,
        messageId: String,
        timestampMs: Long
    ) = nativeRequestMediaDownload(chatJid, senderJid, messageId, timestampMs)

    @JvmStatic
    fun requestGroupHistoryBackfill(
        chatJid: String,
        anchorId: String,
        anchorFromMe: Boolean,
        anchorTsMs: Long
    ) = nativeRequestGroupHistoryBackfill(chatJid, anchorId, anchorFromMe, anchorTsMs)

    @JvmStatic
    fun setAutoDownloadPolicy(
        allowPhoto: Boolean,
        allowAudio: Boolean,
        allowVideo: Boolean,
        allowDoc: Boolean
    ) = nativeSetAutoDownloadPolicy(allowPhoto, allowAudio, allowVideo, allowDoc)

    @JvmStatic
    fun setChatAutoDownload(chatJid: String, mask: Int) {
        runCatching { nativeSetChatAutoDownload(chatJid, mask) }
    }

    @JvmStatic
    fun setPerChatAutoDownloadJson(jsonStr: String) {
        runCatching { nativeSetPerChatAutoDownloadJson(jsonStr) }
    }

    @JvmStatic
    fun verifyNumbersOnWhatsApp(phonesCsv: String): String =
        nativeVerifyNumbersOnWhatsApp(phonesCsv) ?: "[]"

    @JvmStatic
    fun syncContactsAndAvatars(filterCsv: String) = nativeSyncContactsAndAvatars(filterCsv)

    @JvmStatic
    fun fetchNewsletterMessages(nlJid: String) = nativeFetchNewsletterMessages(nlJid)

    @JvmStatic
    fun fetchGroupLockState(groupJid: String): Boolean = nativeFetchGroupLockState(groupJid)

    @JvmStatic
    fun getGroupInfoJson(groupJid: String): String = nativeGetGroupInfoJson(groupJid) ?: "{}"

    @JvmStatic
    fun fetchFullProfilePicture(jidStr: String): String =
        nativeFetchFullProfilePicture(jidStr).orEmpty()

    @JvmStatic
    fun toggleNewsletterMute(nlJid: String, muted: Boolean) =
        nativeToggleNewsletterMute(nlJid, muted)

    @JvmStatic
    fun sendReaction(
        chatJid: String,
        targetId: String,
        senderJid: String,
        fromMe: Boolean,
        emoji: String
    ) = nativeSendReaction(chatJid, targetId, senderJid, fromMe, emoji)

    @JvmStatic
    fun sendStatusReply(
        authorJid: String,
        statusId: String,
        replyText: String,
        origType: String,
        origBody: String,
        isFromMe: Boolean
    ) = nativeSendStatusReply(authorJid, statusId, replyText, origType, origBody, isFromMe)

    @JvmStatic
    fun sendStatusReaction(authorJid: String, statusId: String, emoji: String) =
        nativeSendStatusReaction(authorJid, statusId, emoji)

    @JvmStatic
    fun pinChatMessage(
        chatJid: String,
        msgId: String,
        fromMe: Boolean,
        senderJid: String,
        durationSecs: Long,
        isUnpin: Boolean
    ) = nativePinChatMessage(chatJid, msgId, fromMe, senderJid, durationSecs, isUnpin)

    @JvmStatic
    fun getOwnProfileJson(): String = nativeGetOwnProfileJson() ?: "{}"

    @JvmStatic
    fun setOwnPushName(name: String) = nativeSetOwnPushName(name)

    @JvmStatic
    fun setOwnAbout(about: String) = nativeSetOwnAbout(about)

    @JvmStatic
    fun getAccountPrivacySettingsJson(): String = nativeGetAccountPrivacySettingsJson() ?: "{}"

    @JvmStatic
    fun setAccountPrivacySetting(category: String, value: String): String =
        nativeSetAccountPrivacySetting(category, value) ?: "{}"

    @JvmStatic
    fun setDefaultDisappearingTimerSec(seconds: Long) =
        nativeSetDefaultDisappearingTimerSec(seconds)

    @JvmStatic
    fun getBlocklistJson(): String = nativeGetBlocklistJson() ?: "[]"

    @JvmStatic
    fun setContactBlocked(jidStr: String, blocked: Boolean) =
        nativeSetContactBlocked(jidStr, blocked)

    @JvmStatic
    fun rejectCall(fromJid: String, callId: String) = nativeRejectCall(fromJid, callId)

    @JvmStatic
    fun startOutgoingCall(peerJid: String, isVideo: Boolean, isGroup: Boolean): String =
        nativeStartOutgoingCall(peerJid, isVideo, isGroup).orEmpty()

    @JvmStatic
    fun acceptIncomingCall(callId: String, withVideo: Boolean): String =
        nativeAcceptIncomingCall(callId, withVideo).orEmpty()

    @JvmStatic
    fun terminateCall(callId: String) = nativeTerminateCall(callId)

    @JvmStatic
    fun setCallMuted(callId: String, muted: Boolean) = nativeSetCallMuted(callId, muted)

    @JvmStatic
    fun setCallVideoEnabled(callId: String, enabled: Boolean) =
        nativeSetCallVideoEnabled(callId, enabled)

    @JvmStatic
    fun setCallVideoOrientation(callId: String, orientation: Int) =
        nativeSetCallVideoOrientation(callId, orientation)

    @JvmStatic
    fun requestPeerKeyframe(callId: String) = nativeRequestPeerKeyframe(callId)

    @JvmStatic
    fun pushVoipAudioCapture(callId: String, samples: ShortArray): Int =
        nativePushVoipAudioCapture(callId, samples)

    @JvmStatic
    fun pullVoipAudioPlayout(callId: String, outSamples: ShortArray): Int =
        nativePullVoipAudioPlayout(callId, outSamples)

    @JvmStatic
    fun pushVoipVideoAu(callId: String, data: ByteArray, timestamp90k: Long): Int =
        nativePushVoipVideoAu(callId, data, timestamp90k)

    @JvmStatic
    fun pullVoipVideoFrame(callId: String): ByteArray? = nativePullVoipVideoFrame(callId)

    private external fun nativeInitClient(filesDir: String, callback: NativeBridgeCallback)
    private external fun nativeSetGhostPresence(enabled: Boolean)
    private external fun nativeConnectClient()
    private external fun nativeDisconnectClient()
    private external fun nativeShutdownClient()
    private external fun nativeResetSocket()
    private external fun nativeIsLoggedIn(): Boolean
    private external fun nativeIsConnected(): Boolean
    private external fun nativeGetOwnJID(): String?
    private external fun nativePairWithPhoneNumber(phone: String): String?
    private external fun nativeSendTextMessage(
        chatJid: String,
        text: String,
        quotedId: String,
        quotedSender: String,
        quotedBody: String,
        mentionsCsv: String,
        ghostTyping: Boolean
    ): String?
    private external fun nativeSendContactMessage(
        chatJid: String,
        displayName: String,
        vcard: String,
        quotedId: String,
        quotedSender: String,
        quotedBody: String
    ): String?

    private external fun nativeSendLocationMessage(
        chatJid: String,
        latitude: Double,
        longitude: Double,
        name: String,
        address: String,
        url: String,
        quotedId: String,
        quotedSender: String,
        quotedBody: String
    ): String?
    private external fun nativeSendMediaMessage(
        chatJid: String,
        filePath: String,
        mimeType: String,
        caption: String,
        qualityMode: String,
        isVoiceNote: Boolean,
        durationSec: Int,
        waveformCsv: String
    ): String?
    private external fun nativeSendGifMessage(
        chatJid: String,
        filePath: String,
        caption: String,
        durationSec: Int
    ): String?
    private external fun nativeEditMessage(chatJid: String, messageId: String, newText: String)
    private external fun nativeRevokeMessage(chatJid: String, messageId: String)
    private external fun nativeDeleteChatRemote(
        chatJid: String,
        lastMsgId: String,
        lastMsgFromMe: Boolean,
        lastMsgParticipant: String,
        lastMsgTimestampMs: Long,
        deleteMedia: Boolean
    )
    private external fun nativePinChat(chatJid: String, pinned: Boolean)
    private external fun nativeMuteChat(chatJid: String, muted: Boolean)
    private external fun nativeArchiveChat(chatJid: String, archived: Boolean)
    private external fun nativeMarkReadManual(
        chatJid: String,
        senderJid: String,
        messageIdsCsv: String
    )
    private external fun nativeSendPlayedReceiptManual(
        chatJid: String,
        senderJid: String,
        messageId: String
    )
    private external fun nativeSendChatPresenceManual(
        chatJid: String,
        state: String,
        media: String,
        blockedByGhost: Boolean
    )
    private external fun nativeSubscribeContactPresence(jidStr: String)
    private external fun nativeResolveParticipantNamesJson(jidsCsv: String): String?
    private external fun nativeResolveLocalDmAliasesJson(jidsCsv: String): String?
    private external fun nativeGetGroupParticipantsJson(groupJid: String): String?
    private external fun nativeHasCachedMediaMeta(messageId: String): Boolean
    private external fun nativeRequestMediaDownload(
        chatJid: String,
        senderJid: String,
        messageId: String,
        timestampMs: Long
    )
    private external fun nativeRequestGroupHistoryBackfill(
        chatJid: String,
        anchorId: String,
        anchorFromMe: Boolean,
        anchorTsMs: Long
    )
    private external fun nativeSetAutoDownloadPolicy(
        allowPhoto: Boolean,
        allowAudio: Boolean,
        allowVideo: Boolean,
        allowDoc: Boolean
    )
    private external fun nativeSetChatAutoDownload(chatJid: String, mask: Int)
    private external fun nativeSetPerChatAutoDownloadJson(jsonStr: String)
    private external fun nativeVerifyNumbersOnWhatsApp(phonesCsv: String): String?
    private external fun nativeSyncContactsAndAvatars(filterCsv: String)
    private external fun nativeFetchNewsletterMessages(nlJid: String)
    private external fun nativeFetchGroupLockState(groupJid: String): Boolean
    private external fun nativeGetGroupInfoJson(groupJid: String): String?
    private external fun nativeFetchFullProfilePicture(jidStr: String): String?
    private external fun nativeToggleNewsletterMute(nlJid: String, muted: Boolean)
    private external fun nativeSendReaction(
        chatJid: String,
        targetId: String,
        senderJid: String,
        fromMe: Boolean,
        emoji: String
    )
    private external fun nativeSendStatusReply(
        authorJid: String,
        statusId: String,
        replyText: String,
        origType: String,
        origBody: String,
        isFromMe: Boolean
    )
    private external fun nativeSendStatusReaction(
        authorJid: String,
        statusId: String,
        emoji: String
    )
    private external fun nativePinChatMessage(
        chatJid: String,
        msgId: String,
        fromMe: Boolean,
        senderJid: String,
        durationSecs: Long,
        isUnpin: Boolean
    )
    private external fun nativeGetOwnProfileJson(): String?
    private external fun nativeSetOwnPushName(name: String)
    private external fun nativeSetOwnAbout(about: String)
    private external fun nativeGetAccountPrivacySettingsJson(): String?
    private external fun nativeSetAccountPrivacySetting(category: String, value: String): String?
    private external fun nativeSetDefaultDisappearingTimerSec(seconds: Long)
    private external fun nativeGetBlocklistJson(): String?
    private external fun nativeSetContactBlocked(jidStr: String, blocked: Boolean)
    private external fun nativeRejectCall(fromJid: String, callId: String)
    private external fun nativeStartOutgoingCall(
        peerJid: String,
        isVideo: Boolean,
        isGroup: Boolean
    ): String?
    private external fun nativeAcceptIncomingCall(callId: String, withVideo: Boolean): String?
    private external fun nativeTerminateCall(callId: String)
    private external fun nativeSetCallMuted(callId: String, muted: Boolean)
    private external fun nativeSetCallVideoEnabled(callId: String, enabled: Boolean)
    private external fun nativeSetCallVideoOrientation(callId: String, orientation: Int)
    private external fun nativeRequestPeerKeyframe(callId: String)
    private external fun nativePushVoipAudioCapture(callId: String, samples: ShortArray): Int
    private external fun nativePullVoipAudioPlayout(callId: String, outSamples: ShortArray): Int
    private external fun nativePushVoipVideoAu(
        callId: String,
        data: ByteArray,
        timestamp90k: Long
    ): Int
    private external fun nativePullVoipVideoFrame(callId: String): ByteArray?
}
