package com.wa.beastclient.ui.components

import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.media.EagerMediaVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentDownloadAndApkTest {

    private fun docMsg(
        body: String,
        mimeType: String = "application/octet-stream",
        fileSize: Long = 24_000_000L
    ) = MessageEntity(
        id = "doc1",
        chatJid = "12345@s.whatsapp.net",
        senderJid = "12345@s.whatsapp.net",
        senderName = "Sender",
        isFromMe = false,
        timestamp = 1000L,
        body = body,
        msgType = "DOCUMENT",
        mimeType = mimeType,
        fileSize = fileSize
    )

    @Test
    fun captionedApkPreservesFilenameExtensionAndExportName() {
        val msg = docMsg(
            body = "Wupp-release.apk\nInstall this build",
            mimeType = "application/octet-stream"
        )
        assertTrue(isDocumentMessage(msg))
        assertTrue(isApkDocument(msg, File("/tmp/doc1.bin")))
        assertEquals("APK", resolveDocumentExtensionLabel(msg.mimeType, msg.body))
        assertEquals("Wupp-release.apk", resolveDocumentDisplayName(msg, msg.mimeType, File("/tmp/doc1.bin")))
        assertEquals("Install this build", resolveDocumentCaptionLine(msg.body))
        assertEquals("Wupp-release.apk", buildDocumentExportFileName(msg, File("/tmp/doc1.bin")))
    }

    @Test
    fun downloadExportSpecFixesOctetStreamForApkAndMissingExt() {
        val (name1, mime1) = EagerMediaVault.resolveDownloadExportSpec(
            displayName = "MyApp.apk",
            mimeType = "application/octet-stream"
        )
        assertEquals("MyApp.apk", name1)
        assertEquals("application/vnd.android.package-archive", mime1)

        val (name2, mime2) = EagerMediaVault.resolveDownloadExportSpec(
            displayName = "Installer",
            mimeType = "application/vnd.android.package-archive"
        )
        assertEquals("Installer.apk", name2)
        assertEquals("application/vnd.android.package-archive", mime2)
    }

    @Test
    fun metaLineSurfacesRealDownloadErrorReason() {
        val line = formatDocumentMetaLine(
            pageCount = null,
            fileSize = 250_000_000L,
            extLabel = "APK",
            isDownloading = false,
            errorMsg = "file exceeds 512 MB safety limit",
            hasLocalFile = false
        )
        assertTrue(line.contains("file exceeds 512 MB safety limit"))
        assertTrue(line.contains("Tap to retry"))
    }
}
