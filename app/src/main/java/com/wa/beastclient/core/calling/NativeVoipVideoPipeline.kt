package com.wa.beastclient.core.calling

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.Surface
import com.wa.beastclient.core.logging.AppLogManager
import com.wa.bridge.whatsbridge.Whatsbridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal enum class AvcBitrateMode {
    VBR,
    CBR,
    DEFAULT
}

internal data class VideoEncoderCandidate(
    val width: Int,
    val height: Int,
    val bitrateMode: AvcBitrateMode,
    val bitrate: Int
)

internal fun buildAvcEncoderCandidates(): List<VideoEncoderCandidate> = listOf(
    VideoEncoderCandidate(width = 544, height = 960, bitrateMode = AvcBitrateMode.VBR, bitrate = 1_000_000),
    VideoEncoderCandidate(width = 544, height = 960, bitrateMode = AvcBitrateMode.CBR, bitrate = 1_000_000),
    VideoEncoderCandidate(width = 480, height = 848, bitrateMode = AvcBitrateMode.DEFAULT, bitrate = 850_000),
    VideoEncoderCandidate(width = 480, height = 640, bitrateMode = AvcBitrateMode.DEFAULT, bitrate = 800_000),
    VideoEncoderCandidate(width = 368, height = 480, bitrateMode = AvcBitrateMode.DEFAULT, bitrate = 500_000)
)

internal fun isSoftwareAvcCodec(name: String): Boolean {
    val lower = name.lowercase()
    return lower.startsWith("omx.google.") ||
        lower.startsWith("c2.android.") ||
        lower.contains(".sw.")
}

internal fun selectBestCameraCaptureSize(
    sizes: List<Pair<Int, Int>>,
    targetWidth: Int = 640,
    targetHeight: Int = 480
): Pair<Int, Int> {
    val valid = sizes.filter { it.first > 0 && it.second > 0 }
    if (valid.isEmpty()) return targetWidth to targetHeight
    val targetArea = targetWidth * targetHeight
    val targetRatio = targetWidth.toDouble() / targetHeight.toDouble()
    return valid.minWithOrNull(
        compareBy<Pair<Int, Int>>(
            { (w, h) -> if (w == targetWidth && h == targetHeight) 0 else 1 },
            { (w, h) ->
                val minDim = minOf(w, h)
                if (minDim >= 360) 0 else 1
            },
            { (w, h) -> abs((w.toDouble() / h.toDouble()) - targetRatio) },
            { (w, h) -> abs((w * h) - targetArea) }
        )
    ) ?: (targetWidth to targetHeight)
}

internal fun isCaptureSessionCallbackStale(
    running: Boolean,
    sendEnabled: Boolean,
    sameCameraInstance: Boolean,
    callbackSessionGen: Long,
    activeSessionGen: Long
): Boolean = !running || !sendEnabled || !sameCameraInstance || callbackSessionGen != activeSessionGen

internal fun shouldContinueEncoderDrain(
    running: Boolean,
    sendEnabled: Boolean,
    callbackEncGen: Long,
    activeEncGen: Long
): Boolean = running && sendEnabled && callbackEncGen == activeEncGen

/**
 * Computes a strictly monotonic 90 kHz RTP timestamp from a call-lifetime clock so encoder
 * restarts (camera flip or camera off->on) never jump backwards.
 */
internal fun computeMonotonicRtpTimestamp90k(
    lastTs90k: Long,
    elapsedNs: Long
): Long {
    val candidate = ((elapsedNs.coerceAtLeast(0L) * 90L) / 1_000_000L) and 0xFFFF_FFFFL
    if (lastTs90k < 0L) return candidate
    val delta = (candidate - lastTs90k) and 0xFFFF_FFFFL
    return if (delta in 1L until 0x8000_0000L) {
        candidate
    } else {
        (lastTs90k + 90L) and 0xFFFF_FFFFL
    }
}

/**
 * Returns true if the Annex-B H.264 access unit contains a NAL unit with [targetNalType]
 * (e.g., 5 = IDR, 7 = SPS, 8 = PPS).
 */
internal fun annexBContainsNalType(data: ByteArray, targetNalType: Int): Boolean {
    var i = 0
    val len = data.size
    while (i + 3 < len) {
        val startCodeLen = when {
            i + 4 < len && data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
            data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
            else -> {
                i++
                continue
            }
        }
        val nalIdx = i + startCodeLen
        if (nalIdx < len && (data[nalIdx].toInt() and 0x1F) == targetNalType) {
            return true
        }
        i = nalIdx + 1
    }
    return false
}

/**
 * Builds an outbound H.264 Annex-B access unit, detecting IDR frames from both MediaCodec flags
 * and NAL unit type 5, and prepending cached SPS/PPS only when not already present in [frameData].
 */
internal fun buildOutboundAccessUnit(
    frameData: ByteArray,
    isKeyFlag: Boolean,
    codecConfigAnnexB: ByteArray?
): Pair<ByteArray, Boolean> {
    val isIdr = isKeyFlag || annexBContainsNalType(frameData, 5)
    val au = if (isIdr && codecConfigAnnexB != null && codecConfigAnnexB.isNotEmpty() && !annexBContainsNalType(frameData, 7)) {
        codecConfigAnnexB + frameData
    } else {
        frameData
    }
    return au to isIdr
}

