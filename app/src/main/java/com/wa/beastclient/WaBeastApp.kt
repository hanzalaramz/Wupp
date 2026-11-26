package com.wa.beastclient

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.logging.AppLogManager
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.data.media.SoundPoolManager
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.LocaleHelper

class WaBeastApp : Application(), ImageLoaderFactory {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        AppLogManager.installCrashHandler(this)
        EagerMediaVault.ensureDirectories(this)
        StealthMatrixManager.init(this)
        AppLockManager.init(this)
        SoundPoolManager.init(this)
        com.wa.beastclient.domain.scheduling.ScheduledMessageManager.init(this)
        WaUiDatabase.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CallStateManager.onTrimMemory(level)
    }
}
