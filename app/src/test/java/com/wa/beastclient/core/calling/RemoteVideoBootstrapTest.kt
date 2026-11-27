package com.wa.beastclient.core.calling

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteVideoBootstrapTest {

    private val spsPps = byteArrayOf(
        0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x1E,
        0x00, 0x00, 0x00, 0x01, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte()
    )
    private val idrFrame = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x88.toByte(), 0x84.toByte())
    private val deltaFrame = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x41, 0x9A.toByte(), 0x22)

    @Test
    fun deltaFrameBootstrapsFromCachedIdrAndSpsPpsWhenSurfaceAttachesLate() {
        val plan = resolveDecoderBootstrap(
            isCurrentKey = false,
            currentPayload = deltaFrame,
            currentTs90k = 9_000L,
            cachedSpsPps = spsPps,
            cachedIdrFrame = idrFrame,
            cachedIdrTs90k = 6_000L
        )
        assertNotNull(plan)
        assertArrayEquals(spsPps, plan!!.csd0)
        assertArrayEquals(idrFrame, plan.bootstrapIdrFrame)
        assertEquals(6_000L, plan.bootstrapIdrTs90k)
        assertTrue(plan.feedCurrentFrame)
    }

    @Test
    fun keyframeWithCachedSpsPpsOpensDirectlyWithoutDuplicateBootstrap() {
        val plan = resolveDecoderBootstrap(
            isCurrentKey = true,
            currentPayload = idrFrame,
            currentTs90k = 12_000L,
            cachedSpsPps = spsPps,
            cachedIdrFrame = idrFrame,
            cachedIdrTs90k = 12_000L
        )
        assertNotNull(plan)
        assertNull(plan!!.bootstrapIdrFrame)
        assertTrue(plan.feedCurrentFrame)
    }

    @Test
    fun deltaFrameWithoutCachedIdrWaitsForPeerKeyframe() {
        val plan = resolveDecoderBootstrap(
            isCurrentKey = false,
            currentPayload = deltaFrame,
            currentTs90k = 9_000L,
            cachedSpsPps = spsPps,
            cachedIdrFrame = null,
            cachedIdrTs90k = 0L
        )
        assertNull(plan)
    }

    @Test
    fun keyframeWithoutSpsPpsWaitsForParameterSets() {
        val plan = resolveDecoderBootstrap(
            isCurrentKey = true,
            currentPayload = idrFrame,
            currentTs90k = 9_000L,
            cachedSpsPps = null,
            cachedIdrFrame = idrFrame,
            cachedIdrTs90k = 9_000L
        )
        assertNull(plan)
    }

    @Test
    fun keyframeWithInlineSpsPpsOpensEvenIfCacheWasNull() {
        val inlineIdr = spsPps + idrFrame
        val plan = resolveDecoderBootstrap(
            isCurrentKey = true,
            currentPayload = inlineIdr,
            currentTs90k = 9_000L,
            cachedSpsPps = null,
            cachedIdrFrame = null,
            cachedIdrTs90k = 0L
        )
        assertNotNull(plan)
        assertFalse(plan!!.bootstrapIdrFrame != null)
        assertTrue(plan.feedCurrentFrame)
    }
}