/**
 * Computes uniform display scale for video TextureView rendering.
 *
 * - When [fillCrop] is false ("Fit"), returns exact aspect-fit scale (zero cropping).
 * - When [fillCrop] is true ("Smart fill"), fills the viewport edge-to-edge whenever cropping
 *   stays within [maxCropFraction] (e.g., 9:16 portrait video on a 20:9 screen), and smoothly
 *   caps aggressive zoom when aspect ratios diverge sharply (e.g., landscape/4:3 on a tall phone).
 */
internal fun computeSmartVideoDisplayScale(
    viewWidth: Float,
    viewHeight: Float,
    rotatedVideoWidth: Float,
    rotatedVideoHeight: Float,
    fillCrop: Boolean,
    maxCropFraction: Float = 0.28f
): Float {
    if (viewWidth <= 0f || viewHeight <= 0f || rotatedVideoWidth <= 0f || rotatedVideoHeight <= 0f) {
        return 1f
    }
    val fitScale = minOf(viewWidth / rotatedVideoWidth, viewHeight / rotatedVideoHeight)
    if (!fillCrop) return fitScale
    val fillScale = maxOf(viewWidth / rotatedVideoWidth, viewHeight / rotatedVideoHeight)
    val clampedMaxCrop = maxCropFraction.coerceIn(0f, 0.99f)
    if (clampedMaxCrop >= 0.99f || fillScale <= 0f) return fillScale
    val cropFraction = 1f - (fitScale / fillScale)
    return if (cropFraction <= clampedMaxCrop) {
        fillScale
    } else {
        fitScale / (1f - clampedMaxCrop)
    }
}

/**
 * Hardware MediaCodec H.264 Annex-B video encoder (Camera2 -> GL upright portrait relay ->
 * Rust VoIP) and decoder (Rust VoIP -> remote Surface) supporting front/back switching and PLI.
 */
