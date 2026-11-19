package com.wa.beastclient.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionPolicyTest {

    @Test
    fun normalChatWithPreviewsShowsBothReplyAndMarkRead() {
        val policy = resolveNotificationActionPolicy(
            notifyShowPreview = true,
            isChannel = false,
            isAnnounceLocked = false
        )
        assertTrue(policy.showReplyAction)
        assertTrue(policy.showMarkReadAction)
    }

    @Test
    fun hiddenPreviewsHideInlineActionsForPrivacy() {
        val policy = resolveNotificationActionPolicy(
            notifyShowPreview = false,
            isChannel = false,
            isAnnounceLocked = false
        )
        assertFalse(policy.showReplyAction)
        assertFalse(policy.showMarkReadAction)
    }

    @Test
    fun announceLockedGroupHidesReplyButAllowsMarkRead() {
        val policy = resolveNotificationActionPolicy(
            notifyShowPreview = true,
            isChannel = false,
            isAnnounceLocked = true
        )
        assertFalse(policy.showReplyAction)
        assertTrue(policy.showMarkReadAction)
    }

    @Test
    fun channelHidesBothActions() {
        val policy = resolveNotificationActionPolicy(
            notifyShowPreview = true,
            isChannel = true,
            isAnnounceLocked = false
        )
        assertFalse(policy.showReplyAction)
        assertFalse(policy.showMarkReadAction)
    }

    @Test
    fun readReceiptHelperRespectsPrivacyAndOfflineMode() {
        assertTrue(shouldSendReadReceiptOnNotificationAction(sendReadReceipts = true, offlineMode = false))
        assertFalse(shouldSendReadReceiptOnNotificationAction(sendReadReceipts = false, offlineMode = false))
        assertFalse(shouldSendReadReceiptOnNotificationAction(sendReadReceipts = true, offlineMode = true))
    }
}
