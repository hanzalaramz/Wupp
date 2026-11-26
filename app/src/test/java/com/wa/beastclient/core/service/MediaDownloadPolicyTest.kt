package com.wa.beastclient.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadPolicyTest {

    @Test
    fun autoDownloadSkipsWhenAlreadyInFlightAndManualTapRetriesAfterStall() {
        assertTrue(shouldAllowMediaDownloadRequest(isAlreadyDownloading = false, userInitiated = false, elapsedSinceStartMs = 0L))
        assertFalse(shouldAllowMediaDownloadRequest(isAlreadyDownloading = true, userInitiated = false, elapsedSinceStartMs = 10_000L))
        assertFalse(shouldAllowMediaDownloadRequest(isAlreadyDownloading = true, userInitiated = true, elapsedSinceStartMs = 1_500L))
        assertTrue(shouldAllowMediaDownloadRequest(isAlreadyDownloading = true, userInitiated = true, elapsedSinceStartMs = 4_000L))
    }
}
