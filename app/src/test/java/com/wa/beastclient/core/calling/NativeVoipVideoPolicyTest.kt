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

    @Test
    fun shouldContinueEncoderDrainStopsImmediatelyOnStaleGenerationOrCameraOff() {
        assertTrue(
            shouldContinueEncoderDrain(
                running = true,
                sendEnabled = true,
                callbackEncGen = 4L,
                activeEncGen = 4L
            )
        )
        assertFalse(
            shouldContinueEncoderDrain(
                running = true,
                sendEnabled = true,
                callbackEncGen = 3L,
                activeEncGen = 4L
            )
        )
        assertFalse(
            shouldContinueEncoderDrain(
                running = true,
                sendEnabled = false,
                callbackEncGen = 4L,
                activeEncGen = 4L
            )
        )
        assertFalse(
            shouldContinueEncoderDrain(
                running = false,
                sendEnabled = true,
                callbackEncGen = 4L,
                activeEncGen = 4L
            )
        )
    }

    @Test
    fun computeSmartVideoDisplayScaleFillsMatchedPortraitAndCapsExtremeCrop() {
        // 9:16 portrait video (544x960) on a 20:9 phone (1080x2400): ~20.6% crop -> full edge-to-edge fill (2.5x)
        val matchedFill = computeSmartVideoDisplayScale(
            viewWidth = 1080f,
            viewHeight = 2400f,
            rotatedVideoWidth = 544f,
            rotatedVideoHeight = 960f,
            fillCrop = true,
            maxCropFraction = 0.28f
        )
        assertEquals(2.5f, matchedFill, 0.001f)

        // Fit mode always preserves 100% of frame without cropping
        val exactFit = computeSmartVideoDisplayScale(
            viewWidth = 1080f,
            viewHeight = 2400f,
            rotatedVideoWidth = 640f,
            rotatedVideoHeight = 480f,
            fillCrop = false
        )
        assertEquals(1080f / 640f, exactFit, 0.001f)

        // Landscape 4:3 video (640x480) on tall 20:9 portrait (1080x2400): smart fill caps extreme zoom
        val cappedLandscape = computeSmartVideoDisplayScale(
            viewWidth = 1080f,
            viewHeight = 2400f,
            rotatedVideoWidth = 640f,
            rotatedVideoHeight = 480f,
            fillCrop = true,
            maxCropFraction = 0.28f
        )
        val fullCropScale = 2400f / 480f
        assertTrue(cappedLandscape > exactFit)
        assertTrue(cappedLandscape < fullCropScale)
        assertEquals(exactFit / (1f - 0.28f), cappedLandscape, 0.001f)
    }

    @Test
    fun computeMonotonicRtpTimestamp90kNeverMovesBackwardsAcrossRestarts() {
        val t0 = computeMonotonicRtpTimestamp90k(lastTs90k = -1L, elapsedNs = 0L)
        assertEquals(0L, t0)
        val t1 = computeMonotonicRtpTimestamp90k(lastTs90k = t0, elapsedNs = 10_000_000_000L)
        assertEquals(900_000L, t1)
        // Simulated encoder restart resetting elapsed to 0 must still advance forward
        val t2 = computeMonotonicRtpTimestamp90k(lastTs90k = t1, elapsedNs = 0L)
        assertEquals(900_090L, t2)
        // Normal progression resumes seamlessly once elapsed moves ahead
        val t3 = computeMonotonicRtpTimestamp90k(lastTs90k = t2, elapsedNs = 11_000_000_000L)
        assertEquals(990_000L, t3)
    }

    @Test
    fun buildOutboundAccessUnitDetectsIdrByNalTypeAndPrependsConfigWithoutDuplicatingSps() {
        val spsPps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0, 0, 0, 1, 0x68, 0x1E)
        val idrOnly = byteArrayOf(0, 0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte())
        val deltaFrame = byteArrayOf(0, 0, 0, 1, 0x41, 0x9A.toByte())

        // Detects IDR from NAL type 5 even if MediaCodec flag was false, and prepends SPS/PPS
        val (au1, isIdr1) = buildOutboundAccessUnit(idrOnly, isKeyFlag = false, codecConfigAnnexB = spsPps)
        assertTrue(isIdr1)
        assertTrue(annexBContainsNalType(au1, 7))
        assertTrue(annexBContainsNalType(au1, 5))
        assertEquals(spsPps.size + idrOnly.size, au1.size)

        // Does not duplicate SPS/PPS when encoder already included SPS inside the frame
        val alreadyPrefixed = spsPps + idrOnly
        val (au2, isIdr2) = buildOutboundAccessUnit(alreadyPrefixed, isKeyFlag = true, codecConfigAnnexB = spsPps)
        assertTrue(isIdr2)
        assertEquals(alreadyPrefixed.size, au2.size)

        // Delta frame stays untouched
        val (au3, isIdr3) = buildOutboundAccessUnit(deltaFrame, isKeyFlag = false, codecConfigAnnexB = spsPps)
        assertFalse(isIdr3)
        assertEquals(deltaFrame.size, au3.size)
    }
}
