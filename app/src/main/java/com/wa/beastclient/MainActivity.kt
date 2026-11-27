package com.wa.beastclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wa.beastclient.core.calling.CallPhase
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.OfficialWhatsAppHelper
import com.wa.beastclient.core.calling.shouldPromptOfficialCallCoexistence
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.core.update.WuppUpdater
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.calling.OfficialWhatsAppCoexistenceDialog
import com.wa.beastclient.ui.components.AppLockOverlay
import com.wa.beastclient.ui.components.AppUpdateDialog
import com.wa.beastclient.ui.screens.ChatScreen
import com.wa.beastclient.ui.screens.ContactInfoScreen
import com.wa.beastclient.ui.screens.FirstInstallSetupScreen
import com.wa.beastclient.ui.screens.HomeScreen
import com.wa.beastclient.ui.screens.LinkDeviceScreen
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocaleHelper
import com.wa.beastclient.ui.theme.WhatsAppTheme
import com.wa.beastclient.ui.components.ForwardMessageDialog
import com.wa.bridge.whatsbridge.Whatsbridge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

data class IncomingShareData(
    val text: String? = null,
    val uris: List<android.net.Uri> = emptyList(),
    val mimeType: String? = null
)

class MainActivity : FragmentActivity() {
    private val pendingOpenChatJid = MutableStateFlow<String?>(null)
    private val pendingShareData = MutableStateFlow<IncomingShareData?>(null)

