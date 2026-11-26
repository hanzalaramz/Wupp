package com.wa.beastclient.ui

import com.wa.beastclient.core.service.isViewOnceSupportedForMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecordingPolicyTest {

    @Test
    fun android10PlusPrioritizesMonoOggOpusThenFallsBackCleanlyToAac() {
        val profiles = buildVoiceRecorderProfiles(sdkInt = 34)
        assertEquals(3, profiles.size)
        assertTrue(profiles[0].isOggOpus)
        assertEquals(48_000, profiles[0].sampleRateHz)
        assertEquals("ogg", profiles[0].ext)

        assertTrue(profiles[1].isOggOpus)
        assertEquals(16_000, profiles[1].sampleRateHz)

        assertFalse(profiles[2].isOggOpus)
        assertEquals("m4a", profiles[2].ext)
    }

    @Test
    fun android8And9UseAacM4aProfile() {
        val profiles = buildVoiceRecorderProfiles(sdkInt = 28)
        assertEquals(1, profiles.size)
        assertFalse(profiles[0].isOggOpus)
        assertEquals("m4a", profiles[0].ext)
    }

    @Test
    fun outboundAudioDispatchTagsOggAsPttAndM4aAsStandardAudio() {
        val oggDispatch = resolveOutboundAudioDispatch("ogg")
        assertEquals("audio/ogg; codecs=opus", oggDispatch.mimeType)
        assertTrue(oggDispatch.isVoiceNote)

        val m4aDispatch = resolveOutboundAudioDispatch("m4a")
        assertEquals("audio/mp4", m4aDispatch.mimeType)
        assertFalse(m4aDispatch.isVoiceNote)
    }

    @Test
    fun headerValidationVerifiesOggAndM4aContainers() {
        assertTrue(hasValidAudioContainerHeader("ogg", "OggS\u0000\u0002".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(hasValidAudioContainerHeader("ogg", "RIFF\u0000\u0000".toByteArray(Charsets.ISO_8859_1)))
        assertTrue(hasValidAudioContainerHeader("m4a", byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())))
        assertFalse(hasValidAudioContainerHeader("m4a", byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun recordedElapsedTimeAccumulatesAcrossPauseAndResume() {
        val firstSegmentMs = computeRecordedElapsedMs(
            isPaused = false,
            accumulatedMs = 0L,
            segmentStartMs = 1_000L,
            nowMs = 3_400L
        )
        assertEquals(2_400L, firstSegmentMs)

        val whilePausedMs = computeRecordedElapsedMs(
            isPaused = true,
            accumulatedMs = firstSegmentMs,
            segmentStartMs = 1_000L,
            nowMs = 15_000L
        )
        assertEquals(2_400L, whilePausedMs)

        val afterResumeMs = computeRecordedElapsedMs(
            isPaused = false,
            accumulatedMs = whilePausedMs,
            segmentStartMs = 16_000L,
            nowMs = 18_600L
        )
        assertEquals(5_000L, afterResumeMs)
    }

    @Test
    fun voicePauseSupportedOnOpusOrAndroid7Plus() {
        assertTrue(resolveVoicePauseSupported(sdkInt = 29, isOpusRecorder = true))
        assertTrue(resolveVoicePauseSupported(sdkInt = 24, isOpusRecorder = false))
        assertFalse(resolveVoicePauseSupported(sdkInt = 23, isOpusRecorder = false))
    }

    @Test
    fun viewOnceSupportsAudioAndVoiceNotesAlongsideImageAndVideo() {
        assertTrue(isViewOnceSupportedForMediaType("PTT", isGif = false))
        assertTrue(isViewOnceSupportedForMediaType("AUDIO", isGif = false))
        assertTrue(isViewOnceSupportedForMediaType("IMAGE", isGif = false))
        assertTrue(isViewOnceSupportedForMediaType("VIDEO", isGif = false))
        assertFalse(isViewOnceSupportedForMediaType("VIDEO", isGif = true))
        assertFalse(isViewOnceSupportedForMediaType("DOCUMENT", isGif = false))
    }
}
