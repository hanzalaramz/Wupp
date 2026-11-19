package com.wa.beastclient.core.calling

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wa.beastclient.R
import com.wa.beastclient.ui.calling.CallActivity

/**
 * Foreground service active solely during ringing/dialing/in-call states,
 * isolated from WaRealtimeService so microphone/camera FGS types stay scoped to calls.
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER_CALL -> CallStateManager.answerIncomingCall(audioOnly = false)
            ACTION_DECLINE_CALL, ACTION_END_CALL -> {
                CallStateManager.declineOrEndCall()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_OFFICIAL_WA -> {
                CallStateManager.openOfficialWhatsAppFallback()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val state = CallStateManager.uiState.value
        if (state.phase == CallPhase.IDLE || state.phase == CallPhase.FAILED) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureCallNotificationChannel()
        val notification = buildCallNotification(state)
        val fgsType = computeForegroundServiceType(state)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        return START_NOT_STICKY
    }

    private fun computeForegroundServiceType(state: CallUiState): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var mask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }
        val isMediaPhase = state.phase == CallPhase.DIALING ||
            state.phase == CallPhase.CONNECTING ||
            state.phase == CallPhase.ACTIVE
        if (isMediaPhase && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (state.isVideo && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return mask
    }

    private fun ensureCallNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Voice & Video Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming and ongoing Wupp voice/video calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        mgr.createNotificationChannel(ch)
    }

    private fun buildCallNotification(state: CallUiState): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declinePi = serviceActionPendingIntent(101, ACTION_DECLINE_CALL)
        val answerPi = if (state.canAnswerInApp) {
            serviceActionPendingIntent(102, ACTION_ANSWER_CALL)
        } else {
            serviceActionPendingIntent(103, ACTION_OPEN_OFFICIAL_WA)
        }
        val hangupPi = serviceActionPendingIntent(104, ACTION_END_CALL)
        val callerName = state.peerName.ifBlank { "WhatsApp Call" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build()
            val style = if (state.phase == CallPhase.INCOMING_RINGING) {
                Notification.CallStyle.forIncomingCall(caller, declinePi, answerPi)
            } else {
                Notification.CallStyle.forOngoingCall(caller, hangupPi)
            }
            return Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_wupp)
                .setContentTitle(callerName)
                .setContentText(
                    state.statusText.ifBlank {
                        if (state.isVideo) "Video call" else "Voice call"
                    }
                )
                .setStyle(style)
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .build()
        }

        val compat = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wupp)
            .setContentTitle(callerName)
            .setContentText(state.statusText.ifBlank { if (state.isVideo) "Video call" else "Voice call" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .setOngoing(true)
        if (state.phase == CallPhase.INCOMING_RINGING) {
            compat.addAction(0, "Decline", declinePi)
            compat.addAction(0, if (state.canAnswerInApp) "Answer" else "Open WhatsApp", answerPi)
        } else {
            compat.addAction(0, "End Call", hangupPi)
        }
        return compat.build()
    }

    private fun serviceActionPendingIntent(reqCode: Int, action: String): PendingIntent {
        val intent = Intent(this, CallForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "wupp_calls_high"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_SYNC = "com.wa.beastclient.calling.SYNC"
        const val ACTION_STOP = "com.wa.beastclient.calling.STOP"
        const val ACTION_ANSWER_CALL = "com.wa.beastclient.calling.ANSWER"
        const val ACTION_DECLINE_CALL = "com.wa.beastclient.calling.DECLINE"
        const val ACTION_END_CALL = "com.wa.beastclient.calling.END"
        const val ACTION_OPEN_OFFICIAL_WA = "com.wa.beastclient.calling.OPEN_OFFICIAL"

        fun sync(context: Context, state: CallUiState) {
            if (state.phase == CallPhase.IDLE || state.phase == CallPhase.FAILED) {
                stop(context)
                return
            }
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SYNC
            }
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.applicationContext.stopService(intent) }
        }
    }
}