    private fun handleIncomingShareIntent(targetIntent: Intent?) {
        if (targetIntent == null) return
        val action = targetIntent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val mimeType = targetIntent.type
        val text = targetIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val streamUris = when (action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    targetIntent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    targetIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
                }
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list.orEmpty()
            }
            else -> emptyList()
        }
        val uris = if (streamUris.isNotEmpty()) {
            streamUris
        } else {
            val clip = targetIntent.clipData
            buildList {
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { add(it) }
                    }
                }
            }
        }

        if (!text.isNullOrBlank() || uris.isNotEmpty()) {
            pendingShareData.value = IncomingShareData(text = text, uris = uris, mimeType = mimeType)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.getStringExtra("open_chat_jid")?.takeIf { it.isNotBlank() }?.let { jid ->
            pendingOpenChatJid.value = jid
            intent?.removeExtra("open_chat_jid")
        }
        handleIncomingShareIntent(intent)

        val setupPrefs = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
        val initialSetupDone = setupPrefs.getBoolean("setup_completed_v1", false)
        CallStateManager.init(applicationContext)
        if (initialSetupDone && Whatsbridge.isNativeLoaded) {
            WaRealtimeService.ensureStarted(this)
        }

        setContent {
            val isNativeLoaded = remember { Whatsbridge.isNativeLoaded }
            if (!isNativeLoaded) {
                WhatsAppTheme(
                    themeMode = com.wa.beastclient.ui.theme.WuppThemeMode.AMOLED,
                    assetManager = assets
                ) {
                    val colors = LocalWaColors.current
                    Box(modifier = Modifier.fillMaxSize().background(colors.appSurface), contentAlignment = Alignment.Center) {
                        AlertDialog(
                            onDismissRequest = { finish() },
                            containerColor = colors.elevatedBar,
                            title = { Text("Unsupported Architecture", color = colors.textPrimary) },
                            text = {
                                Text(
                                    "This device runs a 32-bit CPU (armeabi-v7a). Wupp requires a 64-bit device (arm64-v8a) to load its native engine.",
                                    color = colors.textSecondary
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { finish() }) {
                                    Text("Exit", color = colors.accentGreen)
                                }
                            }
                        )
                    }
                }
                return@setContent
            }
            val vm: MainViewModel = viewModel()
            val stealth by vm.stealthConfig.collectAsState()
            val appLockConfig by vm.appLockConfig.collectAsState()
            val isAppLocked by vm.isAppLocked.collectAsState()
            val bridgeStatus by vm.bridgeStatus.collectAsState()
            val accounts by vm.accounts.collectAsState()
            val activeAccountId by vm.activeAccountId.collectAsState()
            val accountGeneration by vm.accountGeneration.collectAsState()
            val isSwitchingAccount by vm.isSwitchingAccount.collectAsState()
            val callUiState by CallStateManager.uiState.collectAsState()
            val updateState by vm.updateState.collectAsState()
            val showUpdatePrompt by vm.showUpdatePrompt.collectAsState()
            var setupDone by remember { mutableStateOf(initialSetupDone) }
            var selectedChatJid by rememberSaveable { mutableStateOf<String?>(null) }
            var activeContactInfoJid by rememberSaveable { mutableStateOf<String?>(null) }
            val requestedChatJid by pendingOpenChatJid.collectAsState()
            var officialCoexistenceDismissed by remember {
                mutableStateOf(setupPrefs.getBoolean("official_call_coexistence_prompted_v1", false))
            }
            val installedOfficialPkg = remember(setupDone, bridgeStatus.isLoggedIn) {
                OfficialWhatsAppHelper.findInstalledPackage(this@MainActivity)
            }
            val showOfficialCoexistenceDialog = shouldPromptOfficialCallCoexistence(
                setupDone = setupDone,
                isLoggedIn = bridgeStatus.isLoggedIn,
                isAppLocked = isAppLocked,
                alreadyPrompted = officialCoexistenceDismissed,
                officialAppInstalled = installedOfficialPkg != null,
                officialMuteConfirmed = stealth.officialCallMuteConfirmed,
                suppressWhenOfficialInstalled = stealth.suppressIncomingRingWhenOfficialInstalled
            )

            LaunchedEffect(accountGeneration) {
                selectedChatJid = null
                activeContactInfoJid = null
            }

            LaunchedEffect(appLockConfig) {
                AppLockManager.applyWindowSecureFlag(this@MainActivity, appLockConfig)
            }

            LaunchedEffect(stealth.offlineMode, stealth.callBackendMode) {
                CallStateManager.onConfigChanged(stealth)
            }

            LaunchedEffect(requestedChatJid, setupDone, bridgeStatus.isLoggedIn, isAppLocked) {
                val target = requestedChatJid
                if (!target.isNullOrBlank() && setupDone && bridgeStatus.isLoggedIn && !isAppLocked) {
                    val chat = vm.observeChat(target).first()
                    if (vm.isAppLocked.value || pendingOpenChatJid.value != target) return@LaunchedEffect
                    val hidden = AppLockManager.shouldSuppressAlertsForChat(
                        chatIsLocked = chat?.isLocked == true,
                        cfg = vm.appLockConfig.value,
                        revealed = vm.lockedChatsRevealed.value
                    )
                    activeContactInfoJid = null
                    selectedChatJid = target.takeUnless { hidden }
                    if (hidden) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Unlock locked chats to open this conversation",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    pendingOpenChatJid.value = null
                }
            }

            LaunchedEffect(intent?.getBooleanExtra("open_update_prompt", false)) {
                if (intent?.getBooleanExtra("open_update_prompt", false) == true) {
                    intent?.removeExtra("open_update_prompt")
                    vm.openUpdatePrompt()
                }
            }

            BackHandler(enabled = setupDone && bridgeStatus.isLoggedIn && (activeContactInfoJid != null || selectedChatJid != null) && !isAppLocked) {
                if (activeContactInfoJid != null) {
                    activeContactInfoJid = null
                } else {
                    selectedChatJid = null
                }
            }

            WhatsAppTheme(
                themeMode = stealth.themeMode,
                accentColor = stealth.accentColor,
                pureBlackOled = stealth.pureBlackOled,
                dynamicColors = stealth.dynamicMaterialYou,
                useSystemFont = stealth.useSystemFont,
                fontStyle = stealth.fontStyle,
                customFontPath = stealth.customFontPath,
                bubbleCornerRadiusDp = stealth.bubbleCornerRadiusDp,
                customOutgoingBubbleArgb = stealth.customOutgoingBubbleArgb,
                customIncomingBubbleArgb = stealth.customIncomingBubbleArgb,
                showSecondsInTimestamps = stealth.showSecondsInTimestamps,
                use24HourFormat = StealthMatrixManager.is24HourEnabled(this, stealth.clockFormat),
                chatFontScale = stealth.chatFontScale,
                classicUi = stealth.classicWhatsAppLook,
                assetManager = assets
            ) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                val motionScheme = MaterialTheme.motionScheme

                Box(modifier = Modifier.fillMaxSize()) {

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
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(22.dp))
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            ContainedLoadingIndicator(
                                containerColor = colors.pillActiveBg,
                                indicatorColor = colors.brightGreen,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                } else if (!bridgeStatus.isLoggedIn) {
                    LinkDeviceScreen(
                        bridgeStatus = bridgeStatus,
                        onPairPhone = { phone, onCode -> vm.pairWithPhoneNumber(phone, onCode) },
                        onRefreshConnection = { vm.reconnectSocket() },
                        accounts = accounts,
                        activeAccountId = activeAccountId,
                        isSwitchingAccount = isSwitchingAccount,
                        onSwitchAccount = { vm.switchAccount(it) },
                        onAddAccount = { vm.addAndSwitchAccount(it) },
                        onRenameAccount = { id, label -> vm.renameAccount(id, label) },
                        onRemoveAccount = { vm.removeInactiveAccount(it) }
                    )
                } else {
                    key(activeAccountId, accountGeneration) {
                        var homeTab by rememberSaveable { mutableIntStateOf(0) }
                        val chatsListState = rememberLazyListState()
                        val groupsListState = rememberLazyListState()
                        val updatesListState = rememberLazyListState()
                        val communitiesListState = rememberLazyListState()
                        val callsListState = rememberLazyListState()
                        val toolsListState = rememberLazyListState()

                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        AnimatedContent(
                            targetState = activeContactInfoJid,
                            transitionSpec = {
                                if (targetState != null) {
                                    (slideInHorizontally(
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) { it / 3 } + scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) + fadeIn(
                                        animationSpec = motionScheme.fastEffectsSpec()
                                    )) togetherWith (slideOutHorizontally(
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) { -it / 6 } + fadeOut(
                                        animationSpec = motionScheme.fastEffectsSpec()
                                    ))
                                } else {
                                    (slideInHorizontally(
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) { -it / 6 } + fadeIn(
                                        animationSpec = motionScheme.fastEffectsSpec()
                                    )) togetherWith (slideOutHorizontally(
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) { it / 3 } + scaleOut(
                                        targetScale = 0.96f,
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    ) + fadeOut(
                                        animationSpec = motionScheme.fastEffectsSpec()
                                    ))
                                }
                            },
                            label = "contact_info_route"
                        ) { infoJid ->
                            if (infoJid != null) {
                                ContactInfoScreen(
                                    chatJid = infoJid,
                                    viewModel = vm,
                                    onBack = { activeContactInfoJid = null },
                                    onOpenChat = { jid ->
                                        activeContactInfoJid = null
                                        selectedChatJid = jid
                                    }
                                )
                            } else {
                                AnimatedContent(
                                    targetState = selectedChatJid,
                                    transitionSpec = {
                                        if (targetState != null) {
                                            (slideInHorizontally(
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) { it / 3 } + scaleIn(
                                                initialScale = 0.96f,
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) + fadeIn(
                                                animationSpec = motionScheme.fastEffectsSpec()
                                            )) togetherWith (slideOutHorizontally(
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) { -it / 6 } + fadeOut(
                                                animationSpec = motionScheme.fastEffectsSpec()
                                            ))
                                        } else {
                                            (slideInHorizontally(
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) { -it / 6 } + fadeIn(
                                                animationSpec = motionScheme.fastEffectsSpec()
                                            )) togetherWith (slideOutHorizontally(
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) { it / 3 } + scaleOut(
                                                targetScale = 0.96f,
                                                animationSpec = motionScheme.defaultSpatialSpec()
                                            ) + fadeOut(
                                                animationSpec = motionScheme.fastEffectsSpec()
                                            ))
                                        }
                                    },
                                    label = "home_chat_route"
                                ) { activeJid ->
                                    if (activeJid == null) {
                                        HomeScreen(
                                            viewModel = vm,
                                            onOpenChat = { jid -> selectedChatJid = jid },
                                            onOpenContactInfo = { jid -> activeContactInfoJid = jid },
                                            currentTab = homeTab,
                                            onSelectTab = { homeTab = it },
                                            chatsListState = chatsListState,
                                            groupsListState = groupsListState,
                                            updatesListState = updatesListState,
                                            communitiesListState = communitiesListState,
                                            callsListState = callsListState,
                                            toolsListState = toolsListState
                                        )
                                    } else {
                                        ChatScreen(
                                            chatJid = activeJid,
                                            viewModel = vm,
                                            onBack = { selectedChatJid = null },
                                            onOpenContactInfo = { jid -> activeContactInfoJid = jid },
                                            onNavigateToChat = { jid -> selectedChatJid = jid }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showOfficialCoexistenceDialog && installedOfficialPkg != null) {
                    OfficialWhatsAppCoexistenceDialog(
                        officialPkg = installedOfficialPkg,
                        stealth = stealth,
                        onDismiss = {
                            setupPrefs.edit().putBoolean("official_call_coexistence_prompted_v1", true).apply()
                            officialCoexistenceDismissed = true
                        }
                    )
                }

                if (showUpdatePrompt && setupDone && !isAppLocked) {
                    AppUpdateDialog(
                        state = updateState,
                        onDownload = { release -> vm.downloadUpdate(release) },
                        onDownloadToDevice = { release, apkPath -> vm.downloadUpdateToDevice(release, apkPath) },
                        onInstall = { apkFile -> vm.installDownloadedApk(apkFile) },
                        onInstallExternal = { apkFile -> vm.installExternalApkFallback(apkFile) },
                        onSkipVersion = { vm.dismissUpdatePrompt(skipVersion = false) },
                        onDismiss = { vm.dismissUpdatePrompt(skipVersion = false) }
                    )
                }

                if (setupDone && isAppLocked) {
                    AppLockOverlay(config = appLockConfig)
                }

                val shareData by pendingShareData.collectAsState()
                if (shareData != null && setupDone && bridgeStatus.isLoggedIn && !isAppLocked) {
                    val allChatsList by vm.allChats.collectAsState()
                    val hiddenLockedJids by vm.hiddenLockedJids.collectAsState()
                    val shareableChats = remember(allChatsList, hiddenLockedJids) {
                        if (hiddenLockedJids.isEmpty()) allChatsList
                        else allChatsList.filter { it.jid !in hiddenLockedJids && it.jid.substringBefore("@").substringBefore(":") !in hiddenLockedJids }
                    }
                    val colors = LocalWaColors.current
                    ForwardMessageDialog(
                        allChats = shareableChats,
                        announceLockedGroups = emptySet(),
                        currentChatJid = "",
                        colors = colors,
                        onDismiss = { pendingShareData.value = null },
                        onConfirmForward = { targetJids ->
                            val data = shareData
                            pendingShareData.value = null
                            if (data != null && targetJids.isNotEmpty()) {
                                if (data.uris.isNotEmpty()) {
                                    lifecycleScope.launch {
                                        for (u in data.uris) {
                                            val perUriMime = runCatching { contentResolver.getType(u) }
                                                .getOrNull()
                                                ?.takeIf { it.isNotBlank() }
                                                ?: data.mimeType
                                            val staged = vm.stageMediaForPreview(u, perUriMime) ?: continue
                                            for (targetJid in targetJids) {
                                                vm.sendStagedMedia(
                                                    chatJid = targetJid,
                                                    staged = staged,
                                                    caption = data.text.orEmpty(),
                                                    qualityMode = com.wa.beastclient.data.media.OutboundQualityMode.STANDARD
                                                )
                                            }
                                        }
                                    }
                                } else if (!data.text.isNullOrBlank()) {
                                    for (targetJid in targetJids) {
                                        vm.sendText(
                                            chatJid = targetJid,
                                            text = data.text,
                                            quoted = null,
                                            ghostMentionEveryone = false
                                        )
                                    }
                                }
                                selectedChatJid = targetJids.first()
                            }
                        }
                    )
                }
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
        intent.getStringExtra("open_chat_jid")?.takeIf { it.isNotBlank() }?.let { jid ->
            pendingOpenChatJid.value = jid
            intent.removeExtra("open_chat_jid")
        }
        handleIncomingShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onAppForegrounded()
        WaRealtimeService.isUiForeground = true
        CallStateManager.onAppForegroundChanged(true)
        val setupDone = getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        if (setupDone && Whatsbridge.isNativeLoaded) {
            WaRealtimeService.ensureStarted(this)
            lifecycleScope.launch {
                WuppUpdater.onAppOpened(this@MainActivity)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
        WaRealtimeService.isUiForeground = false
        CallStateManager.onAppForegroundChanged(false)
        WuppUpdater.onAppBackgrounded()
    }
}
