package com.wa.beastclient.ui.calling

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.wa.beastclient.core.calling.CallAudioRoute
import com.wa.beastclient.core.calling.CallPhase
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.CallUiState
import com.wa.beastclient.core.calling.computeSmartVideoDisplayScale
import com.wa.beastclient.core.calling.formatCallTimer
import com.wa.beastclient.core.calling.resolveEffectiveRemoteDisplayRotation
import com.wa.beastclient.core.calling.shouldShowLocalPreviewAsPip
import com.wa.beastclient.domain.stealth.CallPipTapAction
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocaleHelper
import com.wa.beastclient.ui.theme.WhatsAppTheme

/**
 * Full-screen native Voice & Video call interface backed by `whatsapp-rust` VoIP pipelines.
 * Supports lockscreen wake-up, minimize-to-banner for voice calls, and Android PiP for video calls.
 */
class CallActivity : ComponentActivity() {

    private var inPipModeState by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CallStateManager.init(this)

        setContent {
            val stealth by StealthMatrixManager.config.collectAsState()
            val callState by CallStateManager.uiState.collectAsState()
            var pendingAnswerAudioOnly by rememberSaveable(callState.callId) { mutableStateOf<Boolean?>(null) }
            var pendingUpgradeToVideo by rememberSaveable(callState.callId) { mutableStateOf(false) }
            var pendingEnableCamera by rememberSaveable(callState.callId) { mutableStateOf(false) }

            val callPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val camGranted = grants[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val audioOnly = pendingAnswerAudioOnly
                val wantUpgrade = pendingUpgradeToVideo
                val wantEnableCam = pendingEnableCamera
                pendingAnswerAudioOnly = null
                pendingUpgradeToVideo = false
                pendingEnableCamera = false
                when {
                    micGranted && audioOnly != null -> {
                        CallStateManager.answerIncomingCall(audioOnly = audioOnly || !camGranted)
                    }
                    camGranted && wantUpgrade -> {
                        CallStateManager.upgradeToVideo()
                    }
                    camGranted && wantEnableCam -> {
                        CallStateManager.toggleCamera()
                    }
                    micGranted || camGranted -> {
                        CallStateManager.onMediaPermissionsGranted()
                    }
                }
            }

            fun requestPermsAndAnswer(audioOnly: Boolean, needsCamera: Boolean) {
                val needed = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.RECORD_AUDIO)
                }
                if (needsCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.CAMERA)
                }
                if (needed.isEmpty()) {
                    CallStateManager.answerIncomingCall(audioOnly)
                } else {
                    pendingAnswerAudioOnly = audioOnly
                    pendingUpgradeToVideo = false
                    pendingEnableCamera = false
                    callPermissionLauncher.launch(needed.toTypedArray())
                }
            }

            fun requestCameraThenUpgrade() {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    CallStateManager.upgradeToVideo()
                } else {
                    pendingAnswerAudioOnly = null
                    pendingUpgradeToVideo = true
                    pendingEnableCamera = false
                    callPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }

            fun requestCameraThenEnable() {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    CallStateManager.toggleCamera()
                } else {
                    pendingAnswerAudioOnly = null
                    pendingUpgradeToVideo = false
                    pendingEnableCamera = true
                    callPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }

            LaunchedEffect(callState.phase, callState.isVideo) {
                if (callState.phase == CallPhase.IDLE) {
                    finish()
                    return@LaunchedEffect
                }
                if (callState.phase == CallPhase.DIALING ||
                    callState.phase == CallPhase.CONNECTING ||
                    callState.phase == CallPhase.ACTIVE
                ) {
                    val needed = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (callState.isVideo && !callState.isCameraOff &&
                        ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ) {
                        needed.add(Manifest.permission.CAMERA)
                    }
                    if (needed.isNotEmpty()) {
                        pendingAnswerAudioOnly = null
                        pendingUpgradeToVideo = false
                        pendingEnableCamera = false
                        callPermissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            BackHandler {
                when (callState.phase) {
                    CallPhase.FAILED -> CallStateManager.dismissFailedScreen()
                    CallPhase.INCOMING_RINGING -> finish()
                    else -> finish()
                }
            }

            WhatsAppTheme(
                themeMode = stealth.themeMode,
                accentColor = stealth.accentColor,
                pureBlackOled = stealth.pureBlackOled,
                dynamicColors = stealth.dynamicMaterialYou,
                useSystemFont = stealth.useSystemFont,
                fontStyle = stealth.fontStyle,
                customFontPath = stealth.customFontPath,
                bubbleCornerRadiusDp = stealth.bubbleCornerRadiusDp,
                customOutgoingBubbleArgb = stealth.customOutgoingBubbleArgb,
                customIncomingBubbleArgb = stealth.customIncomingBubbleArgb,
                showSecondsInTimestamps = stealth.showSecondsInTimestamps,
                use24HourFormat = StealthMatrixManager.is24HourEnabled(this, stealth.clockFormat),
                chatFontScale = stealth.chatFontScale,
                classicUi = stealth.classicWhatsAppLook,
                assetManager = assets
            ) {
                CallScreenContent(
                    state = callState,
                    isInPip = inPipModeState,
                    pipTapAction = stealth.callPipTapAction,
                    onTogglePipTapAction = {
                        val next = if (stealth.callPipTapAction == CallPipTapAction.SWAP_LAYOUT) {
                            CallPipTapAction.FLIP_CAMERA
                        } else {
                            CallPipTapAction.SWAP_LAYOUT
                        }
                        StealthMatrixManager.updateConfig { it.copy(callPipTapAction = next) }
                    },
                    onMinimize = { finish() },
                    onAnswer = { audioOnly -> requestPermsAndAnswer(audioOnly, callState.isVideo && !audioOnly) },
                    onDeclineOrEnd = { CallStateManager.declineOrEndCall() },
                    onToggleMute = { CallStateManager.toggleMute() },
                    onToggleRecording = { CallStateManager.toggleCallRecording() },
                    onToggleCamera = {
                        when {
                            !callState.isVideo -> requestCameraThenUpgrade()
                            callState.isCameraOff -> requestCameraThenEnable()
                            else -> CallStateManager.toggleCamera()
                        }
                    },
                    onFlipCamera = { CallStateManager.flipCamera() },
                    onSelectAudioRoute = { route -> CallStateManager.setAudioRoute(route) },
                    onOpenOfficialWa = { CallStateManager.openOfficialWhatsAppFallback() },
                    onDismissFailed = { CallStateManager.dismissFailedScreen() },
                    onAcceptVideoUpgrade = { requestCameraThenUpgrade() },
                    onDeclineVideoUpgrade = { CallStateManager.declineVideoUpgrade() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = CallStateManager.uiState.value
        if (state.phase == CallPhase.ACTIVE && state.isVideo) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipModeState = isInPictureInPictureMode
    }
}

@Composable
private fun CallScreenContent(
    state: CallUiState,
    isInPip: Boolean,
    pipTapAction: CallPipTapAction = CallPipTapAction.SWAP_LAYOUT,
    onTogglePipTapAction: () -> Unit = {},
    onMinimize: () -> Unit,
    onAnswer: (audioOnly: Boolean) -> Unit,
    onDeclineOrEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onSelectAudioRoute: (CallAudioRoute) -> Unit,
    onOpenOfficialWa: () -> Unit,
    onDismissFailed: () -> Unit,
    onAcceptVideoUpgrade: () -> Unit,
    onDeclineVideoUpgrade: () -> Unit
) {
    val mono = LocalWaMonoFamily.current
    val waColors = com.wa.beastclient.ui.theme.LocalWaColors.current
    val accentGreen = waColors.brightGreen
    val showVideoFeed = state.isVideo && (
        state.phase == CallPhase.ACTIVE ||
            state.phase == CallPhase.CONNECTING ||
            state.phase == CallPhase.DIALING
        ) && (!state.isCameraOff || state.hasRemoteVideo)
    var showChrome by remember { mutableStateOf(true) }
    var showRouteMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isLayoutSwapped by rememberSaveable(state.callId) { mutableStateOf(false) }
    var manualRemoteRotationOffset by rememberSaveable(state.callId) { mutableIntStateOf(0) }
    var isRemoteVideoFitMode by rememberSaveable(state.callId) { mutableStateOf(false) }

    val canSwapViews = showVideoFeed && !state.isCameraOff && state.hasRemoteVideo
    val effectiveSwapped = isLayoutSwapped && canSwapViews
    val showLocalAsPip = shouldShowLocalPreviewAsPip(
        hasRemoteVideo = state.hasRemoteVideo,
        effectiveSwapped = effectiveSwapped
    )

    DisposableEffect(Unit) {
        onDispose {
            CallStateManager.attachRemoteVideoSurface(null)
            CallStateManager.attachLocalVideoSurface(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B1419),
                        Color(0xFF102229),
                        Color(0xFF091115)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showVideoFeed && !isInPip) {
                    showChrome = !showChrome
                }
            }
    ) {
        if (showVideoFeed) {
            val effectiveRemoteDeg = resolveEffectiveRemoteDisplayRotation(
                videoWidth = state.remoteVideoWidth,
                videoHeight = state.remoteVideoHeight,
                wireOrientationDegrees = state.remoteOrientationDegrees,
                codecRotationDegrees = state.remoteCodecRotationDegrees,
                manualOffsetDegrees = manualRemoteRotationOffset
            )
            val remoteCwDegState by rememberUpdatedState(effectiveRemoteDeg)
            val remoteWState by rememberUpdatedState(state.remoteVideoWidth)
            val remoteHState by rememberUpdatedState(state.remoteVideoHeight)
            val remoteFillCropState by rememberUpdatedState(if (effectiveSwapped) true else !isRemoteVideoFitMode)
            val remoteMaxCropState by rememberUpdatedState(if (effectiveSwapped) 0.99f else 0.28f)
            val localWState by rememberUpdatedState(state.localVideoWidth)
            val localHState by rememberUpdatedState(state.localVideoHeight)
            val localMaxCropState by rememberUpdatedState(if (showLocalAsPip) 0.99f else 0.35f)

            val pipCardModifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 74.dp, end = 16.dp)
                .size(width = 108.dp, height = 162.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101C22))
                .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .zIndex(2f)

            val handlePipTap = {
                if (pipTapAction == CallPipTapAction.SWAP_LAYOUT && canSwapViews) {
                    isLayoutSwapped = !isLayoutSwapped
                } else {
                    onFlipCamera()
                }
            }

            // Remote video feed (either Full-Screen main view or Mini PiP when swapped)
            if (state.hasRemoteVideo && !(effectiveSwapped && isInPip)) {
                Box(
                    modifier = (if (effectiveSwapped) pipCardModifier.clickable(onClick = handlePipTap) else Modifier.fillMaxSize().zIndex(0f))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                isOpaque = false
                                var boundSurface: Surface? = null
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        applyVideoTextureTransform(
                                            textureView = this@apply,
                                            viewWidth = w,
                                            viewHeight = h,
                                            videoWidth = remoteWState,
                                            videoHeight = remoteHState,
                                            cwRotationDeg = remoteCwDegState,
                                            fillCrop = remoteFillCropState,
                                            maxCropFraction = remoteMaxCropState
                                        )
                                        val s = Surface(st)
                                        boundSurface = s
                                        CallStateManager.attachRemoteVideoSurface(s)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                        applyVideoTextureTransform(
                                            textureView = this@apply,
                                            viewWidth = w,
                                            viewHeight = h,
                                            videoWidth = remoteWState,
                                            videoHeight = remoteHState,
                                            cwRotationDeg = remoteCwDegState,
                                            fillCrop = remoteFillCropState,
                                            maxCropFraction = remoteMaxCropState
                                        )
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        val staleSurface = boundSurface
                                        boundSurface = null
                                        CallStateManager.attachRemoteVideoSurface(null) {
                                            runCatching { staleSurface?.release() }
                                            runCatching { st.release() }
                                        }
                                        return false
                                    }
                                }
                            }
                        },
                        update = { tv ->
                            val curW = state.remoteVideoWidth
                            val curH = state.remoteVideoHeight
                            val curDeg = effectiveRemoteDeg
                            val curFill = if (effectiveSwapped) true else !isRemoteVideoFitMode
                            val curMaxCrop = if (effectiveSwapped) 0.99f else 0.28f
                            if (tv.width > 0 && tv.height > 0) {
                                applyVideoTextureTransform(
                                    textureView = tv,
                                    viewWidth = tv.width,
                                    viewHeight = tv.height,
                                    videoWidth = curW,
                                    videoHeight = curH,
                                    cwRotationDeg = curDeg,
                                    fillCrop = curFill,
                                    maxCropFraction = curMaxCrop
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (effectiveSwapped) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x73000000))
                                .padding(vertical = 3.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.peerName.ifBlank { "Peer" },
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Local camera feed (Full-Screen while dialing / peer camera off / swapped, or Mini PiP)
            if (!state.isCameraOff && (!showLocalAsPip || !isInPip)) {
                Box(
                    modifier = if (showLocalAsPip) {
                        pipCardModifier.clickable(onClick = handlePipTap)
                    } else {
                        Modifier.fillMaxSize().zIndex(0f)
                    }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                var boundSurface: Surface? = null
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        val bufW = localHState.coerceAtLeast(1)
                                        val bufH = localWState.coerceAtLeast(1)
                                        st.setDefaultBufferSize(bufW, bufH)
                                        tag = bufW to bufH
                                        applyLocalPreviewTransform(
                                            view = this@apply,
                                            width = w,
                                            height = h,
                                            localPortraitW = localWState,
                                            localPortraitH = localHState,
                                            maxCropFraction = localMaxCropState
                                        )
                                        val s = Surface(st)
                                        boundSurface = s
                                        CallStateManager.attachLocalVideoSurface(s)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                        applyLocalPreviewTransform(
                                            view = this@apply,
                                            width = w,
                                            height = h,
                                            localPortraitW = localWState,
                                            localPortraitH = localHState,
                                            maxCropFraction = localMaxCropState
                                        )
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        val staleSurface = boundSurface
                                        boundSurface = null
                                        CallStateManager.attachLocalVideoSurface(null) {
                                            runCatching { staleSurface?.release() }
                                            runCatching { st.release() }
                                        }
                                        return false
                                    }
                                }
                            }
                        },
                        update = { tv ->
                            val curLW = state.localVideoWidth
                            val curLH = state.localVideoHeight
                            val targetBuf = curLH.coerceAtLeast(1) to curLW.coerceAtLeast(1)
                            if (tv.tag != targetBuf) {
                                tv.surfaceTexture?.setDefaultBufferSize(targetBuf.first, targetBuf.second)
                                tv.tag = targetBuf
                            }
                            if (tv.width > 0 && tv.height > 0) {
                                applyLocalPreviewTransform(
                                    view = tv,
                                    width = tv.width,
                                    height = tv.height,
                                    localPortraitW = curLW,
                                    localPortraitH = curLH,
                                    maxCropFraction = if (showLocalAsPip) 0.99f else 0.35f
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showLocalAsPip && state.isMuted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xB3000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MicOff,
                                contentDescription = "Muted",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (showLocalAsPip) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0x73000000))
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "You",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            VoiceCallBackdropContent(state = state, isInPip = isInPip, accentColor = accentGreen)
        }

        if (showVideoFeed && !isInPip) {
            // Subtle top and bottom edge scrims keep header text and dock legible over bright video.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(148.dp)
                    .zIndex(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xA6000000), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(180.dp)
                    .zIndex(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xB8000000))
                        )
                    )
            )
        }

        if (!isInPip) {
            AnimatedVisibility(
                visible = !showVideoFeed || showChrome || showMoreMenu || showRouteMenu,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize().zIndex(3f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top header bar: minimize button left, centered title & timer, flip/REC right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x99142026))
                                .border(1.dp, Color(0x26FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloseFullscreen,
                                contentDescription = "Minimize",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp)
                        ) {
                            if (showVideoFeed) {
                                Text(
                                    text = state.peerName.ifBlank { "Call" },
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = state.statusText.ifBlank { "Connecting…" },
                                    color = if (state.phase == CallPhase.ACTIVE) accentGreen else Color(0xFFE0E7EB),
                                    fontSize = 12.5.sp,
                                    fontFamily = mono,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x66142026))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = Color(0xCCDAE5EA),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (state.isVideo) "End-to-end encrypted video" else "End-to-end encrypted",
                                        color = Color(0xCCDAE5EA),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            if (state.isRecording) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xD9E53935))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FiberManualRecord,
                                        contentDescription = "Recording",
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Text(
                                        text = formatCallTimer(state.recordingDurationSec),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = mono,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (state.isVideo && !state.isCameraOff) {
                                IconButton(
                                    onClick = onFlipCamera,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x99142026))
                                        .border(1.dp, Color(0x26FFFFFF), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "Flip camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.pendingVideoUpgrade && state.phase == CallPhase.ACTIVE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xEB152026))
                                    .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Peer requested video",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onDeclineVideoUpgrade) {
                                        Text("Voice", color = Color.White, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onAcceptVideoUpgrade,
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                    ) {
                                        Text("Switch", color = Color(0xFF0A1418), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        when (state.phase) {
                            CallPhase.FAILED -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xE0152026))
                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = state.errorReason ?: "Couldn't connect call",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = onDismissFailed) {
                                            Text("Close", color = Color.White)
                                        }
                                        Button(
                                            onClick = onOpenOfficialWa,
                                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                        ) {
                                            Text("Open in Official App", color = Color(0xFF0A1418))
                                        }
                                    }
                                }
                            }

                            CallPhase.INCOMING_RINGING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(40.dp))
                                            .background(Color(0xE0141F25))
                                            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(40.dp))
                                            .padding(horizontal = 30.dp, vertical = 16.dp)
                                    ) {
                                        CallRoundAction(
                                            icon = Icons.Rounded.CallEnd,
                                            label = "Decline",
                                            containerColor = Color(0xFFE53935),
                                            contentColor = Color.White,
                                            onClick = onDeclineOrEnd
                                        )
                                        if (state.canAnswerInApp) {
                                            CallRoundAction(
                                                icon = if (state.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                                label = "Answer",
                                                containerColor = accentGreen,
                                                contentColor = Color(0xFF091418),
                                                onClick = { onAnswer(false) }
                                            )
                                        } else {
                                            CallRoundAction(
                                                icon = Icons.Rounded.Call,
                                                label = "Official",
                                                containerColor = accentGreen,
                                                contentColor = Color(0xFF091418),
                                                onClick = onOpenOfficialWa
                                            )
                                        }
                                    }
                                    if (state.canAnswerInApp && state.isVideo) {
                                        OutlinedButton(onClick = { onAnswer(true) }) {
                                            Text("Answer audio only", color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            else -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(Color(0xE0141F25))
                                        .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(36.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    // More options (Call Recording & Audio Devices)
                                    Box {
                                        CallCircleControl(
                                            icon = Icons.Rounded.MoreHoriz,
                                            contentDescription = "More call options",
                                            active = state.isRecording,
                                            activeContainer = Color(0xFFE53935),
                                            activeContent = Color.White,
                                            onClick = { showMoreMenu = true }
                                        )
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            if (state.phase == CallPhase.ACTIVE) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(if (state.isRecording) "Stop recording" else "Record call")
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (state.isRecording) Icons.Rounded.StopCircle else Icons.Rounded.FiberManualRecord,
                                                            contentDescription = null,
                                                            tint = if (state.isRecording) Color(0xFFE53935) else waColors.textPrimary
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onToggleRecording()
                                                    }
                                                )
                                            }
                                            if (canSwapViews) {
                                                DropdownMenuItem(
                                                    text = { Text("Swap main / mini view") },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PictureInPictureAlt,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        isLayoutSwapped = !isLayoutSwapped
                                                    }
                                                )
                                            }
                                            if (showVideoFeed && state.hasRemoteVideo) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (isRemoteVideoFitMode)
                                                                "Video framing: Fit full frame"
                                                            else
                                                                "Video framing: Smart fill"
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Rounded.FitScreen,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        isRemoteVideoFitMode = !isRemoteVideoFitMode
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Rotate peer video (+90°)") },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Rounded.RotateRight,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        manualRemoteRotationOffset = (manualRemoteRotationOffset + 90) % 360
                                                    }
                                                )
                                            }
                                            if (showVideoFeed) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (pipTapAction == CallPipTapAction.SWAP_LAYOUT)
                                                                "Mini-view tap: Swap"
                                                            else
                                                                "Mini-view tap: Flip camera"
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Cameraswitch,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onTogglePipTapAction()
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Minimize call") },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CloseFullscreen,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showMoreMenu = false
                                                    onMinimize()
                                                }
                                            )
                                        }
                                    }

                                    // Audio Route (Speaker / Bluetooth / Earpiece)
                                    Box {
                                        CallCircleControl(
                                            icon = routeIcon(state.audioRoute),
                                            contentDescription = state.audioRoute.label,
                                            active = state.audioRoute != CallAudioRoute.EARPIECE,
                                            onClick = {
                                                if (state.availableRoutes.size <= 2) {
                                                    state.availableRoutes.firstOrNull { it != state.audioRoute }
                                                        ?.let(onSelectAudioRoute)
                                                } else {
                                                    showRouteMenu = true
                                                }
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = showRouteMenu,
                                            onDismissRequest = { showRouteMenu = false }
                                        ) {
                                            state.availableRoutes.forEach { route ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (route == state.audioRoute) "${route.label} ✓" else route.label
                                                        )
                                                    },
                                                    onClick = {
                                                        showRouteMenu = false
                                                        onSelectAudioRoute(route)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Camera Toggle
                                    CallCircleControl(
                                        icon = if (state.isVideo && !state.isCameraOff) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                        contentDescription = if (!state.isVideo || state.isCameraOff) "Turn camera on" else "Turn camera off",
                                        active = state.isVideo && !state.isCameraOff,
                                        onClick = onToggleCamera
                                    )

                                    // Microphone Mute
                                    CallCircleControl(
                                        icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
                                        active = state.isMuted,
                                        activeContent = Color(0xFFD32F2F),
                                        onClick = onToggleMute
                                    )

                                    // End Call
                                    CallCircleControl(
                                        icon = Icons.Rounded.CallEnd,
                                        contentDescription = "End call",
                                        active = true,
                                        activeContainer = Color(0xFFE53935),
                                        activeContent = Color.White,
                                        onClick = onDeclineOrEnd
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCallBackdropContent(
    state: CallUiState,
    isInPip: Boolean,
    accentColor: Color
) {
    val mono = LocalWaMonoFamily.current
    val pulseTransition = rememberInfiniteTransition(label = "callAura")
    val auraScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )
    val shouldPulse = state.phase == CallPhase.DIALING ||
        state.phase == CallPhase.INCOMING_RINGING ||
        state.phase == CallPhase.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (shouldPulse && !isInPip) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(auraScale)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.20f))
                )
            }
            WhatsAppAvatar(
                jid = state.peerJid.ifBlank { "call" },
                avatarPath = state.peerAvatarPath,
                isGroup = state.isGroup,
                size = if (isInPip) 68.dp else 120.dp
            )
        }
        if (!isInPip) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = state.peerName.ifBlank { "Incoming Call" },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isGroup && state.senderName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Started by ${state.senderName}",
                    color = Color(0xCCD1D7DB),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (state.peerPhoneDigits.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${state.peerPhoneDigits}",
                    color = Color(0xB3D1D7DB),
                    fontSize = 14.sp,
                    fontFamily = mono
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.statusText.ifBlank { "Calling…" },
                color = if (state.phase == CallPhase.ACTIVE) accentColor else Color(0xFFD1D7DB),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (state.phase == CallPhase.ACTIVE) mono else null
            )
        }
    }
}

