package com.wa.beastclient.core.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles inline quick-reply and "Mark as read" actions directly from Android message notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val chatJid = intent.getStringExtra(EXTRA_CHAT_JID)?.trim().orEmpty()
        if (chatJid.isBlank()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val appCtx = context.applicationContext

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                StealthMatrixManager.init(appCtx)
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                when (action) {
                    ACTION_INLINE_REPLY -> {
                        val replyText = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                        if (replyText.isBlank()) {
                            WaRealtimeService.refreshMessageNotificationAfterAction(appCtx, chatJid)
                            return@launch
                        }
                        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()
                        val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, chatJid.endsWith("@g.us"))
                        val quotedMsg = if (isGroup && messageId.isNotBlank()) {
                            dao.getMessage(messageId)
                        } else {
                            null
                        }

                        val sendResult = WaRealtimeService.sendTextMessage(
                            context = appCtx,
                            chatJid = chatJid,
                            text = replyText,
                            quoted = quotedMsg,
                            ghostMentionEveryone = false
                        )
                        sendResult
                            .onSuccess { sentEntity ->
                                val privacy = StealthMatrixManager.getEffectivePrivacy(dao, chatJid)
                                val offline = StealthMatrixManager.config.value.offlineMode
                                if (shouldSendReadReceiptOnNotificationAction(privacy.sendReadReceipts, offline)) {
                                    WaRealtimeService.markChatReadExplicitly(appCtx, chatJid)
                                } else {
                                    dao.clearLocalUnread(chatJid)
                                }
                                WaRealtimeService.appendSelfReplyToNotification(
                                    context = appCtx,
                                    chatJid = chatJid,
                                    replyText = replyText,
                                    timestampMs = sentEntity.timestamp
                                )
                            }
                            .onFailure { err ->
                                WaRealtimeService.refreshMessageNotificationAfterAction(appCtx, chatJid)
                                val errMsg = err.message?.takeIf { it.isNotBlank() } ?: "Failed to send reply"
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(appCtx, errMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                    }

                    ACTION_MARK_READ -> {
                        val privacy = StealthMatrixManager.getEffectivePrivacy(dao, chatJid)
                        val offline = StealthMatrixManager.config.value.offlineMode
                        if (shouldSendReadReceiptOnNotificationAction(privacy.sendReadReceipts, offline)) {
                            WaRealtimeService.markChatReadExplicitly(appCtx, chatJid)
                        } else {
                            dao.clearLocalUnread(chatJid)
                        }
                        WaRealtimeService.clearNotificationHistory(chatJid)
                        if (notifId != 0) {
                            val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            mgr?.cancel(notifId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_INLINE_REPLY = "com.wupp.app.action.NOTIFICATION_INLINE_REPLY"
        const val ACTION_MARK_READ = "com.wupp.app.action.NOTIFICATION_MARK_READ"
        const val KEY_REPLY_TEXT = "key_notification_reply_text"
        const val EXTRA_CHAT_JID = "extra_chat_jid"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_GROUP = "extra_is_group"
    }
}

internal data class NotificationActionPolicy(
    val showReplyAction: Boolean,
    val showMarkReadAction: Boolean
)

internal fun resolveNotificationActionPolicy(
    notifyShowPreview: Boolean,
    isChannel: Boolean,
    isAnnounceLocked: Boolean
): NotificationActionPolicy {
    if (isChannel || !notifyShowPreview) {
        return NotificationActionPolicy(showReplyAction = false, showMarkReadAction = false)
    }
    return NotificationActionPolicy(
        showReplyAction = !isAnnounceLocked,
        showMarkReadAction = true
    )
}

internal fun shouldSendReadReceiptOnNotificationAction(
    sendReadReceipts: Boolean,
    offlineMode: Boolean
): Boolean = sendReadReceipts && !offlineMode
