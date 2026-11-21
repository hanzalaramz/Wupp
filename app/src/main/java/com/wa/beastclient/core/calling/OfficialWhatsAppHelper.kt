package com.wa.beastclient.core.calling

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object OfficialWhatsAppHelper {
    private val OFFICIAL_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Returns the installed official WhatsApp (or Business) package name, or null if absent. */
    fun findInstalledPackage(context: Context): String? {
        val pm = context.packageManager
        return OFFICIAL_PACKAGES.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /**
     * Opens Android's Notification settings directly for the installed official WhatsApp package,
     * falling back to App Details if a device OEM restricts notification deep links.
     */
    fun openNotificationSettings(context: Context, packageName: String): Boolean {
        val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedNotif = runCatching {
            context.startActivity(notifIntent)
            true
        }.getOrDefault(false)
        if (launchedNotif) return true

        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(detailsIntent)
            true
        }.getOrDefault(false)
    }
}

/**
 * Pure policy check for whether Wupp should ring and show its incoming-call popup locally.
 * Suppression is strictly local so VoIP signaling never sends premature reject stanzas.
 */
internal fun shouldRingLocallyForIncomingCall(
    suppressWhenOfficialInstalled: Boolean,
    officialAppInstalled: Boolean
): Boolean = !(suppressWhenOfficialInstalled && officialAppInstalled)

/**
 * Shows the one-time coexistence setup dialog only after setup + login complete, when an
 * official WhatsApp app is installed on the device and the user has not resolved or dismissed it.
 */
internal fun shouldPromptOfficialCallCoexistence(
    setupDone: Boolean,
    isLoggedIn: Boolean,
    isAppLocked: Boolean,
    alreadyPrompted: Boolean,
    officialAppInstalled: Boolean,
    officialMuteConfirmed: Boolean,
    suppressWhenOfficialInstalled: Boolean
): Boolean = setupDone &&
    isLoggedIn &&
    !isAppLocked &&
    !alreadyPrompted &&
    officialAppInstalled &&
    !officialMuteConfirmed &&
    !suppressWhenOfficialInstalled