@Composable
private fun CallRoundAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CallCircleControl(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    activeContainer: Color = Color.White,
    activeContent: Color = Color(0xFF0A1418),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (active) activeContainer else Color(0x2BFFFFFF))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeContent else Color.White,
            modifier = Modifier.size(23.dp)
        )
    }
}

private fun applyLocalPreviewTransform(
    view: TextureView,
    width: Int,
    height: Int,
    localPortraitW: Int = 480,
    localPortraitH: Int = 640,
    maxCropFraction: Float = 0.35f
) {
    // Camera2 supplies the sensor transform to TextureView. Compensate for screen rotation.
    val degrees = when (view.display?.rotation) {
        Surface.ROTATION_90 -> 270
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 90
        else -> 0
    }
    applyVideoTextureTransform(
        textureView = view,
        viewWidth = width,
        viewHeight = height,
        videoWidth = localPortraitW.coerceAtLeast(1),
        videoHeight = localPortraitH.coerceAtLeast(1),
        cwRotationDeg = degrees,
        fillCrop = true,
        maxCropFraction = maxCropFraction
    )
}

private fun applyVideoTextureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    cwRotationDeg: Int,
    fillCrop: Boolean,
    maxCropFraction: Float = 0.28f
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val vw = viewWidth.toFloat()
    val vh = viewHeight.toFloat()
    val cx = vw / 2f
    val cy = vh / 2f
    val rot = ((cwRotationDeg % 360) + 360) % 360
    val rotatedW = if (rot == 90 || rot == 270) videoHeight.toFloat() else videoWidth.toFloat()
    val rotatedH = if (rot == 90 || rot == 270) videoWidth.toFloat() else videoHeight.toFloat()

    val matrix = Matrix()
    // Undo TextureView's default full-bounds stretch back to true decoded pixel dimensions
    matrix.setScale(videoWidth.toFloat() / vw, videoHeight.toFloat() / vh, cx, cy)
    if (rot != 0) {
        matrix.postRotate(rot.toFloat(), cx, cy)
    }
    val uniformScale = computeSmartVideoDisplayScale(
        viewWidth = vw,
        viewHeight = vh,
        rotatedVideoWidth = rotatedW,
        rotatedVideoHeight = rotatedH,
        fillCrop = fillCrop,
        maxCropFraction = maxCropFraction
    )
    matrix.postScale(uniformScale, uniformScale, cx, cy)
    textureView.setTransform(matrix)
}

private fun routeIcon(route: CallAudioRoute): ImageVector = when (route) {
    CallAudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    CallAudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    CallAudioRoute.WIRED_HEADSET -> Icons.Rounded.HeadsetMic
    CallAudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
}
