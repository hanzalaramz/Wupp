package com.wa.beastclient.core.calling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wa.beastclient.core.logging.AppLogManager
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.calling.CallActivity
import com.wa.bridge.whatsbridge.Whatsbridge
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates native Rust (`whatsapp-rust`) VoIP signaling, Android full-duplex audio/video
 * pipelines, call foreground service, and call screen state without any browser backend.
 */
object CallStateManager {
    private const val TAG = "CallStateManager"

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private val loggedCallIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var initialized = false

    @Volatile
    private var appInForeground = false

    private var audioController: CallAudioController? = null
    private var audioPipeline: NativeVoipAudioPipeline? = null
    private var videoPipeline: NativeVoipVideoPipeline? = null
    private var localPreviewSurface: Surface? = null
    private var remoteVideoSurface: Surface? = null
    private var callTimerJob: Job? = null
    private var failedDismissJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val ctx = context.applicationContext
        appContext = ctx
        StealthMatrixManager.init(ctx)
        audioController = CallAudioController(
            context = ctx,
            onRoutesChanged = { available, active ->
                _uiState.update { it.copy(availableRoutes = available, audioRoute = active) }
            },
            onVoicePlaybackStarted = {
                // Local AudioTrack.play() starts immediately for audio pipeline priming while dialing;
                // never promote to ACTIVE or start the call timer until Rust signals MediaConnected.
            }
        )
        initialized = true
    }

    fun onConfigChanged(config: GlobalStealthConfig) {
        if (_uiState.value.phase == CallPhase.INCOMING_RINGING) {
            val officialPresent = appContext?.let { OfficialWhatsAppHelper.findInstalledPackage(it) != null } == true
            if (config.offlineMode || (config.suppressIncomingRingWhenOfficialInstalled && officialPresent)) {
                audioController?.stopRinging()
            }
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        appInForeground = inForeground
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTrimMemory(level: Int) {
        // No WebView/Gecko engine retained in RAM.
    }

    fun startOutgoingCall(
        context: Context,
        peerJid: String,
        peerName: String,
        peerAvatarPath: String?,
        phoneDigits: String,
        isVideo: Boolean,
        isGroup: Boolean = false
    ) {
        init(context)
        val ctx = context.applicationContext
        val cfg = StealthMatrixManager.config.value

        if (!isCallStartAllowed(_uiState.value.phase)) {
            launchCallActivity(ctx)
            return
        }

        if (cfg.offlineMode || cfg.callBackendMode == CallBackendMode.OFF) {
            openOfficialWhatsApp(ctx, phoneDigits)
            return
        }

        failedDismissJob?.cancel()
        val displayTitle = peerName.ifBlank { peerJid.substringBefore("@").ifBlank { "WhatsApp Call" } }

        _uiState.value = CallUiState(
            phase = CallPhase.DIALING,
            callId = "",
            peerJid = peerJid,
            peerFromJid = peerJid,
            peerPhoneDigits = phoneDigits,
            peerName = displayTitle,
            peerAvatarPath = peerAvatarPath,
            direction = CallDirection.OUTGOING,
            isVideo = isVideo,
            isGroup = isGroup,
            isMuted = false,
            isCameraOff = !isVideo,
            hasRemoteVideo = false,
            remoteOrientationDegrees = 0,
            pendingVideoUpgrade = false,
            canAnswerInApp = false,
            statusText = if (isVideo) "Video calling…" else "Calling…"
        )

        audioController?.startInCallAudio(isVideo = isVideo)
        CallForegroundService.sync(ctx, _uiState.value)
        launchCallActivity(ctx)

        ioScope.launch {
            runCatching {
                Whatsbridge.startOutgoingCall(peerJid, isVideo, isGroup)
            }.onSuccess { callId ->
                mainScope.launch {
                    val cur = _uiState.value
                    if (cur.phase == CallPhase.IDLE || cur.phase == CallPhase.FAILED) {
                        if (callId.isNotBlank()) {
                            ioScope.launch { runCatching { Whatsbridge.terminateCall(callId) } }
                        }
                        return@launch
                    }
                    if (callId.isBlank()) {
                        failCall("Could not obtain call ID")
                        return@launch
                    }
                    _uiState.update { it.copy(callId = callId) }
                    ensureMediaPipelinesStarted(callId = callId, startVideo = isVideo)
                }
            }.onFailure { err ->
                Log.w(TAG, "startOutgoingCall failed: ${err.message}", err)
                mainScope.launch {
                    failCall(err.message?.ifBlank { null } ?: "Couldn't place call")
                }
            }
        }
    }

    fun onGoIncomingCall(
        callId: String,
        fromJid: String,
        callerJid: String,
        callerAltJid: String,
        peerName: String,
        peerAvatarPath: String?,
        isVideo: Boolean,
        isGroup: Boolean = false,
        @Suppress("UNUSED_PARAMETER") isNotice: Boolean = false,
        isSavedContact: Boolean = false,
        chatJid: String = "",
        callerName: String = ""
    ) {
        val ctx = appContext ?: return
        init(ctx)
        if (callId.isBlank()) return

        val cfg = StealthMatrixManager.config.value
        val individualCaller = callerJid.ifBlank { fromJid }
        val effectiveIsGroup = isGroup || chatJid.endsWith("@g.us") || individualCaller.endsWith("@g.us")
        val targetChatJid = resolveIncomingCallTargetChatJid(
            chatJid = chatJid,
            callerJid = individualCaller,
            fromJid = fromJid,
            isGroup = effectiveIsGroup
        )
        when (resolveIncomingAntiCallAction(cfg.antiCallMode, isSavedContact)) {
            IncomingCallPolicyAction.SILENCE -> return
            IncomingCallPolicyAction.REJECT -> {
                if (loggedCallIds.size > 512) loggedCallIds.clear()
                if (shouldPersistLocalCallLog(targetChatJid, effectiveIsGroup) && loggedCallIds.add(callId)) {
                    WaRealtimeService.recordLocalCallLog(
                        context = ctx,
                        callId = callId,
                        peerJid = targetChatJid,
                        isFromMe = false,
                        isVideo = isVideo,
                        isGroup = effectiveIsGroup,
                        timestampMs = System.currentTimeMillis(),
                        durationSec = 0,
                        subtitleOverride = "Blocked by Anti-Call",
                        senderJid = individualCaller,
                        senderName = callerName
                    )
                }
                ioScope.launch {
                    runCatching {
                        Whatsbridge.rejectCall(fromJid.ifBlank { individualCaller }, callId)
                    }.onFailure { err ->
                        Log.w(TAG, "Anti-Call reject failed: ${err.message}", err)
                    }
                }
                return
            }
            IncomingCallPolicyAction.ALLOW -> Unit
        }

        val cur = _uiState.value
        if (cur.phase != CallPhase.IDLE && cur.phase != CallPhase.FAILED) {
            return
        }

        failedDismissJob?.cancel()
        val canAnswerInApp = !cfg.offlineMode && cfg.callBackendMode != CallBackendMode.OFF
        val digits = if (effectiveIsGroup) "" else extractDialablePhoneDigits(individualCaller, callerAltJid)
        val label = peerName.ifBlank {
            digits.takeIf { it.isNotEmpty() }?.let { "+$it" }
                ?: targetChatJid.substringBefore("@").ifBlank { "WhatsApp Call" }
        }

        _uiState.value = CallUiState(
            phase = CallPhase.INCOMING_RINGING,
            callId = callId,
            peerJid = targetChatJid,
            peerFromJid = fromJid.ifBlank { individualCaller },
            peerPhoneDigits = digits,
            peerName = label,
            peerAvatarPath = peerAvatarPath,
            senderJid = individualCaller,
            senderName = callerName,
            direction = CallDirection.INCOMING,
            isVideo = isVideo,
            isGroup = effectiveIsGroup,
            isMuted = false,
            isCameraOff = !isVideo,
            hasRemoteVideo = false,
            remoteOrientationDegrees = 0,
            pendingVideoUpgrade = false,
            canAnswerInApp = canAnswerInApp,
            statusText = if (isVideo) "Incoming video call…" else "Incoming voice call…"
        )

        val officialInstalled = OfficialWhatsAppHelper.findInstalledPackage(ctx) != null
        if (shouldRingLocallyForIncomingCall(cfg.suppressIncomingRingWhenOfficialInstalled, officialInstalled)) {
            audioController?.startRinging()
            CallForegroundService.sync(ctx, _uiState.value)
            launchCallActivity(ctx)
        }
    }

    fun answerIncomingCall(audioOnly: Boolean = false) {
        val ctx = appContext ?: return
        val cur = _uiState.value
        if (cur.phase != CallPhase.INCOMING_RINGING) return

        if (!cur.canAnswerInApp) {
            openOfficialWhatsAppFallback()
            return
        }

        val answerVideo = cur.isVideo && !audioOnly
        audioController?.stopRinging()
        _uiState.update {
            it.copy(
                phase = CallPhase.CONNECTING,
                isVideo = answerVideo,
                isCameraOff = !answerVideo,
                statusText = "Connecting…"
            )
        }
        // Set MODE_IN_COMMUNICATION & voice focus synchronously and promote FGS mic type before capture starts.
        audioController?.startInCallAudio(isVideo = answerVideo)
        CallForegroundService.sync(ctx, _uiState.value)

        val targetCallId = cur.callId
        ioScope.launch {
            runCatching {
                Whatsbridge.acceptIncomingCall(targetCallId, answerVideo)
            }.onSuccess { actualId ->
                mainScope.launch {
                    val activeId = actualId.ifBlank { targetCallId }
                    if (_uiState.value.phase == CallPhase.IDLE) {
                        if (activeId.isNotBlank()) {
                            ioScope.launch { runCatching { Whatsbridge.terminateCall(activeId) } }
                        }
                        return@launch
                    }
                    _uiState.update { it.copy(callId = activeId) }
                    ensureMediaPipelinesStarted(callId = activeId, startVideo = answerVideo)
                }
            }.onFailure { err ->
                Log.w(TAG, "acceptIncomingCall failed: ${err.message}", err)
                mainScope.launch {
                    failCall(err.message?.ifBlank { null } ?: "Couldn't answer call")
                }
            }
        }
    }

    fun declineOrEndCall() {
        val cur = _uiState.value
        if (cur.phase == CallPhase.IDLE) return

        val callId = cur.callId
        val fromJid = cur.peerFromJid.ifBlank { cur.peerJid }
        val wasIncomingRinging = cur.phase == CallPhase.INCOMING_RINGING

        val isOutgoing = cur.direction == CallDirection.OUTGOING
        recordFinishedCallLog(
            cur = cur,
            titleOverride = null,
            subtitleOverride = when {
                cur.phase == CallPhase.ACTIVE && cur.durationSec > 0 -> null
                wasIncomingRinging -> "Declined"
                isOutgoing -> "No answer"
                else -> "Ended"
            }
        )
        resetToIdle()

        if (callId.isNotBlank()) {
            ioScope.launch {
                runCatching {
                    if (wasIncomingRinging) {
                        Whatsbridge.rejectCall(fromJid, callId)
                    } else {
                        Whatsbridge.terminateCall(callId)
                    }
                }.onFailure {
                    Log.w(TAG, "Remote call reject/terminate note: ${it.message}")
                }
            }
        }
    }

    fun onGoCallTerminated(callId: String, reason: String) {
        mainScope.launch {
            val cur = _uiState.value
            if (!shouldDismissCallFromGoSignal(cur, callId)) return@launch

            val cleanReason = reason.trim()
            val wasMissed = cleanReason.equals("missed", ignoreCase = true) ||
                cleanReason.equals("timeout", ignoreCase = true) ||
                cleanReason.equals("unanswered", ignoreCase = true)
            recordFinishedCallLog(
                cur = cur,
                titleOverride = when {
                    wasMissed && cur.direction == CallDirection.INCOMING ->
                        if (cur.isVideo) "Missed video call" else "Missed voice call"
                    else -> null
                },
                subtitleOverride = when {
                    cur.phase == CallPhase.ACTIVE && cur.durationSec > 0 -> null
                    wasMissed && cur.direction == CallDirection.INCOMING -> "Tap to call back"
                    cleanReason.equals("busy", ignoreCase = true) -> "No answer"
                    cleanReason.equals("rejected", ignoreCase = true) ||
                        cleanReason.equals("declined", ignoreCase = true) ->
                        if (cur.direction == CallDirection.OUTGOING) "No answer" else "Declined"
                    cleanReason.equals("terminated", ignoreCase = true) && cur.direction == CallDirection.INCOMING && cur.phase == CallPhase.INCOMING_RINGING ->
                        "Tap to call back"
                    else -> if (cur.durationSec > 0) null else "No answer"
                }
            )
            val showReason = cur.phase != CallPhase.ACTIVE &&
                cleanReason.isNotBlank() &&
                !cleanReason.equals("ended", ignoreCase = true) &&
                !cleanReason.equals("closed", ignoreCase = true)
            if (showReason) {
                failCall(formatTerminationReason(cleanReason))
            } else {
                resetToIdle()
            }
        }
    }

    fun onGoCallAcceptedElsewhere(callId: String) {
        mainScope.launch {
            val cur = _uiState.value
            if (!shouldDismissCallFromGoSignal(cur, callId)) return@launch
            recordFinishedCallLog(
                cur = cur,
                titleOverride = null,
                subtitleOverride = "Answered elsewhere"
            )
            resetToIdle()
        }
    }

    fun onNativeCallStateUpdate(callId: String, state: String, isVideo: Boolean) {
        mainScope.launch {
            val cur = _uiState.value
            when (val decision = resolveNativeCallStateUpdate(cur, callId, state, isVideo)) {
                is CallStateUpdateDecision.Ignore -> return@launch
                is CallStateUpdateDecision.SyncState -> {
                    _uiState.value = decision.newState
                    appContext?.let { CallForegroundService.sync(it, decision.newState) }
                }
                is CallStateUpdateDecision.ActivateCall -> {
                    transitionToActive(decision.effectiveCallId, decision.remoteVideo)
                }
            }
        }
    }

    fun onNativeVideoStateUpdate(
        callId: String,
        videoState: String,
        orientationQuarterTurns: Int,
        upgradePending: Boolean,
        isVideo: Boolean
    ) {
        mainScope.launch {
            val cur = _uiState.value
            if (cur.phase == CallPhase.IDLE || cur.phase == CallPhase.FAILED) return@launch
            if (callId.isNotBlank() && cur.callId.isNotBlank() && cur.callId != callId) return@launch

            val activeId = cur.callId.ifBlank { callId }

            when (videoState.trim()) {
                "Enabled", "UpgradeAccept" -> {
                    _uiState.update {
                        it.copy(
                            isVideo = true,
                            hasRemoteVideo = true,
                            pendingVideoUpgrade = false,
                            remoteOrientationDegrees = if (!it.isRtpOrientationActive) {
                                resolveRemoteOrientation(it.remoteOrientationDegrees, orientationQuarterTurns)
                            } else it.remoteOrientationDegrees
                        )
                    }
                    audioController?.updateVideoState(true)
                    ensureMediaPipelinesStarted(activeId, startVideo = true)
                    appContext?.let { CallForegroundService.sync(it, _uiState.value) }
                }
                "UpgradeRequest" -> {
                    _uiState.update {
                        it.copy(
                            pendingVideoUpgrade = upgradePending,
                            remoteOrientationDegrees = if (!it.isRtpOrientationActive) {
                                resolveRemoteOrientation(it.remoteOrientationDegrees, orientationQuarterTurns)
                            } else it.remoteOrientationDegrees
                        )
                    }
                }
                "UpgradeReject" -> {
                    _uiState.update { it.copy(pendingVideoUpgrade = false) }
                }
                "Paused", "Held", "Disabled", "Stopped" -> {
                    _uiState.update {
                        it.copy(
                            hasRemoteVideo = false,
                            isRtpOrientationActive = false,
                            isVideo = isVideo || !it.isCameraOff,
                            pendingVideoUpgrade = false,
                            remoteOrientationDegrees = resolveRemoteOrientation(it.remoteOrientationDegrees, orientationQuarterTurns)
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        if (!it.isRtpOrientationActive) {
                            it.copy(remoteOrientationDegrees = resolveRemoteOrientation(it.remoteOrientationDegrees, orientationQuarterTurns))
                        } else it
                    }
                }
            }
        }
    }

    fun onNativeKeyframeNeeded(callId: String) {
        val cur = _uiState.value
        if (callId.isNotBlank() && cur.callId.isNotBlank() && cur.callId != callId) return
        videoPipeline?.requestSyncKeyframe()
    }

    fun onMediaPermissionsGranted() {
        val cur = _uiState.value
        if (cur.phase == CallPhase.IDLE || cur.phase == CallPhase.FAILED) return
        if (cur.callId.isNotBlank()) {
            ensureMediaPipelinesStarted(cur.callId, startVideo = cur.isVideo)
        }
    }

    fun toggleMute() {
        val cur = _uiState.value
        if (!cur.isInProgress) return
        val next = !cur.isMuted
        _uiState.update { it.copy(isMuted = next) }
        audioPipeline?.setMuted(next)
        if (cur.callId.isNotBlank()) {
            ioScope.launch { runCatching { Whatsbridge.setCallMuted(cur.callId, next) } }
        }
    }

    fun toggleCallRecording() {
        val cur = _uiState.value
        val ctx = appContext ?: return
        val pipeline = audioPipeline ?: return
        if (cur.phase != CallPhase.ACTIVE && !cur.isRecording) return

        if (cur.isRecording) {
            val saved = runCatching { pipeline.stopRecording() }.getOrNull()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    recordingStartedEpochMs = 0L,
                    recordingDurationSec = 0,
                    lastRecordingPath = saved?.absolutePath ?: it.lastRecordingPath
                )
            }
            mainScope.launch {
                val msg = if (saved != null) "Call recording saved to Media Vault" else "Call recording stopped"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            val dir = EagerMediaVault.callRecordingsVaultDir(ctx)
            val safePeer = cur.peerName
                .ifBlank { cur.peerPhoneDigits }
                .ifBlank { cur.peerJid.substringBefore("@") }
                .let { EagerMediaVault.sanitizeVaultFolderName(it) }
                .take(24)
            val outFile = java.io.File(dir, "call_${safePeer}_${System.currentTimeMillis()}.wav")
            if (pipeline.startRecording(outFile)) {
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        recordingStartedEpochMs = now,
                        recordingDurationSec = 0
                    )
                }
                mainScope.launch {
                    Toast.makeText(ctx, "Recording call locally…", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleCamera() {
        val cur = _uiState.value
        if (!cur.isInProgress) return
        val nextOff = !cur.isCameraOff
        val enable = !nextOff
        _uiState.update {
            it.copy(
                isCameraOff = nextOff,
                isVideo = it.isVideo || enable,
                pendingVideoUpgrade = false
            )
        }
        if (enable && cur.callId.isNotBlank()) {
            audioController?.updateVideoState(true)
            ensureMediaPipelinesStarted(cur.callId, startVideo = true)
        }
        videoPipeline?.setCameraEnabled(enable && hasCameraPermission())
        if (cur.callId.isNotBlank()) {
            ioScope.launch {
                runCatching { Whatsbridge.setCallVideoEnabled(cur.callId, enable) }
                if (enable) {
                    mainScope.launch {
                        ensureMediaPipelinesStarted(cur.callId, startVideo = true)
                        videoPipeline?.requestSyncKeyframe()
                    }
                    runCatching { Whatsbridge.requestPeerKeyframe(cur.callId) }
                }
            }
        }
    }

    fun upgradeToVideo() {
        val cur = _uiState.value
        if (!cur.isInProgress) return
        _uiState.update {
            it.copy(
                isVideo = true,
                isCameraOff = false,
                pendingVideoUpgrade = false
            )
        }
        audioController?.updateVideoState(true)
        if (cur.callId.isNotBlank()) {
            ensureMediaPipelinesStarted(cur.callId, startVideo = true)
            videoPipeline?.setCameraEnabled(hasCameraPermission())
            ioScope.launch {
                runCatching { Whatsbridge.setCallVideoEnabled(cur.callId, true) }
                mainScope.launch {
                    ensureMediaPipelinesStarted(cur.callId, startVideo = true)
                    videoPipeline?.requestSyncKeyframe()
                }
                runCatching { Whatsbridge.requestPeerKeyframe(cur.callId) }
            }
        }
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }
    }

    fun declineVideoUpgrade() {
        val cur = _uiState.value
        if (!cur.pendingVideoUpgrade) return
        _uiState.update { it.copy(pendingVideoUpgrade = false) }
        if (cur.callId.isNotBlank() && videoPipeline != null) {
            ioScope.launch { runCatching { Whatsbridge.setCallVideoEnabled(cur.callId, false) } }
        }
    }

    fun flipCamera() {
        videoPipeline?.switchCamera()
    }

    fun setAudioRoute(route: CallAudioRoute) {
        audioController?.setAudioRoute(route)
    }

    fun attachLocalVideoSurface(surface: Surface?) {
        localPreviewSurface = surface
        videoPipeline?.setLocalPreviewSurface(surface)
    }

    fun attachRemoteVideoSurface(surface: Surface?) {
        remoteVideoSurface = surface
        videoPipeline?.setRemoteSurface(surface)
    }

    fun dismissFailedScreen() {
        failedDismissJob?.cancel()
        if (_uiState.value.phase == CallPhase.FAILED) {
            resetToIdle()
        }
    }

    fun launchCallActivity(context: Context) {
        runCatching {
            val intent = Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Failed launching CallActivity: ${it.message}")
        }
    }

    fun openOfficialWhatsAppFallback() {
        val ctx = appContext ?: return
        val cur = _uiState.value
        openOfficialWhatsApp(ctx, cur.peerPhoneDigits)
        if (cur.phase != CallPhase.IDLE) {
            resetToIdle()
        }
    }

    fun openOfficialWhatsApp(context: Context, phoneDigits: String = "") {
        val cleanDigits = phoneDigits.filter { it.isDigit() }
        val pm = context.packageManager
        val targetPkg = OfficialWhatsAppHelper.findInstalledPackage(context)

        val intent = if (cleanDigits.length >= 7) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanDigits")).apply {
                targetPkg?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else if (targetPkg != null) {
            pm.getLaunchIntentForPackage(targetPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else null

        if (intent != null) {
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "Official WhatsApp is not installed", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Official WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun transitionToActive(callId: String, remoteVideo: Boolean) {
        val cur = _uiState.value
        if (cur.phase == CallPhase.IDLE || cur.phase == CallPhase.FAILED) return

        val effectiveId = callId.ifBlank { cur.callId }
        val wasActive = cur.phase == CallPhase.ACTIVE
        val startedAt = if (cur.connectedAtEpochMs > 0L) cur.connectedAtEpochMs else System.currentTimeMillis()
        val videoMode = cur.isVideo || remoteVideo

        _uiState.update {
            it.copy(
                callId = effectiveId,
                phase = CallPhase.ACTIVE,
                peerAccepted = true,
                isVideo = videoMode,
                connectedAtEpochMs = startedAt,
                statusText = if (wasActive) it.statusText else formatCallTimer(it.durationSec)
            )
        }

        audioController?.startInCallAudio(isVideo = videoMode)
        if (effectiveId.isNotBlank()) {
            ensureMediaPipelinesStarted(effectiveId, startVideo = videoMode)
            if (!wasActive && StealthMatrixManager.config.value.autoRecordCalls && !_uiState.value.isRecording) {
                toggleCallRecording()
            }
        }
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }

        if (callTimerJob?.isActive != true) {
            callTimerJob = mainScope.launch {
                while (isActive) {
                    val snap = _uiState.value
                    if (snap.phase != CallPhase.ACTIVE) break
                    val now = System.currentTimeMillis()
                    val elapsedSec = ((now - snap.connectedAtEpochMs) / 1000L)
                        .toInt()
                        .coerceAtLeast(0)
                    val recSec = if (snap.isRecording && snap.recordingStartedEpochMs > 0L) {
                        ((now - snap.recordingStartedEpochMs) / 1000L).toInt().coerceAtLeast(0)
                    } else 0
                    _uiState.update {
                        it.copy(
                            durationSec = elapsedSec,
                            statusText = formatCallTimer(elapsedSec),
                            recordingDurationSec = recSec
                        )
                    }
                    delay(1000L)
                }
            }
        }
    }

    @Synchronized
    private fun ensureMediaPipelinesStarted(callId: String, startVideo: Boolean) {
        val ctx = appContext ?: return
        if (callId.isBlank()) return

        if (audioPipeline == null && hasMicPermission()) {
            audioPipeline = NativeVoipAudioPipeline(callId).apply {
                setMuted(_uiState.value.isMuted)
                start()
            }
        }

        if (startVideo) {
            val sendCam = !_uiState.value.isCameraOff && hasCameraPermission()
            if (videoPipeline == null) {
                videoPipeline = NativeVoipVideoPipeline(
                    context = ctx,
                    callId = callId,
                    onRemoteOrientationDegrees = { deg ->
                        mainScope.launch {
                            _uiState.update {
                                it.copy(
                                    remoteOrientationDegrees = deg,
                                    isRtpOrientationActive = true
                                )
                            }
                        }
                    },
                    onRemoteCodecRotationDegrees = { rotDeg ->
                        mainScope.launch {
                            _uiState.update { it.copy(remoteCodecRotationDegrees = rotDeg) }
                        }
                    },
                    onRemoteVideoSizeChanged = { w, h ->
                        mainScope.launch {
                            _uiState.update {
                                it.copy(
                                    remoteVideoWidth = w,
                                    remoteVideoHeight = h,
                                    hasRemoteVideo = true
                                )
                            }
                        }
                    },
                    onLocalVideoSizeChanged = { w, h ->
                        mainScope.launch {
                            _uiState.update {
                                it.copy(
                                    localVideoWidth = w,
                                    localVideoHeight = h
                                )
                            }
                        }
                    },
                    onRemoteFirstFrameDecoded = {
                        mainScope.launch {
                            _uiState.update { cur ->
                                if (cur.hasRemoteVideo) cur else cur.copy(hasRemoteVideo = true)
                            }
                        }
                    }
                ).apply {
                    setLocalPreviewSurface(localPreviewSurface)
                    setRemoteSurface(remoteVideoSurface)
                    start(enableSendCamera = sendCam)
                }
            } else {
                videoPipeline?.setCameraEnabled(sendCam)
            }
        }
    }

    @Synchronized
    private fun stopPipelines() {
        runCatching { audioPipeline?.stop() }
        audioPipeline = null
        runCatching { videoPipeline?.stop() }
        videoPipeline = null
    }

    private fun failCall(reason: String) {
        AppLogManager.log("WARN", "VoIP", "Call ended/failed: $reason")
        callTimerJob?.cancel()
        callTimerJob = null
        stopPipelines()
        audioController?.stopAll()

        _uiState.update {
            it.copy(
                phase = CallPhase.FAILED,
                statusText = "Call ended",
                errorReason = reason
            )
        }
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }

        failedDismissJob?.cancel()
        failedDismissJob = mainScope.launch {
            delay(3000L)
            if (_uiState.value.phase == CallPhase.FAILED) {
                resetToIdle()
            }
        }
    }

    private fun resetToIdle() {
        callTimerJob?.cancel()
        callTimerJob = null
        failedDismissJob?.cancel()
        failedDismissJob = null
        stopPipelines()
        audioController?.stopAll()
        _uiState.value = CallUiState(phase = CallPhase.IDLE)
        appContext?.let { CallForegroundService.sync(it, _uiState.value) }
    }

    private fun hasMicPermission(): Boolean {
        val ctx = appContext ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        val ctx = appContext ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun formatTerminationReason(raw: String): String = when (raw.lowercase()) {
        "busy" -> "Contact is busy"
        "rejected", "declined" -> "Call declined"
        "timeout", "unanswered" -> "No answer"
        "unavailable" -> "Recipient unavailable"
        else -> raw
    }

    private fun recordFinishedCallLog(
        cur: CallUiState,
        titleOverride: String?,
        subtitleOverride: String?
    ) {
        val ctx = appContext ?: return
        if (cur.peerJid.isBlank()) return
        if (!shouldPersistLocalCallLog(cur.peerJid, cur.isGroup)) return
        val dedupKey = cur.callId.ifBlank { "${cur.peerJid}_${System.currentTimeMillis() / 10_000L}" }
        if (loggedCallIds.size > 512) {
            loggedCallIds.clear()
        }
        if (!loggedCallIds.add(dedupKey)) return
        val tsMs = if (cur.connectedAtEpochMs > 0L) cur.connectedAtEpochMs else System.currentTimeMillis()
        WaRealtimeService.recordLocalCallLog(
            context = ctx,
            callId = cur.callId,
            peerJid = cur.peerJid,
            isFromMe = cur.direction == CallDirection.OUTGOING,
            isVideo = cur.isVideo,
            isGroup = cur.isGroup,
            timestampMs = tsMs,
            durationSec = cur.durationSec,
            titleOverride = titleOverride,
            subtitleOverride = subtitleOverride,
            senderJid = cur.senderJid.ifBlank { cur.peerJid },
            senderName = cur.senderName
        )
    }
}

internal fun isCallStartAllowed(phase: CallPhase): Boolean =
    phase == CallPhase.IDLE || phase == CallPhase.FAILED

internal fun resolveIncomingCallTargetChatJid(
    chatJid: String,
    callerJid: String,
    fromJid: String,
    isGroup: Boolean
): String {
    val cleanChat = chatJid.trim()
    val cleanCaller = callerJid.trim().ifBlank { fromJid.trim() }
    if (cleanChat.endsWith("@g.us")) return cleanChat
    if (isGroup && cleanChat.isNotBlank()) return cleanChat
    return cleanChat.ifBlank { cleanCaller }
}

internal fun shouldPersistLocalCallLog(
    targetChatJid: String,
    isGroup: Boolean
): Boolean {
    val clean = targetChatJid.trim()
    if (clean.isEmpty()) return false
    return !isGroup || clean.endsWith("@g.us")
}
