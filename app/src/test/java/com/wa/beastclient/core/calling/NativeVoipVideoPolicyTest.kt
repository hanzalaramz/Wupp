package com.wa.beastclient.core.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoipVideoPolicyTest {
    @Test
    fun buildAvcEncoderCandidatesStartsWithPortraitFullScreenAndFallsBackCleanly() {
        val candidates = buildAvcEncoderCandidates()
        assertEquals(5, candidates.size)
        assertEquals(
            VideoEncoderCandidate(544, 960, AvcBitrateMode.VBR, 1_000_000),
            candidates[0]
        )
        assertEquals(
            VideoEncoderCandidate(544, 960, AvcBitrateMode.CBR, 1_000_000),
            candidates[1]
        )
        assertEquals(
            VideoEncoderCandidate(480, 848, AvcBitrateMode.DEFAULT, 850_000),
            candidates[2]
        )
        assertEquals(
            VideoEncoderCandidate(480, 640, AvcBitrateMode.DEFAULT, 800_000),
            candidates[3]
        )
        assertEquals(
            VideoEncoderCandidate(368, 480, AvcBitrateMode.DEFAULT, 500_000),
            candidates[4]
        )
    }

    @Test
    fun relayCenterCropScalesNeverShrinkBelowViewportBounds() {
        val (sxWide, syWide) = computeRelayCenterCropScales(srcAspect = 0.75f, encAspect = 0.5625f)
        assertTrue(sxWide > 1f)
        assertEquals(1f, syWide, 0.0001f)

        val (sxTall, syTall) = computeRelayCenterCropScales(srcAspect = 0.50f, encAspect = 0.75f)
        assertEquals(1f, sxTall, 0.0001f)
        assertTrue(syTall > 1f)

        val (sxMatch, syMatch) = computeRelayCenterCropScales(srcAspect = 0.5625f, encAspect = 0.5666f)
        assertEquals(1f, sxMatch, 0.0001f)
        assertEquals(1f, syMatch, 0.0001f)
    }

    @Test
    fun selectBestCameraCaptureSizePrefersExactTargetWhenSupported() {
        val chosen = selectBestCameraCaptureSize(
            listOf(1280 to 720, 640 to 480, 320 to 240)
        )
        assertEquals(640 to 480, chosen)
    }

    @Test
    fun selectBestCameraCaptureSizeFallsBackCleanlyWhen640x480Unavailable() {
        val chosen = selectBestCameraCaptureSize(
            listOf(320 to 240, 1280 to 960, 720 to 540, 1920 to 1080)
        )
        assertEquals(720 to 540, chosen)
    }

    @Test
    fun selectBestCameraCaptureSizeReturnsDefaultWhenEmpty() {
        assertEquals(640 to 480, selectBestCameraCaptureSize(emptyList()))
    }

    @Test
    fun isSoftwareAvcCodecIdentifiesGoogleAndAospSoftwareCodecsOnly() {
        assertTrue(isSoftwareAvcCodec("OMX.google.h264.encoder"))
        assertTrue(isSoftwareAvcCodec("c2.android.avc.encoder"))
        assertTrue(isSoftwareAvcCodec("c2.mtk.sw.avc.encoder"))
        assertFalse(isSoftwareAvcCodec("c2.mtk.avc.encoder"))
        assertFalse(isSoftwareAvcCodec("OMX.qcom.video.encoder.avc"))
    }

    @Test
    fun isCaptureSessionCallbackStaleDetectsStaleAndInactiveCallbacks() {
        assertFalse(
            isCaptureSessionCallbackStale(
                running = true,
                sendEnabled = true,
                sameCameraInstance = true,
                callbackSessionGen = 3L,
                activeSessionGen = 3L
            )
        )
        assertTrue(
            isCaptureSessionCallbackStale(
                running = false,
                sendEnabled = true,
                sameCameraInstance = true,
                callbackSessionGen = 3L,
                activeSessionGen = 3L
            )
        )
        assertTrue(
            isCaptureSessionCallbackStale(
                running = true,
                sendEnabled = false,
                sameCameraInstance = true,
                callbackSessionGen = 3L,
                activeSessionGen = 3L
            )
        )
        assertTrue(
            isCaptureSessionCallbackStale(
                running = true,
                sendEnabled = true,
                sameCameraInstance = false,
                callbackSessionGen = 3L,
                activeSessionGen = 3L
            )
        )
        assertTrue(
            isCaptureSessionCallbackStale(
                running = true,
                sendEnabled = true,
                sameCameraInstance = true,
                callbackSessionGen = 2L,
                activeSessionGen = 3L
            )
        )
    }
}
