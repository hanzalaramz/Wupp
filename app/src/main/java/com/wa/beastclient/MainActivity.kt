package com.wa.beastclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.core.update.WuppUpdater
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.components.AppUpdateDialog
import com.wa.beastclient.ui.screens.ChatScreen
import com.wa.beastclient.ui.screens.FirstInstallSetupScreen
import com.wa.beastclient.ui.screens.HomeScreen
import com.wa.beastclient.ui.screens.LinkDeviceScreen
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.WhatsAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val setupPrefs = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
        val initialSetupDone = setupPrefs.getBoolean("setup_completed_v1", false)
        if (initialSetupDone) {
            WaRealtimeService.ensureStarted(this)
        }

        setContent {
            val vm: MainViewModel = viewModel()
            val stealth by vm.stealthConfig.collectAsState()
            val bridgeStatus by vm.bridgeStatus.collectAsState()
            val updateState by vm.updateState.collectAsState()
            val showUpdatePrompt by vm.showUpdatePrompt.collectAsState()
            var setupDone by remember { mutableStateOf(initialSetupDone) }
            var selectedChatJid by rememberSaveable { mutableStateOf<String?>(null) }

            LaunchedEffect(intent?.getBooleanExtra("open_update_prompt", false)) {
                if (intent?.getBooleanExtra("open_update_prompt", false) == true) {
                    intent?.removeExtra("open_update_prompt")
                    vm.openUpdatePrompt()
                }
            }

            BackHandler(enabled = setupDone && bridgeStatus.isLoggedIn && selectedChatJid != null) {
                selectedChatJid = null
            }

            WhatsAppTheme(
                themeMode = stealth.themeMode,
                assetManager = assets
            ) {
                if (!setupDone) {
                    FirstInstallSetupScreen(
                        onCompleteSetup = {
                            setupPrefs.edit().putBoolean("setup_completed_v1", true).apply()
                            setupDone = true
                            WaRealtimeService.ensureStarted(this@MainActivity)
                        }
                    )
                } else if (!bridgeStatus.engineReady) {
                    val colors = LocalWaColors.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.appSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.wupp_logo),
                                contentDescription = "Wupp",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            CircularProgressIndicator(
                                color = colors.accentGreen,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else if (!bridgeStatus.isLoggedIn) {
                    LinkDeviceScreen(
                        bridgeStatus = bridgeStatus,
                        onPairPhone = { phone, onCode -> vm.pairWithPhoneNumber(phone, onCode) },
                        onRefreshConnection = { vm.reconnectSocket() }
                    )
                } else {
                    var homeTab by rememberSaveable { mutableIntStateOf(0) }
                    val chatsListState = rememberLazyListState()
                    val updatesListState = rememberLazyListState()
                    val communitiesListState = rememberLazyListState()
                    val toolsListState = rememberLazyListState()
                    val activeJid = selectedChatJid
                    if (activeJid == null) {
                        HomeScreen(
                            viewModel = vm,
                            onOpenChat = { jid -> selectedChatJid = jid },
                            currentTab = homeTab,
                            onSelectTab = { homeTab = it },
                            chatsListState = chatsListState,
                            updatesListState = updatesListState,
                            communitiesListState = communitiesListState,
                            toolsListState = toolsListState
                        )
                    } else {
                        ChatScreen(
                            chatJid = activeJid,
                            viewModel = vm,
                            onBack = { selectedChatJid = null }
                        )
                    }
                }

                if (showUpdatePrompt && setupDone) {
                    AppUpdateDialog(
                        state = updateState,
                        onDownload = { release -> vm.downloadUpdate(release) },
                        onInstall = { apkFile -> vm.installDownloadedApk(apkFile) },
                        onSkipVersion = { vm.dismissUpdatePrompt(skipVersion = false) },
                        onDismiss = { vm.dismissUpdatePrompt(skipVersion = false) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_update_prompt", false)) {
            WuppUpdater.openPromptDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        val setupDone = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        if (setupDone) {
            WaRealtimeService.ensureStarted(this)
            lifecycleScope.launch {
                WuppUpdater.onAppOpened(this@MainActivity)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        WuppUpdater.onAppBackgrounded()
    }
}