class NativeVoipVideoPipeline(
    private val context: Context,
    private val callId: String,
    private val onRemoteOrientationDegrees: (Int) -> Unit = {},
    private val onRemoteCodecRotationDegrees: (Int) -> Unit = {},
    private val onRemoteVideoSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onLocalVideoSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onRemoteFirstFrameDecoded: () -> Unit = {}
) {
    companion object {
        private const val TAG = "NativeVoipVideo"
        private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val CAM_WIDTH = 1280
        private const val CAM_HEIGHT = 720
        private const val ENC_WIDTH = 544
        private const val ENC_HEIGHT = 960
        private const val BITRATE = 800_000
        private const val FPS = 24
        private const val I_FRAME_INTERVAL_SEC = 2
        private const val MAX_CAMERA_OPEN_RETRIES = 3
    }

    private val running = AtomicBoolean(false)
    private val sendEnabled = AtomicBoolean(false)
    private val useFrontCamera = AtomicBoolean(true)
    private val cameraOpening = AtomicBoolean(false)
    private val orientationSent = AtomicBoolean(false)

    private val pipelineClockStartNs = System.nanoTime()
    private val lastSentTs90k = AtomicLong(-1L)

    private val localSurfaceRef = AtomicReference<Surface?>(null)
    private val remoteSurfaceRef = AtomicReference<Surface?>(null)

    private val controlExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "wupp-voip-vctrl").apply { isDaemon = true }
    }

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraClosedLatch: CountDownLatch? = null
    private var captureSession: CameraCaptureSession? = null

    private var encoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var glRelay: CameraVideoGlRelay? = null
    private var glCameraSurface: Surface? = null
    private var encoderDrainThread: Thread? = null
    private var decoderThread: Thread? = null

    @Volatile
    private var codecConfigAnnexB: ByteArray? = null
    private val cameraLock = Any()
    private val decoderSurfaceLock = Any()
    private var activeDecoder: MediaCodec? = null
    private var configuredRemoteSurface: Surface? = null

    private val cameraGeneration = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private val encoderGeneration = AtomicLong(0L)
    private var startedEncoderGen: Long = 0L

    private fun submitControl(onRejected: (() -> Unit)? = null, block: () -> Unit) {
        val submitted = runCatching {
            controlExecutor.execute {
                runCatching(block).onFailure { err ->
                    Log.w(TAG, "VoIP video control task error: ${err.message}", err)
                }
            }
        }
        if (submitted.isFailure) {
            onRejected?.invoke()
        }
    }

    private fun scheduleControl(delayMs: Long, block: () -> Unit) {
        runCatching {
            controlExecutor.schedule({
                runCatching(block).onFailure { err ->
                    Log.w(TAG, "VoIP video scheduled control task error: ${err.message}", err)
                }
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    fun start(enableSendCamera: Boolean) {
        if (!running.compareAndSet(false, true)) return
        sendEnabled.set(enableSendCamera)

        decoderThread = Thread({ runDecoderLoop() }, "wupp-voip-vdec").apply {
            isDaemon = true
            start()
        }

        if (enableSendCamera) {
            submitControl { reconcileCameraState() }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        sendEnabled.set(false)
        cameraGeneration.incrementAndGet()
        sessionGeneration.incrementAndGet()
        encoderGeneration.incrementAndGet()
        submitControl {
            stopCameraAndEncoder()
            synchronized(decoderSurfaceLock) {
                releaseActiveDecoderLocked()
            }
        }
        runCatching { controlExecutor.shutdown() }
        decoderThread = null
    }

    fun setLocalPreviewSurface(surface: Surface?, onPreviousDetached: (() -> Unit)? = null) {
        val prev = localSurfaceRef.getAndSet(surface)
        if (prev === surface && onPreviousDetached == null) return
        if (running.get() || onPreviousDetached != null) {
            submitControl(onRejected = onPreviousDetached) {
                if (prev !== surface && running.get() && sendEnabled.get()) {
                    restartCaptureSessionOnly()
                } else if (surface == null && localSurfaceRef.get() == null && !sendEnabled.get()) {
                    synchronized(cameraLock) {
                        val prevSession = captureSession
                        captureSession = null
                        if (prevSession != null) {
                            runCatching { prevSession.stopRepeating() }
                            runCatching { prevSession.close() }
                        }
                    }
                }
                onPreviousDetached?.invoke()
            }
        }
    }

    fun setRemoteSurface(surface: Surface?, onPreviousDetached: (() -> Unit)? = null) {
        val prev = remoteSurfaceRef.getAndSet(surface)
        if (surface != null && running.get()) {
            submitControl { runCatching { Whatsbridge.requestPeerKeyframe(callId) } }
        }
        if (prev !== surface || onPreviousDetached != null) {
            submitControl(onRejected = onPreviousDetached) {
                synchronized(decoderSurfaceLock) {
                    val detachPrev = prev != null && configuredRemoteSurface === prev
                    val detachCleared = surface == null && remoteSurfaceRef.get() == null
                    if (detachPrev || detachCleared) {
                        releaseActiveDecoderLocked()
                    }
                }
                onPreviousDetached?.invoke()
            }
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        sendEnabled.set(enabled)
        if (!enabled) {
            encoderGeneration.incrementAndGet()
            sessionGeneration.incrementAndGet()
        }
        if (!running.get()) return
        submitControl { reconcileCameraState() }
    }

    fun switchCamera() {
        useFrontCamera.set(!useFrontCamera.get())
        if (!running.get() || !sendEnabled.get()) return
        submitControl {
            stopCameraAndEncoder()
            if (running.get() && sendEnabled.get()) {
                startCameraAndEncoder(retryAttempt = 0)
            }
        }
    }

    fun requestSyncKeyframe() {
        synchronized(cameraLock) {
            if (!running.get() || !sendEnabled.get()) return
            val enc = encoder ?: return
            runCatching {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                enc.setParameters(params)
            }
        }
    }

    /**
     * Nudges the hardware encoder for an immediate IDR keyframe plus staggered follow-ups so
     * post-connect, camera-toggle, and camera-flip transitions always deliver SPS+PPS+IDR quickly.
     */
    fun requestSyncKeyframeBurst() {
        if (!running.get() || !sendEnabled.get()) return
        requestSyncKeyframe()
        scheduleControl(350L) {
            if (running.get() && sendEnabled.get()) requestSyncKeyframe()
        }
        scheduleControl(900L) {
            if (running.get() && sendEnabled.get()) requestSyncKeyframe()
        }
    }

    private fun reconcileCameraState() {
        val (shouldStopOnly, needsFreshStart) = synchronized(cameraLock) {
            if (!running.get() || !sendEnabled.get()) {
                true to false
            } else {
                val fresh = (cameraDevice == null && !cameraOpening.get()) ||
                    encoder == null ||
                    startedEncoderGen != encoderGeneration.get()
                false to fresh
            }
        }
        if (shouldStopOnly) {
            stopCameraAndEncoder()
            return
        }
        if (needsFreshStart) {
            stopCameraAndEncoder()
            if (running.get() && sendEnabled.get()) {
                startCameraAndEncoder(retryAttempt = 0)
            }
        }
    }

    private fun releaseActiveDecoderLocked() {
        activeDecoder?.let { dec ->
            runCatching { dec.stop() }
            runCatching { dec.release() }
        }
        activeDecoder = null
        configuredRemoteSurface = null
    }

    private fun openConfiguredAvcEncoder(): Triple<MediaCodec, Surface, VideoEncoderCandidate>? {
        val explicitCodecs = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(MIME_AVC, ignoreCase = true) }
                }
                .sortedBy { if (isSoftwareAvcCodec(it.name)) 1 else 0 }
                .map { it.name }
        }.getOrDefault(emptyList())

        val codecNamesToTry: List<String?> = buildList {
            add(null)
            explicitCodecs.forEach { add(it) }
        }
        val presets = buildAvcEncoderCandidates()

        for (codecName in codecNamesToTry) {
            for (preset in presets) {
                var enc: MediaCodec? = null
                var inputSurf: Surface? = null
                try {
                    val format = MediaFormat.createVideoFormat(MIME_AVC, preset.width, preset.height).apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, preset.bitrate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
                        when (preset.bitrateMode) {
                            AvcBitrateMode.VBR -> setInteger(
                                MediaFormat.KEY_BITRATE_MODE,
                                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                            )
                            AvcBitrateMode.CBR -> setInteger(
                                MediaFormat.KEY_BITRATE_MODE,
                                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                            )
                            AvcBitrateMode.DEFAULT -> Unit
                        }
                    }
                    enc = if (codecName == null) {
                        MediaCodec.createEncoderByType(MIME_AVC)
                    } else {
                        MediaCodec.createByCodecName(codecName)
                    }
                    enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurf = enc.createInputSurface()
                    enc.start()
                    return Triple(enc, inputSurf, preset)
                } catch (t: Throwable) {
                    Log.w(
                        TAG,
                        "AVC encoder attempt failed codec=${codecName ?: "default"} size=${preset.width}x${preset.height} mode=${preset.bitrateMode}: ${t.message}"
                    )
                    runCatching { inputSurf?.release() }
                    runCatching { enc?.release() }
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun startCameraAndEncoder(retryAttempt: Int = 0) {
        var shouldCleanupAfterLock = false
        synchronized(cameraLock) {
            if (!running.get() || !sendEnabled.get() || cameraDevice != null || cameraOpening.get()) return
            try {
                val (enc, inputSurf, encPreset) = openConfiguredAvcEncoder()
                    ?: throw IllegalStateException("No compatible H.264 encoder found")
                encoder = enc
                encoderInputSurface = inputSurf
                codecConfigAnnexB = null

                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val isFront = useFrontCamera.get()
                val targetFacing = if (isFront) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }
                val camId = cm.cameraIdList.firstOrNull { id ->
                    val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@firstOrNull false
                    val facingMatch = chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
                    val hasSurfaceOut = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(SurfaceTexture::class.java)
                        ?.isNotEmpty() == true
                    facingMatch && hasSurfaceOut
                } ?: cm.cameraIdList.firstOrNull { id ->
                    runCatching { cm.getCameraCharacteristics(id) }
                        .getOrNull()
                        ?.get(CameraCharacteristics.LENS_FACING) == targetFacing
                } ?: cm.cameraIdList.firstOrNull()
                if (camId == null) {
                    shouldCleanupAfterLock = true
                    return@synchronized
                }

                val characteristics = cm.getCameraCharacteristics(camId)
                val sensorDeg = characteristics
                    .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val availableSizes = streamMap
                    ?.getOutputSizes(SurfaceTexture::class.java)
                    ?.map { it.width to it.height }
                    .orEmpty()
                val (camW, camH) = selectBestCameraCaptureSize(availableSizes, CAM_WIDTH, CAM_HEIGHT)
                onLocalVideoSizeChanged(camH.coerceAtMost(camW), maxOf(camW, camH))

                val relay = CameraVideoGlRelay(
                    encWidth = encPreset.width,
                    encHeight = encPreset.height,
                    camWidth = camW,
                    camHeight = camH,
                    sensorDeg = sensorDeg,
                    isFrontCamera = isFront,
                    encoderInputSurface = inputSurf,
                    displayRotationProvider = { currentDisplayRotationDegrees() }
                )
                val relaySurface = relay.startAndGetCameraSurface()
                if (relaySurface == null) {
                    relay.stop()
                    shouldCleanupAfterLock = true
                    return@synchronized
                }
                glRelay = relay
                glCameraSurface = relaySurface

                // Frames are already rotated to upright portrait before H.264 encoding;
                // announce orientation 0 once instead of spamming signaling on every camera flip.
                if (orientationSent.compareAndSet(false, true)) {
                    runCatching {
                        Whatsbridge.setCallVideoOrientation(callId, 0)
                    }
                }

                val myEncGen = encoderGeneration.incrementAndGet()
                startedEncoderGen = myEncGen
                encoderDrainThread = Thread({ runEncoderDrainLoop(enc, myEncGen) }, "wupp-voip-venc").apply {
                    isDaemon = true
                    start()
                }

                val thread = HandlerThread("wupp-voip-cam").apply { start() }
                val handler = Handler(thread.looper)
                cameraThread = thread
                cameraHandler = handler
                val myCamGen = cameraGeneration.incrementAndGet()
                val myClosedLatch = CountDownLatch(1)
                cameraClosedLatch = myClosedLatch
                cameraOpening.set(true)

                cm.openCamera(camId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        synchronized(cameraLock) {
                            if (myCamGen == cameraGeneration.get()) {
                                cameraOpening.set(false)
                            }
                            if (!running.get() || !sendEnabled.get() || myCamGen != cameraGeneration.get()) {
                                runCatching { camera.close() }
                                myClosedLatch.countDown()
                                return
                            }
                            cameraDevice = camera
                            createCaptureSessionLocked(camera)
                        }
                        // Nudge an immediate keyframe once camera streaming begins (critical on flip/resume)
                        scheduleControl(250L) {
                            if (running.get() && sendEnabled.get() && myCamGen == cameraGeneration.get()) {
                                requestSyncKeyframe()
                            }
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        synchronized(cameraLock) {
                            if (myCamGen == cameraGeneration.get()) {
                                cameraOpening.set(false)
                            }
                            if (cameraDevice === camera) {
                                sessionGeneration.incrementAndGet()
                                runCatching { captureSession?.close() }
                                captureSession = null
                                cameraDevice = null
                            }
                            runCatching { camera.close() }
                        }
                        myClosedLatch.countDown()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.w(TAG, "CameraDevice error=$error retryAttempt=$retryAttempt")
                        AppLogManager.log("WARN", TAG, "CameraDevice error=$error retryAttempt=$retryAttempt")
                        val shouldRetry: Boolean
                        synchronized(cameraLock) {
                            if (myCamGen == cameraGeneration.get()) {
                                cameraOpening.set(false)
                            }
                            if (cameraDevice === camera) {
                                sessionGeneration.incrementAndGet()
                                runCatching { captureSession?.close() }
                                captureSession = null
                                cameraDevice = null
                            }
                            runCatching { camera.close() }
                            shouldRetry = running.get() &&
                                sendEnabled.get() &&
                                myCamGen == cameraGeneration.get() &&
                                retryAttempt < MAX_CAMERA_OPEN_RETRIES
                        }
                        myClosedLatch.countDown()
                        if (shouldRetry) {
                            scheduleControl(350L) {
                                if (running.get() && sendEnabled.get() && myCamGen == cameraGeneration.get()) {
                                    stopCameraAndEncoder()
                                    if (running.get() && sendEnabled.get()) {
                                        startCameraAndEncoder(retryAttempt = retryAttempt + 1)
                                    }
                                }
                            }
                        }
                    }

                    override fun onClosed(camera: CameraDevice) {
                        synchronized(cameraLock) {
                            if (myCamGen == cameraGeneration.get()) {
                                cameraOpening.set(false)
                            }
                            if (cameraDevice === camera) {
                                sessionGeneration.incrementAndGet()
                                captureSession = null
                                cameraDevice = null
                            }
                        }
                        myClosedLatch.countDown()
                    }
                }, handler)
            } catch (t: Throwable) {
                cameraOpening.set(false)
                cameraClosedLatch?.countDown()
                Log.e(TAG, "Failed starting Camera2/H264 encoder: ${t.message}", t)
                AppLogManager.log("ERROR", TAG, "Failed starting Camera2/H264 encoder: ${t.message}", t)
                shouldCleanupAfterLock = true
            }
        }
        if (shouldCleanupAfterLock) {
            stopCameraAndEncoder()
        }
    }

    private fun restartCaptureSessionOnly() {
        synchronized(cameraLock) {
            if (!running.get() || !sendEnabled.get()) return
            val cam = cameraDevice ?: return
            sessionGeneration.incrementAndGet()
            val prevSession = captureSession
            captureSession = null
            if (prevSession != null) {
                runCatching { prevSession.stopRepeating() }
                runCatching { prevSession.close() }
            }
            createCaptureSessionLocked(cam)
        }
    }

    private fun currentDisplayRotationDegrees(): Int {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val rot = dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        return when (rot) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSessionLocked(
        camera: CameraDevice,
        includeLocalSurface: Boolean = true
    ) {
        if (!running.get() || !sendEnabled.get() || cameraDevice !== camera) return
        val encSurf = (glCameraSurface ?: encoderInputSurface)?.takeIf { it.isValid } ?: return
        val handler = cameraHandler ?: return
        if (cameraThread?.isAlive != true) return
        val targets = buildList {
            add(encSurf)
            if (includeLocalSurface) {
                localSurfaceRef.get()?.takeIf { it.isValid }?.let { add(it) }
            }
        }
        val mySessionGen = sessionGeneration.incrementAndGet()
        runCatching {
            camera.createCaptureSession(
                targets,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            if (isCaptureSessionCallbackStale(
                                    running = running.get(),
                                    sendEnabled = sendEnabled.get(),
                                    sameCameraInstance = cameraDevice === camera,
                                    callbackSessionGen = mySessionGen,
                                    activeSessionGen = sessionGeneration.get()
                                )
                            ) {
                                runCatching { session.close() }
                                return
                            }
                            val activeTargets = targets.filter { it.isValid }
                            val activeHandler = cameraHandler
                            if (activeTargets.isEmpty() || activeHandler == null || cameraThread?.isAlive != true) {
                                runCatching { session.close() }
                                return
                            }
                            val reqResult = runCatching {
                                val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    activeTargets.forEach { addTarget(it) }
                                    set(
                                        CaptureRequest.CONTROL_MODE,
                                        CaptureRequest.CONTROL_MODE_AUTO
                                    )
                                }
                                session.setRepeatingRequest(req.build(), null, activeHandler)
                            }
                            reqResult.onSuccess {
                                captureSession = session
                            }.onFailure { err ->
                                Log.w(
                                    TAG,
                                    "setRepeatingRequest failed (targets=${activeTargets.size}): ${err.message}"
                                )
                                AppLogManager.log(
                                    "WARN",
                                    TAG,
                                    "setRepeatingRequest failed (targets=${activeTargets.size}): ${err.message}",
                                    err
                                )
                                runCatching { session.close() }
                                if (captureSession === session) {
                                    captureSession = null
                                }
                                if (includeLocalSurface && activeTargets.size > 1 &&
                                    !isCaptureSessionCallbackStale(
                                        running = running.get(),
                                        sendEnabled = sendEnabled.get(),
                                        sameCameraInstance = cameraDevice === camera,
                                        callbackSessionGen = mySessionGen,
                                        activeSessionGen = sessionGeneration.get()
                                    )
                                ) {
                                    createCaptureSessionLocked(camera, includeLocalSurface = false)
                                }
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            runCatching { session.close() }
                            if (captureSession === session) {
                                captureSession = null
                            }
                            if (isCaptureSessionCallbackStale(
                                    running = running.get(),
                                    sendEnabled = sendEnabled.get(),
                                    sameCameraInstance = cameraDevice === camera,
                                    callbackSessionGen = mySessionGen,
                                    activeSessionGen = sessionGeneration.get()
                                )
                            ) {
                                return
                            }
                            Log.w(TAG, "Camera capture session configure failed (targets=${targets.size})")
                            AppLogManager.log(
                                "WARN",
                                TAG,
                                "Camera capture session configure failed (targets=${targets.size})"
                            )
                            if (includeLocalSurface && targets.size > 1) {
                                createCaptureSessionLocked(camera, includeLocalSurface = false)
                            }
                        }
                    }

                    override fun onClosed(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            if (captureSession === session) {
                                captureSession = null
                            }
                        }
                    }
                },
                handler
            )
        }.onFailure { err ->
            Log.w(TAG, "createCaptureSession failed (targets=${targets.size}): ${err.message}")
            AppLogManager.log(
                "WARN",
                TAG,
                "createCaptureSession failed (targets=${targets.size}): ${err.message}",
                err
            )
            if (includeLocalSurface && targets.size > 1 &&
                !isCaptureSessionCallbackStale(
                    running = running.get(),
                    sendEnabled = sendEnabled.get(),
                    sameCameraInstance = cameraDevice === camera,
                    callbackSessionGen = mySessionGen,
                    activeSessionGen = sessionGeneration.get()
                )
            ) {
                createCaptureSessionLocked(camera, includeLocalSurface = false)
            }
        }
    }

    private fun stopCameraAndEncoder() {
        val prevSession: CameraCaptureSession?
        val prevCam: CameraDevice?
        val prevClosedLatch: CountDownLatch?
        val prevThread: HandlerThread?
        val prevRelay: CameraVideoGlRelay?
        val prevEnc: MediaCodec?
        val prevDrain: Thread?
        val prevInputSurf: Surface?

        synchronized(cameraLock) {
            cameraGeneration.incrementAndGet()
            sessionGeneration.incrementAndGet()
            encoderGeneration.incrementAndGet()
            startedEncoderGen = 0L
            cameraOpening.set(false)

            prevSession = captureSession
            captureSession = null
            prevCam = cameraDevice
            cameraDevice = null
            prevClosedLatch = cameraClosedLatch
            cameraClosedLatch = null
            prevThread = cameraThread
            cameraThread = null
            cameraHandler = null
            prevRelay = glRelay
            glRelay = null
            glCameraSurface = null
            prevEnc = encoder
            encoder = null
            prevDrain = encoderDrainThread
            encoderDrainThread = null
            prevInputSurf = encoderInputSurface
            encoderInputSurface = null
            codecConfigAnnexB = null
        }

        // Perform blocking HAL/GL/MediaCodec teardown OUTSIDE cameraLock so CameraDevice.onClosed
        // can complete cleanly without deadlocking and release both the camera hardware and Surface
        // producer slot before switchCamera() opens the other lens.
        if (prevSession != null) {
            runCatching { prevSession.stopRepeating() }
            runCatching { prevSession.close() }
        }
        if (prevCam != null) {
            runCatching { prevCam.close() }
            if (prevClosedLatch != null) {
                runCatching { prevClosedLatch.await(1000, TimeUnit.MILLISECONDS) }
            }
        }
        runCatching { prevThread?.quitSafely() }
        runCatching { prevRelay?.stop() }

        // Signal EOS and let the drain loop exit before stopping/releasing MediaCodec so
        // getOutputBuffer() can never race against a torn-down native codec instance.
        runCatching { prevEnc?.signalEndOfInputStream() }
        if (prevDrain != null && prevDrain !== Thread.currentThread()) {
            runCatching { prevDrain.join(350) }
        }
        runCatching { prevEnc?.stop() }
        runCatching { prevEnc?.release() }
        runCatching { prevInputSurf?.release() }
    }

    private fun runEncoderDrainLoop(enc: MediaCodec, myEncGen: Long) {
        val info = MediaCodec.BufferInfo()
        var sentAcceptedIdr = false
        var lastPushAccepted = false
        var lastKeyframeNudgeMs = 0L
        while (shouldContinueEncoderDrain(running.get(), sendEnabled.get(), myEncGen, encoderGeneration.get())) {
            val outIdx = runCatching { enc.dequeueOutputBuffer(info, 25_000) }.getOrElse { break }
            if (outIdx < 0) continue
            if (!shouldContinueEncoderDrain(running.get(), sendEnabled.get(), myEncGen, encoderGeneration.get())) {
                runCatching { enc.releaseOutputBuffer(outIdx, false) }
                break
            }
            var fatalCodecError = false
            try {
                val buf = enc.getOutputBuffer(outIdx)
                if (buf != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.get(data)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        codecConfigAnnexB = data
                    } else if (shouldContinueEncoderDrain(running.get(), sendEnabled.get(), myEncGen, encoderGeneration.get())) {
                        val isKeyFlag = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        val (au, isIdr) = buildOutboundAccessUnit(
                            frameData = data,
                            isKeyFlag = isKeyFlag,
                            codecConfigAnnexB = codecConfigAnnexB
                        )
                        val elapsedNs = (System.nanoTime() - pipelineClockStartNs).coerceAtLeast(0L)
                        val ts90k = computeMonotonicRtpTimestamp90k(lastSentTs90k.get(), elapsedNs)
                        lastSentTs90k.set(ts90k)

                        val pushResult = runCatching { Whatsbridge.pushVoipVideoAu(callId, au, ts90k) }
                            .getOrDefault(-1)
                        val accepted = pushResult > 0
                        val nowMs = System.currentTimeMillis()
                        if (accepted && isIdr) {
                            sentAcceptedIdr = true
                        } else if (!sentAcceptedIdr || (accepted && !lastPushAccepted && !isIdr)) {
                            // If the first IDR was dropped while setCallVideoEnabled(true) was still
                            // attaching channels (or push just transitioned from dropped -> accepted),
                            // nudge MediaCodec for an immediate sync IDR so the peer renders right away.
                            if (nowMs - lastKeyframeNudgeMs >= 300L) {
                                lastKeyframeNudgeMs = nowMs
                                requestSyncKeyframe()
                            }
                        }
                        lastPushAccepted = accepted
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Encoder drain loop exiting cleanly on codec teardown: ${t.message}")
                fatalCodecError = true
            } finally {
                runCatching { enc.releaseOutputBuffer(outIdx, false) }
            }
            if (fatalCodecError) break
        }
    }

    private fun openConfiguredAvcDecoder(
        format: MediaFormat,
        targetSurface: Surface
    ): MediaCodec? {
        val explicitDecoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(MIME_AVC, ignoreCase = true) }
                }
                .sortedBy { if (isSoftwareAvcCodec(it.name)) 1 else 0 }
                .map { it.name }
        }.getOrDefault(emptyList())

        val codecNamesToTry: List<String?> = buildList {
            add(null)
            explicitDecoders.forEach { add(it) }
        }

        for (codecName in codecNamesToTry) {
            var dec: MediaCodec? = null
            try {
                dec = if (codecName == null) {
                    MediaCodec.createDecoderByType(MIME_AVC)
                } else {
                    MediaCodec.createByCodecName(codecName)
                }
                dec.configure(format, targetSurface, null, 0)
                dec.start()
                return dec
            } catch (t: Throwable) {
                Log.w(TAG, "AVC decoder attempt failed codec=${codecName ?: "default"}: ${t.message}")
                runCatching { dec?.release() }
            }
        }
        return null
    }

    private fun runDecoderLoop() {
        var cachedSpsPps: ByteArray? = null
        var lastOrientation = -1
        var lastKeyframeOrientation = -1
        var firstFrameReported = false
        var awaitingDecoderKeyframe = true
        var lastPeerKeyframeReqMs = 0L
        val info = MediaCodec.BufferInfo()

        try {
            while (running.get()) {
                val pullStartNs = System.nanoTime()
                val packet = runCatching { Whatsbridge.pullVoipVideoFrame(callId) }.getOrNull()
                if (packet == null) {
                    // Guard against tight busy-spin when the Rust video playout channel is absent
                    // (e.g., audio call upgrading to video or call terminating).
                    if (System.nanoTime() - pullStartNs < 5_000_000L) {
                        runCatching { Thread.sleep(20) }
                    }
                    continue
                }
                if (packet.size <= 8) continue

                val flags = packet[0].toInt() and 0xFF
                val isKeyHeader = (flags and 1) != 0
                val rawOrientation = packet[1].toInt() and 0x03
                val payload = packet.copyOfRange(8, packet.size)
                val isKey = isKeyHeader || annexBContainsNalType(payload, 5)
                if (isKey) {
                    lastKeyframeOrientation = rawOrientation
                }
                // Keep keyframe orientation across delta packets whose RTP extension omitted rotation,
                // preventing the image from jumping between upright and sideways over time.
                val effectiveOri = if (!isKey && rawOrientation == 0 && lastKeyframeOrientation > 0) {
                    lastKeyframeOrientation
                } else {
                    rawOrientation
                }
                if (effectiveOri != lastOrientation) {
                    lastOrientation = effectiveOri
                    onRemoteOrientationDegrees(whatsappOrientationToClockwiseDegrees(effectiveOri))
                }

                val ts90k = ByteBuffer.wrap(packet, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int.toLong() and 0xFFFF_FFFFL

                extractParameterSetsAnnexB(payload)?.let { cachedSpsPps = it }

                var requestPeerKeyframe = false
                synchronized(decoderSurfaceLock) {
                    val targetSurface = remoteSurfaceRef.get()
                    if (targetSurface == null || !targetSurface.isValid) {
                        if (activeDecoder != null) {
                            releaseActiveDecoderLocked()
                            awaitingDecoderKeyframe = true
                        }
                        return@synchronized
                    }

                    if (activeDecoder == null || configuredRemoteSurface !== targetSurface) {
                        releaseActiveDecoderLocked()
                        awaitingDecoderKeyframe = true
                        if (!isKey) {
                            requestPeerKeyframe = true
                            return@synchronized
                        }
                        val initCfg = cachedSpsPps
                        val fmt = MediaFormat.createVideoFormat(MIME_AVC, CAM_WIDTH, CAM_HEIGHT)
                        if (initCfg != null) {
                            fmt.setByteBuffer("csd-0", ByteBuffer.wrap(initCfg))
                        }
                        val opened = openConfiguredAvcDecoder(fmt, targetSurface)
                        if (opened != null) {
                            activeDecoder = opened
                            configuredRemoteSurface = targetSurface
                            awaitingDecoderKeyframe = false
                        } else {
                            return@synchronized
                        }
                    }

                    if (awaitingDecoderKeyframe) {
                        if (!isKey) {
                            requestPeerKeyframe = true
                            return@synchronized
                        }
                        awaitingDecoderKeyframe = false
                    }

                    val dec = activeDecoder ?: return@synchronized
                    try {
                        val inIdx = dec.dequeueInputBuffer(5_000)
                        if (inIdx >= 0) {
                            val inBuf = dec.getInputBuffer(inIdx)
                            inBuf?.clear()
                            val ptsUs = (ts90k * 1_000L) / 90L
                            if (inBuf != null && payload.size <= inBuf.remaining()) {
                                inBuf.put(payload)
                                val decFlags = if (isKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                                dec.queueInputBuffer(inIdx, 0, payload.size, ptsUs, decFlags)
                            } else {
                                // Return the slot without feeding a truncated frame to the decoder.
                                dec.queueInputBuffer(inIdx, 0, 0, ptsUs, 0)
                            }
                        }

                        while (true) {
                            val outIdx = dec.dequeueOutputBuffer(info, 0)
                            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                runCatching {
                                    val outFmt = dec.outputFormat
                                    val w = if (outFmt.containsKey("crop-right") && outFmt.containsKey("crop-left")) {
                                        outFmt.getInteger("crop-right") - outFmt.getInteger("crop-left") + 1
                                    } else if (outFmt.containsKey(MediaFormat.KEY_WIDTH)) {
                                        outFmt.getInteger(MediaFormat.KEY_WIDTH)
                                    } else {
                                        0
                                    }
                                    val h = if (outFmt.containsKey("crop-bottom") && outFmt.containsKey("crop-top")) {
                                        outFmt.getInteger("crop-bottom") - outFmt.getInteger("crop-top") + 1
                                    } else if (outFmt.containsKey(MediaFormat.KEY_HEIGHT)) {
                                        outFmt.getInteger(MediaFormat.KEY_HEIGHT)
                                    } else {
                                        0
                                    }
                                    // MediaCodec rendering directly to a SurfaceTexture/TextureView
                                    // already applies MediaFormat.KEY_ROTATION automatically on API 21+;
                                    // avoid double-rotating on top of TextureView's implicit transform.
                                    onRemoteCodecRotationDegrees(0)
                                    if (w > 0 && h > 0) {
                                        onRemoteVideoSizeChanged(w, h)
                                    }
                                }
                            } else if (outIdx >= 0) {
                                dec.releaseOutputBuffer(outIdx, true)
                                if (!firstFrameReported) {
                                    firstFrameReported = true
                                    onRemoteFirstFrameDecoded()
                                }
                            } else {
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Recoverable decoder frame failure, resetting decoder: ${t.message}")
                        releaseActiveDecoderLocked()
                        awaitingDecoderKeyframe = true
                        requestPeerKeyframe = true
                    }
                }
                if (requestPeerKeyframe) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastPeerKeyframeReqMs >= 500L) {
                        lastPeerKeyframeReqMs = nowMs
                        runCatching { Whatsbridge.requestPeerKeyframe(callId) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Video decoder loop error: ${t.message}", t)
            AppLogManager.log("ERROR", TAG, "Video decoder loop error: ${t.message}", t)
        } finally {
            synchronized(decoderSurfaceLock) {
                releaseActiveDecoderLocked()
            }
        }
    }

    /**
     * Scans Annex-B byte stream and extracts concatenated SPS (type 7) + PPS (type 8) NAL units
     * with 4-byte start codes whenever present in an IDR access unit.
     */
    private fun extractParameterSetsAnnexB(data: ByteArray): ByteArray? {
        val nals = ArrayList<ByteArray>(2)
        var i = 0
        val len = data.size
        while (i + 4 < len) {
            val startCodeLen = when {
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
                else -> {
                    i++
                    continue
                }
            }
            val nalStart = i + startCodeLen
            var next = nalStart
            while (next + 3 < len) {
                if (data[next] == 0.toByte() && data[next + 1] == 0.toByte() &&
                    (data[next + 2] == 1.toByte() || (next + 4 < len && data[next + 2] == 0.toByte() && data[next + 3] == 1.toByte()))
                ) {
                    break
                }
                next++
            }
            if (next + 3 >= len) next = len
            if (nalStart < next) {
                val nalType = data[nalStart].toInt() and 0x1F
                if (nalType == 7 || nalType == 8) {
                    nals.add(byteArrayOf(0, 0, 0, 1) + data.copyOfRange(nalStart, next))
                }
            }
            i = next
        }
        if (nals.isEmpty()) return null
        val total = nals.sumOf { it.size }
        val merged = ByteArray(total)
        var pos = 0
        for (nal in nals) {
            nal.copyInto(merged, pos)
            pos += nal.size
        }
        return merged
    }
}
