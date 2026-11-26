package com.wa.beastclient.core.service

import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.WaDao
import org.junit.Assert.assertEquals
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

    @Test
    fun viewOnceDownloadAllowedWhenUnopenedAndBlockedOnceOpened() {
        val base = MessageEntity(
            id = "vo-1",
            chatJid = "123@s.whatsapp.net",
            senderJid = "123@s.whatsapp.net",
            timestamp = 1000L,
            msgType = "IMAGE",
            mimeType = "image/jpeg",
            isViewOnce = true,
            viewOnceState = "AVAILABLE"
        )
        assertFalse(WaRealtimeService.isViewOnceBlockedDownload(base, userInitiated = false))
        assertFalse(WaRealtimeService.isViewOnceBlockedDownload(base.copy(viewOnceState = "PENDING"), userInitiated = false))
        // Unavailable stub skips silent auto-download loops, but allows explicit user tap download.
        val stub = base.copy(msgType = "VIEW_ONCE_STUB", mimeType = "application/x-view-once-stub")
        assertTrue(WaRealtimeService.isViewOnceBlockedDownload(stub, userInitiated = false))
        assertFalse(WaRealtimeService.isViewOnceBlockedDownload(stub, userInitiated = true))
        // Once truly opened, both auto and manual download stay blocked.
        assertTrue(WaRealtimeService.isViewOnceBlockedDownload(base.copy(viewOnceState = "STRIPPED"), userInitiated = true))
        assertTrue(WaRealtimeService.isViewOnceBlockedDownload(base.copy(msgType = "VIEW_ONCE_STRIPPED", viewOnceState = "STRIPPED"), userInitiated = true))
    }

    @Test
    fun viewOncePreviewShowsOpenedEvenWhenSavedFileRetained() {
        val available = MessageEntity(
            id = "vo-2",
            chatJid = "123@s.whatsapp.net",
            senderJid = "123@s.whatsapp.net",
            timestamp = 1000L,
            msgType = "IMAGE",
            localMediaPath = "/tmp/vo-2.jpg",
            isViewOnce = true,
            viewOnceState = "AVAILABLE"
        )
        assertEquals("Photo", WaDao.previewTextFor(available))
        assertEquals("Opened · Photo", WaDao.previewTextFor(available.copy(viewOnceState = "STRIPPED")))
    }
}
