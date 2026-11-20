package com.wa.beastclient

import android.app.Application
import android.content.Context
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.data.media.SoundPoolManager
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.LocaleHelper

class WaBeastApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        EagerMediaVault.ensureDirectories(this)
        StealthMatrixManager.init(this)
        AppLockManager.init(this)
        SoundPoolManager.init(this)
        WaUiDatabase.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CallStateManager.onTrimMemory(level)
    }
}
