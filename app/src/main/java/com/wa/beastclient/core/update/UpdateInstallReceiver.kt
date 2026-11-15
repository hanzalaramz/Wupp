package com.wa.beastclient.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives commit status callbacks from Android's [PackageInstaller.Session]
 * so system install confirmation dialogs launch cleanly and errors surface clearly.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }.onFailure { err ->
                        WuppUpdater.onPackageInstallFailed(
                            err.message ?: "Unable to open Android system installer confirmation"
                        )
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                WuppUpdater.onPackageInstallSucceeded()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                WuppUpdater.onPackageInstallCancelled()
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                WuppUpdater.onPackageInstallFailed(
                    "App signature conflict — uninstall your current Wupp build first, then install this update."
                )
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                WuppUpdater.onPackageInstallFailed(
                    "This APK is incompatible with your device CPU or Android version. Try the universal APK from GitHub."
                )
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                WuppUpdater.onPackageInstallFailed(
                    "Not enough free storage space to install this update."
                )
            }
            else -> {
                if (status != Int.MIN_VALUE) {
                    WuppUpdater.onPackageInstallFailed(
                        statusMessage.ifBlank { "Package installer failed (code $status). Try External Installer or Open the web." }
                    )
                }
            }
        }
    }
}
