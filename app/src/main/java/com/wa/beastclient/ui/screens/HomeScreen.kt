package com.wa.beastclient.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.wa.beastclient.BuildConfig
import com.wa.beastclient.R
import com.wa.beastclient.core.service.OwnProfileUi
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.core.update.UpdateUiState
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.security.AppLockMode
import com.wa.beastclient.domain.stealth.AutoDownloadNetwork
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.HomeFilterChip
import com.wa.beastclient.ui.components.AppLanguageDialog
import com.wa.beastclient.ui.components.AppLockConfigDialog
import com.wa.beastclient.ui.components.AutoDownloadNetworkDialog
import com.wa.beastclient.ui.components.BlockedContactsDialog
import com.wa.beastclient.ui.components.ChatFontSizeDialog
import com.wa.beastclient.ui.components.CompanionScopeInfoDialog
import com.wa.beastclient.ui.components.ExpressiveIconBadge
import com.wa.beastclient.ui.components.ExpressiveSegmentPosition
import com.wa.beastclient.ui.components.ExpressiveSegmentedActionRow
import com.wa.beastclient.ui.components.ExpressiveSegmentedContainer
import com.wa.beastclient.ui.components.ExpressiveSegmentedGroup
import com.wa.beastclient.ui.components.ExpressiveSegmentedSwitchRow
import com.wa.beastclient.ui.components.FullScreenMediaViewer
import com.wa.beastclient.ui.components.FullScreenStatusViewer
import com.wa.beastclient.ui.components.GalleryVisibilityContactsDialog
import com.wa.beastclient.ui.components.ProfileEditorDialog
import com.wa.beastclient.ui.components.SettingsProfileHeaderCard
import com.wa.beastclient.ui.components.StatusComposerMode
import com.wa.beastclient.ui.components.StatusComposerSheet
import com.wa.beastclient.ui.components.StatusGroup
import com.wa.beastclient.ui.components.StorageUsageDialog
import com.wa.beastclient.ui.components.WhatsAppAccountPrivacyDialog
import com.wa.beastclient.ui.components.formatFontScaleLabel
import com.wa.beastclient.ui.components.isOwnStatusSender
import com.wa.beastclient.ui.components.partitionOwnAndContactStatuses
import com.wa.beastclient.ui.components.GitHubFeedbackDialog
import com.wa.beastclient.ui.components.GitHubFeedbackType
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.components.ChatPeekPopupOverlay
import com.wa.beastclient.ui.components.DeliveryTickIcon
import com.wa.beastclient.ui.components.QuickContactDialog
import com.wa.beastclient.ui.components.ReactionEmojiCustomizerDialog
import com.wa.beastclient.ui.components.ThemePickerDialog
import com.wa.beastclient.ui.components.ThemeSelectorGrid
import com.wa.beastclient.ui.components.WallpaperPickerDialog
import com.wa.beastclient.ui.components.WuppGlassContainer
import com.wa.beastclient.ui.components.WuppGlassNavBar
import com.wa.beastclient.ui.components.describeChatWallpaper
import com.wa.beastclient.ui.components.expressivePressScale
import com.wa.beastclient.ui.components.findFragmentActivity
import com.wa.beastclient.ui.components.formatMessageClock
import com.wa.beastclient.ui.components.launchDeviceAuthPrompt
import com.wa.beastclient.ui.components.rememberWuppBackdropState
import com.wa.beastclient.ui.components.wuppBackdropSource
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocaleHelper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Returns the bottom-bar tab IDs in swipeable left-to-right order. */
internal fun resolveHomeTabOrder(separateGroupsAndChats: Boolean): List<Int> =
    if (separateGroupsAndChats) listOf(0, 4, 1, 2, 3) else listOf(0, 1, 2, 3)

internal fun resolvePagerIndexForTab(tabOrder: List<Int>, tabId: Int): Int =
    tabOrder.indexOf(tabId).let { if (it >= 0) it else 0 }

internal fun resolveTabIdForPagerIndex(tabOrder: List<Int>, pageIndex: Int): Int =
    tabOrder.getOrElse(pageIndex) { tabOrder.firstOrNull() ?: 0 }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenChat: (String) -> Unit,
    currentTab: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    chatsListState: LazyListState = rememberLazyListState(),
    groupsListState: LazyListState = rememberLazyListState(),
    updatesListState: LazyListState = rememberLazyListState(),
    communitiesListState: LazyListState = rememberLazyListState(),
    toolsListState: LazyListState = rememberLazyListState()
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current

    val chats by viewModel.filteredChats.collectAsState()
    val groupsChats by viewModel.filteredGroups.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedChip by viewModel.selectedFilter.collectAsState()
    val selectedGroupsChip by viewModel.selectedGroupsFilter.collectAsState()
    val searchQuery by viewModel.homeSearchQuery.collectAsState()
    val ftsHits by viewModel.ftsSearchResults.collectAsState()
    val stealth by viewModel.stealthConfig.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val includeArchived by viewModel.includeArchived.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val lockedChatsRevealed by viewModel.lockedChatsRevealed.collectAsState()
    val hiddenLockedCount by viewModel.hiddenLockedChatsCount.collectAsState()
    val showSeconds = LocalWuppShowSeconds.current
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val downloadingMediaIds by viewModel.downloadingMediaIds.collectAsState()
    val failedMediaIds by viewModel.failedMediaIds.collectAsState()
    val voVault by viewModel.viewOnceVault.collectAsState()
    val presenceLogs by viewModel.presenceLogs.collectAsState()
    val profileHistory by viewModel.profileHistory.collectAsState()
    val bulkResult by viewModel.bulkVerifyOutput.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    val unreadChatsCount = remember(allChats, stealth.separateGroupsAndChats) {
        allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
                (!stealth.separateGroupsAndChats || !it.isGroup) &&
                it.unreadCount > 0
        }
    }
    val unreadGroupsCount = remember(allChats, stealth.separateGroupsAndChats) {
        if (!stealth.separateGroupsAndChats) 0
        else allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.isGroup &&
                it.unreadCount > 0
        }
    }

    LaunchedEffect(stealth.separateGroupsAndChats, currentTab) {
        if (!stealth.separateGroupsAndChats && currentTab == 4) {
            onSelectTab(0)
        }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var updatesFabExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showQuickContactPicker by remember { mutableStateOf(false) }
    var quickContactTarget by remember { mutableStateOf<ChatEntity?>(null) }
    val quickContactChat = remember(quickContactTarget, allChats) {
        quickContactTarget?.let { t -> allChats.firstOrNull { it.jid == t.jid } ?: t }
    }
    var activeStatusViewer by remember { mutableStateOf<StatusEntity?>(null) }
    var statusComposerInitialMode by remember { mutableStateOf<StatusComposerMode?>(null) }
    var activeVaultViewer by remember { mutableStateOf<MessageEntity?>(null) }
    var activeFeedbackType by remember { mutableStateOf<GitHubFeedbackType?>(null) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showLockedChatsPinPrompt by remember { mutableStateOf(false) }
    var selectedChatJids by remember { mutableStateOf(setOf<String>()) }
    var selectionOverflowExpanded by remember { mutableStateOf(false) }
    var pendingDeleteJids by remember { mutableStateOf<List<String>>(emptyList()) }
    var peekChatTarget by remember { mutableStateOf<ChatEntity?>(null) }
    val peekChat = remember(peekChatTarget, allChats) {
        peekChatTarget?.let { target -> allChats.firstOrNull { it.jid == target.jid } ?: target }
    }
    var lastPeekChat by remember { mutableStateOf<ChatEntity?>(null) }
    LaunchedEffect(peekChat) {
        if (peekChat != null) lastPeekChat = peekChat
    }
    val haptics = LocalHapticFeedback.current

    val hostActivity = remember(context) { context.findFragmentActivity() }
    val lockedDeviceAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.setLockedChatsRevealed(true)
        }
    }
    val unlockPromptTitle = stringResource(R.string.applock_prompt_title)
    val unlockPromptSubtitle = stringResource(R.string.applock_prompt_subtitle)
    val unlockNoDeviceLock = stringResource(R.string.applock_no_device_lock)
    fun requestRevealLockedChats() {
        if (!appLockConfig.hideLockedChatsBehindLock || lockedChatsRevealed) {
            viewModel.setLockedChatsRevealed(true)
            return
        }
        when (appLockConfig.mode) {
            AppLockMode.DEVICE -> {
                val host = hostActivity
                if (host != null) {
                    launchDeviceAuthPrompt(
                        activity = host,
                        title = unlockPromptTitle,
                        subtitle = unlockPromptSubtitle,
                        cancelText = unlockNoDeviceLock,
                        keyguardFallbackLauncher = { intent -> lockedDeviceAuthLauncher.launch(intent) },
                        onAuthenticated = { viewModel.setLockedChatsRevealed(true) },
                        onErrorMessage = { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    viewModel.setLockedChatsRevealed(true)
                }
            }
            AppLockMode.CUSTOM_PIN -> {
                showLockedChatsPinPrompt = true
            }
            AppLockMode.OFF -> {
                viewModel.setLockedChatsRevealed(true)
            }
        }
    }

    val selectedChats = remember(selectedChatJids, allChats, chats, groupsChats) {
        val merged = (allChats + chats + groupsChats).associateBy { it.jid }
        selectedChatJids.mapNotNull { merged[it] }
    }

    LaunchedEffect(currentTab) {
        if (peekChatTarget != null) {
            peekChatTarget = null
        }
        if (selectedChatJids.isNotEmpty()) {
            selectedChatJids = emptySet()
            selectionOverflowExpanded = false
        }
        if (currentTab != 1 && updatesFabExpanded) {
            updatesFabExpanded = false
        }
    }

    BackHandler(enabled = peekChat != null) {
        peekChatTarget = null
    }

    BackHandler(enabled = selectedChatJids.isNotEmpty()) {
        selectedChatJids = emptySet()
        selectionOverflowExpanded = false
    }

    val backdropState = rememberWuppBackdropState()
    val peekBackgroundBlur by animateDpAsState(
        targetValue = if (peekChat != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !stealth.classicWhatsAppLook) {
            14.dp
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 180),
        label = "home_peek_blur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = if (peekBackgroundBlur > 0.dp) Modifier.blur(peekBackgroundBlur) else Modifier,
        containerColor = colors.appSurface,
        topBar = {
            val motionScheme = MaterialTheme.motionScheme
            Column(modifier = Modifier.background(colors.appSurface)) {
                AnimatedContent(
                    targetState = (currentTab == 0 || currentTab == 4) && selectedChatJids.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(motionScheme.fastEffectsSpec()) + scaleIn(
                            initialScale = 0.98f,
                            animationSpec = motionScheme.fastSpatialSpec()
                        )) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                    },
                    label = "home_topbar_mode"
                ) { inSelectionMode ->
                if (inSelectionMode) {
                    val allPinned = selectedChats.isNotEmpty() && selectedChats.all { it.isPinned }
                    val allMuted = selectedChats.isNotEmpty() && selectedChats.all { it.isMuted }
                    val allArchived = selectedChats.isNotEmpty() && selectedChats.all { it.isArchived }
                    val allFavorite = selectedChats.isNotEmpty() && selectedChats.all { it.isFavorite }
                    val anyUnread = selectedChats.any { it.unreadCount > 0 }
                    val targetJids = selectedChatJids.toList()

                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.elevatedBar,
                            titleContentColor = colors.textPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    selectedChatJids = emptySet()
                                    selectionOverflowExpanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Clear selection",
                                    tint = colors.textPrimary
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedChatJids.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    viewModel.pinChats(targetJids, !allPinned)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (allPinned) "Unpin" else "Pin",
                                    tint = if (allPinned) colors.brightGreen else colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = { pendingDeleteJids = targetJids }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete locally",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.muteChats(targetJids, !allMuted)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = if (allMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                    contentDescription = if (allMuted) "Unmute notifications" else "Mute notifications",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.archiveChats(targetJids, !allArchived)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = if (allArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                    contentDescription = if (allArchived) "Unarchive" else "Archive",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(onClick = { selectionOverflowExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, "More actions", tint = colors.textPrimary)
                            }
                            DropdownMenu(
                                expanded = selectionOverflowExpanded,
                                onDismissRequest = { selectionOverflowExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (anyUnread) "Mark as read" else "Mark as unread") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        viewModel.setChatsUnreadLocal(targetJids, !anyUnread)
                                        selectedChatJids = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allFavorite) "Remove from Favorites" else "Add to Favorites") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        viewModel.favoriteChats(targetJids, !allFavorite)
                                        selectedChatJids = emptySet()
                                    }
                                )
                                if (selectedChats.size == 1) {
                                    val only = selectedChats.first()
                                    DropdownMenuItem(
                                        text = { Text("View contact") },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            selectedChatJids = emptySet()
                                            quickContactTarget = only
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Select all") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        selectedChatJids = chats.map { it.jid }.toSet()
                                    }
                                )
                            }
                        }
                    )
                } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.appSurface,
                        titleContentColor = if (colors.isDark) colors.textPrimary else colors.accentGreen
                    ),
                    title = {
                        if (currentTab == 0) {
                            val statusColor by animateColorAsState(
                                targetValue = when (bridgeStatus.connectionState) {
                                    "CONNECTED" -> colors.brightGreen
                                    "CONNECTING", "PAIRING", "SYNCING" -> colors.warningAmber
                                    else -> colors.textSecondary.copy(alpha = 0.55f)
                                },
                                animationSpec = motionScheme.fastEffectsSpec(),
                                label = "top_bar_status_dot"
                            )
                            val statusWord = when (bridgeStatus.connectionState) {
                                "CONNECTED" -> "Live"
                                "SYNCING" -> "Syncing"
                                "CONNECTING", "PAIRING" -> "Linking"
                                else -> "Idle"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.wupp_logo),
                                    contentDescription = "Wupp",
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                                Text(
                                    text = "Wupp",
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                                if (!stealth.hideStealthStatusBadges) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(statusColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.5.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                        Text(
                                            text = statusWord,
                                            color = statusColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = when (currentTab) {
                                    4 -> stringResource(R.string.nav_groups)
                                    1 -> stringResource(R.string.nav_updates)
                                    2 -> stringResource(R.string.nav_communities)
                                    else -> stringResource(R.string.nav_settings)
                                },
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp
                            )
                        }
                    },
                    actions = {
                        if ((currentTab == 0 || currentTab == 4) && !stealth.hideHomeCameraShortcut) {
                            IconButton(onClick = { showPairingDialog = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_camera_reply),
                                    contentDescription = "Camera",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else if (currentTab == 1 || currentTab == 2) {
                            IconButton(onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) viewModel.updateHomeSearch("")
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_search),
                                    contentDescription = "Search",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "Menu", tint = colors.textPrimary)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat") },
                                onClick = {
                                    overflowExpanded = false
                                    showQuickContactPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Linked devices") },
                                leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                                onClick = {
                                    overflowExpanded = false
                                    showPairingDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Starred messages") },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.selectFilter(HomeFilterChip.FAVORITES)
                                    onSelectTab(0)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (includeArchived) stringResource(R.string.home_back_to_active_chats)
                                        else stringResource(R.string.home_archived_chats)
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.toggleArchivedIncluded()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_settings)) },
                                onClick = {
                                    overflowExpanded = false
                                    onSelectTab(3)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Check for updates") },
                                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.checkForUpdates(silent = false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report bug / Feedback") },
                                leadingIcon = { Icon(Icons.Rounded.BugReport, null) },
                                onClick = {
                                    overflowExpanded = false
                                    activeFeedbackType = GitHubFeedbackType.BUG
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Theme · ${stealth.themeMode.label}") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Palette, null)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    showThemePickerDialog = true
                                }
                            )
                        }
                    }
                )
                }
                }

                AnimatedVisibility(visible = searchExpanded && currentTab != 0 && currentTab != 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateHomeSearch(it) },
                        placeholder = { Text("Search...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateHomeSearch("") }) {
                                    Icon(Icons.Rounded.Close, "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        },
        bottomBar = {
            WuppGlassNavBar(
                currentTab = currentTab,
                unreadChatsCount = unreadChatsCount,
                onSelectTab = onSelectTab,
                backdropState = backdropState,
                classicMode = stealth.classicWhatsAppLook,
                separateGroupsAndChats = stealth.separateGroupsAndChats,
                unreadGroupsCount = unreadGroupsCount,
                chatsLabel = stringResource(R.string.nav_chats),
                groupsLabel = stringResource(R.string.nav_groups),
                updatesLabel = stringResource(R.string.nav_updates),
                communitiesLabel = stringResource(R.string.nav_communities),
                settingsLabel = stringResource(R.string.nav_settings)
            )
        },
        floatingActionButton = {
            when (currentTab) {
                0, 4 -> {
                    FloatingActionButton(
                        onClick = { showQuickContactPicker = true },
                        containerColor = colors.brightGreen,
                        contentColor = Color(0xFF0B141A),
                        shape = RoundedCornerShape(18.dp),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_chat_filled),
                            contentDescription = "New Chat",
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                1 -> {
                    FloatingActionButtonMenu(
                        expanded = updatesFabExpanded,
                        button = {
                            ToggleFloatingActionButton(
                                checked = updatesFabExpanded,
                                onCheckedChange = { updatesFabExpanded = it },
                                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                                    initialColor = colors.brightGreen,
                                    finalColor = colors.accentGreen
                                )
                            ) {
                                Icon(
                                    imageVector = if (updatesFabExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                                    contentDescription = if (updatesFabExpanded) "Close status actions" else "Status actions",
                                    tint = Color(0xFF0B141A)
                                )
                            }
                        }
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = {
                                updatesFabExpanded = false
                                statusComposerInitialMode = StatusComposerMode.TEXT
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null
                                )
                            },
                            text = { Text("Text status", fontWeight = FontWeight.SemiBold) },
                            containerColor = colors.elevatedBar,
                            contentColor = colors.textPrimary
                        )
                        FloatingActionButtonMenuItem(
                            onClick = {
                                updatesFabExpanded = false
                                statusComposerInitialMode = StatusComposerMode.MEDIA
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_camera_reply),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text("Camera status", fontWeight = FontWeight.SemiBold) },
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.pillActiveText
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentBottomPad = innerPadding.calculateBottomPadding() + 14.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .then(if (!stealth.classicWhatsAppLook) Modifier.wuppBackdropSource(backdropState) else Modifier)
                .background(colors.appSurface)
        ) {
            val tabOrder = remember(stealth.separateGroupsAndChats) {
                resolveHomeTabOrder(stealth.separateGroupsAndChats)
            }
            val pagerState = rememberPagerState(
                initialPage = resolvePagerIndexForTab(tabOrder, currentTab),
                pageCount = { tabOrder.size }
            )
            val latestTabOrder by rememberUpdatedState(tabOrder)
            val latestCurrentTab by rememberUpdatedState(currentTab)
            val latestOnSelectTab by rememberUpdatedState(onSelectTab)

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { settled ->
                        val resolvedTab = resolveTabIdForPagerIndex(latestTabOrder, settled)
                        if (resolvedTab != latestCurrentTab) {
                            latestOnSelectTab(resolvedTab)
                        }
                    }
            }

            LaunchedEffect(currentTab, tabOrder) {
                val targetPage = resolvePagerIndexForTab(tabOrder, currentTab)
                if (pagerState.currentPage != targetPage && pagerState.targetPage != targetPage) {
                    pagerState.animateScrollToPage(targetPage)
                }
            }

            val toggleSelection: (ChatEntity) -> Unit = { chat ->
                selectedChatJids = if (chat.jid in selectedChatJids) {
                    selectedChatJids - chat.jid
                } else {
                    selectedChatJids + chat.jid
                }
            }
            val handleAvatarClick: (ChatEntity) -> Unit = { chat ->
                if (selectedChatJids.isNotEmpty()) toggleSelection(chat) else quickContactTarget = chat
            }
            val handleChatClick: (ChatEntity) -> Unit = { chat ->
                if (selectedChatJids.isNotEmpty()) toggleSelection(chat) else onOpenChat(chat.jid)
            }
            val revealIfLockedFilter: (HomeFilterChip) -> Unit = { chip ->
                if (chip == HomeFilterChip.LOCKED &&
                    appLockConfig.hideLockedChatsBehindLock &&
                    !lockedChatsRevealed
                ) {
                    requestRevealLockedChats()
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = selectedChatJids.isEmpty() && peekChat == null,
                key = { pageIndex -> resolveTabIdForPagerIndex(tabOrder, pageIndex) },
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                when (resolveTabIdForPagerIndex(tabOrder, pageIndex)) {
                    0, 4 -> {
                        val tabIndex = resolveTabIdForPagerIndex(tabOrder, pageIndex)
                        val isGroupsTab = tabIndex == 4 && stealth.separateGroupsAndChats
                        val tabGroupFilterMode: Boolean? = when {
                            isGroupsTab -> true
                            stealth.separateGroupsAndChats -> false
                            else -> null
                        }
                        ChatsTabContent(
                            chats = if (isGroupsTab) groupsChats else chats,
                            allChats = allChats,
                            selectedChip = if (isGroupsTab) selectedGroupsChip else selectedChip,
                            ftsHits = ftsHits,
                            searchQuery = searchQuery,
                            listState = if (isGroupsTab) groupsListState else chatsListState,
                            selectedChatJids = selectedChatJids,
                            bottomPadding = contentBottomPad,
                            archivedCount = archivedCount,
                            includeArchived = includeArchived,
                            hiddenLockedCount = hiddenLockedCount,
                            hideLockedBehindLock = appLockConfig.hideLockedChatsBehindLock,
                            lockedChatsRevealed = lockedChatsRevealed,
                            compactRows = stealth.compactChatRows,
                            showSeconds = showSeconds,
                            hideStealthBadges = stealth.hideStealthStatusBadges,
                            groupFilterMode = tabGroupFilterMode,
                            onSearchChange = { viewModel.updateHomeSearch(it) },
                            heartbeat = heartbeat,
                            bridgeStatus = bridgeStatus,
                            onSelectChip = { chip ->
                                revealIfLockedFilter(chip)
                                if (isGroupsTab) viewModel.selectGroupsFilter(chip) else viewModel.selectFilter(chip)
                            },
                            onToggleIncludeArchived = { viewModel.setIncludeArchived(it) },
                            onRequestRevealLockedChats = { requestRevealLockedChats() },
                            onHideRevealedLockedChats = { viewModel.setLockedChatsRevealed(false) },
                            onResetHeartbeat = { viewModel.recordPrimaryHeartbeatNow() },
                            onSeedShowcase = { viewModel.seedShowcaseData() },
                            onAvatarClick = handleAvatarClick,
                            onChatClick = handleChatClick,
                            onChatLongClick = { chat ->
                                if (selectedChatJids.isNotEmpty()) {
                                    toggleSelection(chat)
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    peekChatTarget = chat
                                }
                            }
                        )
                    }

                    1 -> UpdatesStatusTab(
                        statuses = statuses,
                        channels = channels,
                        allChats = allChats,
                        ownJid = bridgeStatus.ownJid,
                        searchQuery = searchQuery,
                        listState = updatesListState,
                        bottomPadding = contentBottomPad,
                        verticalStatusLayout = stealth.statusVerticalLayout,
                        hideChannels = stealth.hideChannels,
                        showSeconds = showSeconds,
                        onOpenStatus = { activeStatusViewer = it },
                        onAddStatus = { mode -> statusComposerInitialMode = mode },
                        onOpenChannel = { onOpenChat(it) }
                    )

                    2 -> CommunitiesTab(
                        allChats = allChats,
                        listState = communitiesListState,
                        bottomPadding = contentBottomPad,
                        onOpenGroup = onOpenChat
                    )

                    else -> ToolsVaultTab(
                        viewModel = viewModel,
                        listState = toolsListState,
                        bottomPadding = contentBottomPad,
                        onOpenFeedback = { activeFeedbackType = it }
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = peekChat != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140))
    ) {
        lastPeekChat?.let { activePeek ->
            val recentPeekMessages by remember(activePeek.jid) {
                viewModel.observeRecentMessages(activePeek.jid, limit = 20)
            }.collectAsState(initial = emptyList())
            ChatPeekPopupOverlay(
                chat = activePeek,
                recentMessagesDescending = recentPeekMessages,
                showSeconds = showSeconds,
                backdropState = backdropState,
                classicMode = stealth.classicWhatsAppLook,
                onDismiss = { peekChatTarget = null },
                onOpenChat = {
                    val jid = activePeek.jid
                    peekChatTarget = null
                    onOpenChat(jid)
                },
                onToggleUnread = {
                    viewModel.setChatsUnreadLocal(listOf(activePeek.jid), activePeek.unreadCount == 0)
                },
                onTogglePin = { viewModel.togglePinChat(activePeek) },
                onToggleMute = { viewModel.muteChats(listOf(activePeek.jid), !activePeek.isMuted) },
                onToggleFavorite = { viewModel.toggleFavoriteChat(activePeek) },
                onToggleArchive = { viewModel.archiveChats(listOf(activePeek.jid), !activePeek.isArchived) },
                onSelectChat = { selectedChatJids = setOf(activePeek.jid) },
                onDeleteChat = { pendingDeleteJids = listOf(activePeek.jid) }
            )
        }
    }
    }

    quickContactChat?.let { target ->
        val cachedBio = remember(target.jid, target.aboutText, allContacts) {
            target.aboutText.takeIf { it.isNotBlank() }
                ?: allContacts.firstOrNull { it.jid == target.jid }?.aboutText.orEmpty()
        }
        QuickContactDialog(
            chat = target,
            aboutOverride = if (stealth.alwaysShowContactBio) cachedBio else "",
            onDismiss = { quickContactTarget = null },
            onOpenChat = { onOpenChat(target.jid) },
            onOpenGhostInfo = { onSelectTab(3) },
            onOpenPresenceLog = { onSelectTab(3) },
            onOpenAvatarHistory = { onSelectTab(3) },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(target.jid) },
            onSaveAvatar = {
                val safeName = target.name.ifBlank { target.jid.substringBefore("@") }
                viewModel.exportMediaToGallery(target.avatarPath, "${safeName}_avatar.jpg", "image/jpeg")
            }
        )
    }

    if (showLockedChatsPinPrompt) {
        var pinAttempt by remember { mutableStateOf("") }
        var pinErr by remember { mutableStateOf<String?>(null) }
        val wrongMsg = stringResource(R.string.applock_wrong_pin)
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showLockedChatsPinPrompt = false },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = stringResource(R.string.applock_prompt_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = { raw ->
                            pinErr = null
                            val clean = raw.filter { it.isDigit() }.take(8)
                            pinAttempt = clean
                            if (clean.length >= 4 && AppLockManager.verifyCustomPin(clean)) {
                                viewModel.setLockedChatsRevealed(true)
                                showLockedChatsPinPrompt = false
                            }
                        },
                        label = { Text(stringResource(R.string.applock_pin_enter_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pinErr.isNullOrBlank()) {
                        Text(pinErr.orEmpty(), color = colors.antiDeleteRed, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockedChatsPinPrompt = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (AppLockManager.verifyCustomPin(pinAttempt)) {
                            viewModel.setLockedChatsRevealed(true)
                            showLockedChatsPinPrompt = false
                        } else {
                            pinErr = wrongMsg
                            pinAttempt = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text(stringResource(R.string.applock_unlock_button), color = Color.White)
                }
            }
        )
    }

    if (showPairingDialog) {
        LinkCompanionDialog(
            bridgeStatus = bridgeStatus,
            onDismiss = { showPairingDialog = false },
            onPairPhone = { digits, cb -> viewModel.pairWithPhoneNumber(digits, cb) },
            onReconnect = { viewModel.reconnectSocket() }
        )
    }

    if (showQuickContactPicker) {
        NewChatPickerDialog(
            chats = allChats,
            contacts = allContacts,
            onDismiss = { showQuickContactPicker = false },
            onSelectChat = { jid ->
                showQuickContactPicker = false
                onOpenChat(jid)
            },
            onOpenLinkedDevices = {
                showQuickContactPicker = false
                showPairingDialog = true
            }
        )
    }

    if (showThemePickerDialog) {
        ThemePickerDialog(
            selectedTheme = stealth.themeMode,
            onSelectTheme = { viewModel.setThemeMode(it) },
            onDismiss = { showThemePickerDialog = false }
        )
    }

    statusComposerInitialMode?.let { mode ->
        StatusComposerSheet(
            initialMode = mode,
            onDismiss = { statusComposerInitialMode = null }
        )
    }

    activeStatusViewer?.let { st ->
        val myStatusLabel = stringResource(R.string.status_my_status)
        val statusGroups = remember(statuses, allChats, bridgeStatus.ownJid, myStatusLabel, st.id) {
            val (chatsByJid, chatsByUser) = buildStatusChatLookup(allChats)
            val (ownStatuses, contactStatuses) = partitionOwnAndContactStatuses(statuses, bridgeStatus.ownJid)
            val viewedOwn = isOwnStatusSender(st.senderJid, bridgeStatus.ownJid)

            fun toStatusGroup(senderJid: String, items: List<StatusEntity>, isOwn: Boolean): StatusGroup {
                val sortedItems = items.sortedBy { it.timestamp }
                val senderChat = resolveStatusSenderChat(senderJid, chatsByJid, chatsByUser)
                val firstItem = sortedItems.firstOrNull()
                return StatusGroup(
                    senderJid = senderJid,
                    senderName = if (isOwn) {
                        myStatusLabel
                    } else {
                        resolveStatusDisplayName(
                            senderJid = firstItem?.senderJid ?: senderJid,
                            storedName = firstItem?.senderName.orEmpty(),
                            senderChat = senderChat
                        )
                    },
                    senderAvatarPath = senderChat?.avatarPath,
                    items = sortedItems,
                    isOwn = isOwn
                )
            }

            if (viewedOwn) {
                val ownItems = ownStatuses.ifEmpty { listOf(st) }
                listOf(toStatusGroup(normalizeStatusSenderJid(st.senderJid), ownItems, isOwn = true))
            } else {
                val bySender = contactStatuses.groupBy { normalizeStatusSenderJid(it.senderJid) }
                val groupsList = bySender.entries.map { (senderJid, items) ->
                    toStatusGroup(senderJid, items, isOwn = false)
                }
                groupsList.ifEmpty {
                    val normSender = normalizeStatusSenderJid(st.senderJid)
                    listOf(toStatusGroup(normSender, listOf(st), isOwn = false))
                }
            }
        }
        val initialGroupIdx = remember(statusGroups, st.senderJid) {
            val targetSender = normalizeStatusSenderJid(st.senderJid)
            statusGroups.indexOfFirst { it.senderJid == targetSender || it.senderJid == st.senderJid }.coerceAtLeast(0)
        }
        val initialStoryIdx = remember(statusGroups, initialGroupIdx, st.id) {
            statusGroups.getOrNull(initialGroupIdx)?.items?.indexOfFirst { it.id == st.id }?.coerceAtLeast(0) ?: 0
        }
        FullScreenStatusViewer(
            groups = statusGroups,
            initialGroupIndex = initialGroupIdx,
            initialStoryIndex = initialStoryIdx,
            downloadingStatusIds = downloadingMediaIds,
            failedStatusIds = failedMediaIds,
            onRequestDownload = { target ->
                viewModel.requestStatusMediaDownload(target, userInitiated = true)
            },
            onDismiss = { activeStatusViewer = null },
            onSaveToGallery = { target ->
                val ext = if (target.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    target.localMediaPath,
                    "WA_Status_${target.id}.$ext",
                    target.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = { target ->
                if (target.senderJid.isNotBlank() && !isOwnStatusSender(target.senderJid, bridgeStatus.ownJid)) {
                    onOpenChat(target.senderJid)
                }
            },
            onAddStatus = { statusComposerInitialMode = StatusComposerMode.MEDIA }
        )
    }

    activeVaultViewer?.let { msg ->
        val chatName = remember(allChats, msg.chatJid, msg.senderName) {
            allChats.firstOrNull { it.jid == msg.chatJid }?.name ?: msg.senderName
        }
        FullScreenMediaViewer(
            msg = msg,
            senderDisplayName = chatName,
            onDismiss = { activeVaultViewer = null },
            onSaveToGallery = {
                val ext = if (msg.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    msg.localMediaPath,
                    "WA_ViewOnce_${msg.id}.$ext",
                    msg.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = {
                if (msg.chatJid.isNotBlank()) {
                    onOpenChat(msg.chatJid)
                }
            }
        )
    }

    activeFeedbackType?.let { fbType ->
        GitHubFeedbackDialog(
            initialType = fbType,
            onDismiss = { activeFeedbackType = null }
        )
    }

    if (pendingDeleteJids.isNotEmpty()) {
        val count = pendingDeleteJids.size
        val hasSyncableChats = pendingDeleteJids.any { !it.endsWith("@newsletter") && !it.endsWith("@broadcast") }
        var alsoDeleteOnWhatsApp by remember(pendingDeleteJids) { mutableStateOf(hasSyncableChats) }
        AlertDialog(
            onDismissRequest = { pendingDeleteJids = emptyList() },
            title = { Text(if (count == 1) "Delete this chat?" else "Delete $count chats?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (alsoDeleteOnWhatsApp && hasSyncableChats) {
                            "Deletes ${if (count == 1) "this chat" else "these chats"} from Wupp and syncs deletion to WhatsApp on your primary phone."
                        } else {
                            "Messages for ${if (count == 1) "this conversation" else "these conversations"} will be removed from Wupp's local cache only."
                        }
                    )
                    if (hasSyncableChats) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alsoDeleteOnWhatsApp = !alsoDeleteOnWhatsApp },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alsoDeleteOnWhatsApp,
                                onCheckedChange = { alsoDeleteOnWhatsApp = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Also delete on WhatsApp",
                                fontSize = 13.5.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChats(pendingDeleteJids, syncToWhatsApp = alsoDeleteOnWhatsApp && hasSyncableChats)
                        pendingDeleteJids = emptyList()
                        selectedChatJids = emptySet()
                    }
                ) {
                    Text("Delete", color = colors.antiDeleteRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteJids = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = LocalWaColors.current.pillActiveText,
    selectedTextColor = LocalWaColors.current.textPrimary,
    indicatorColor = LocalWaColors.current.pillActiveBg,
    unselectedIconColor = LocalWaColors.current.textSecondary,
    unselectedTextColor = LocalWaColors.current.textSecondary
)

@Composable
private fun rememberAvatarBitmap(jid: String, avatarPath: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(jid, avatarPath) {
        val explicitFile = avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
        }
        val resolvedFile = explicitFile ?: run {
            val rawUser = jid.substringBefore("@").substringBefore(":")
            val dir = File(context.filesDir, "vault/avatars")
            if (rawUser.isNotBlank() && dir.isDirectory) {
                val prefix = "${rawUser}_"
                dir.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith(prefix) && it.length() > 0L
                }
            } else {
                null
            }
        }
        resolvedFile?.let { file ->
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
}

@Composable
internal fun WhatsAppAvatar(
    jid: String,
    avatarPath: String?,
    isGroup: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    initialChar: String? = null
) {
    val avatarBmp = rememberAvatarBitmap(jid = jid, avatarPath = avatarPath)
    val palette = remember(jid, isGroup) {
        val pairs = listOf(
            Color(0xFF4A2E2B) to Color(0xFFF2998E), // Warm terracotta brown / peach (e.g. Mom)
            Color(0xFF1E3A5F) to Color(0xFF78A9FF), // Deep slate blue / sky blue (e.g. Talha)
            Color(0xFF3E2742) to Color(0xFFD7AEFB), // Plum / lavender
            Color(0xFF1B3B36) to Color(0xFF81C995), // Deep teal / sage
            Color(0xFF423824) to Color(0xFFFDD663)  // Charcoal olive / warm amber
        )
        pairs[abs(jid.substringBefore("@").hashCode()) % pairs.size]
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isGroup) Color(0xFF1F353D) else palette.first),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGroup) {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = Color(0xFF99B2BD),
                modifier = Modifier.size(size * 0.54f)
            )
        } else if (!initialChar.isNullOrBlank()) {
            Text(
                text = initialChar,
                color = palette.second,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = palette.second,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}

@Composable
private fun ChatsTabContent(
    chats: List<ChatEntity>,
    allChats: List<ChatEntity>,
    selectedChip: HomeFilterChip,
    ftsHits: List<MessageEntity>,
    searchQuery: String,
    listState: LazyListState,
    selectedChatJids: Set<String> = emptySet(),
    bottomPadding: Dp = 96.dp,
    archivedCount: Int = 0,
    includeArchived: Boolean = false,
    hiddenLockedCount: Int = 0,
    hideLockedBehindLock: Boolean = false,
    lockedChatsRevealed: Boolean = false,
    compactRows: Boolean = false,
    showSeconds: Boolean = false,
    hideStealthBadges: Boolean = false,
    groupFilterMode: Boolean? = null,
    onSearchChange: (String) -> Unit,
    heartbeat: com.wa.beastclient.domain.stealth.PrimaryHeartbeatStatus,
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onSelectChip: (HomeFilterChip) -> Unit,
    onToggleIncludeArchived: (Boolean) -> Unit = {},
    onRequestRevealLockedChats: () -> Unit = {},
    onHideRevealedLockedChats: () -> Unit = {},
    onResetHeartbeat: () -> Unit,
    onSeedShowcase: () -> Unit,
    onAvatarClick: (ChatEntity) -> Unit,
    onChatClick: (ChatEntity) -> Unit,
    onChatLongClick: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val scope = rememberCoroutineScope()
    var showExtraFilters by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val nonCommunityChats = remember(allChats, groupFilterMode) {
        allChats.filter {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
                (groupFilterMode == null || it.isGroup == groupFilterMode)
        }
    }
    val tabHiddenLockedCount = remember(nonCommunityChats, hideLockedBehindLock, lockedChatsRevealed, hiddenLockedCount) {
        if (!hideLockedBehindLock || lockedChatsRevealed) 0
        else nonCommunityChats.count { it.isLocked }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                scope.launch {
                    delay(700L)
                    isRefreshing = false
                }
            }
        },
        state = pullRefreshState,
        indicator = {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                containerColor = colors.pillActiveBg,
                color = colors.brightGreen,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // Inline search bar (Kyant0 Liquid Glass or flat classic WhatsApp look)
        item(key = "inline_search_bar") {
            val isClassicUi = com.wa.beastclient.ui.theme.LocalWuppClassicUi.current
            WuppGlassContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .height(48.dp),
                colors = colors,
                shape = CircleShape,
                elevation = 6.dp,
                backdropState = null,
                classicMode = isClassicUi,
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.vec_ic_search),
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.5.sp),
                        cursorBrush = SolidColor(colors.brightGreen),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_search_placeholder),
                                    color = colors.textSecondary,
                                    fontSize = 15.5.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 14-Day Primary Heartbeat countdown warning banner (>= 10 days elapsed)
        if (heartbeat.isWarningActive) {
            item(key = "heartbeat_warning") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.warningAmber.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = colors.warningAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "14-Day Primary Heartbeat Alert",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "${heartbeat.elapsedDays}d elapsed · ${heartbeat.remainingDays}d (${heartbeat.remainingHours}h) before Meta unlinks companions. Open official WhatsApp on primary device.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onResetHeartbeat) {
                            Text("Reset", color = colors.accentGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Filter Chips Row (All / Unread N / Favorites / Groups N / +)
        item(key = "filter_chips") {
            val hasLockedChats = remember(nonCommunityChats, tabHiddenLockedCount) {
                tabHiddenLockedCount > 0 || nonCommunityChats.any { it.isLocked }
            }
            val visibleChips = remember(showExtraFilters, hasLockedChats, groupFilterMode) {
                buildList {
                    add(HomeFilterChip.ALL)
                    add(HomeFilterChip.UNREAD)
                    add(HomeFilterChip.FAVORITES)
                    if (groupFilterMode == null) add(HomeFilterChip.GROUPS)
                    if (hasLockedChats) add(HomeFilterChip.LOCKED)
                    if (showExtraFilters) {
                        add(HomeFilterChip.DELETED)
                        add(HomeFilterChip.EDITED)
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleChips) { chip ->
                    val count = when (chip) {
                        HomeFilterChip.ALL -> nonCommunityChats.size
                        HomeFilterChip.UNREAD -> nonCommunityChats.count { it.unreadCount > 0 }
                        HomeFilterChip.FAVORITES -> nonCommunityChats.count { it.isFavorite }
                        HomeFilterChip.GROUPS -> nonCommunityChats.count { it.isGroup }
                        HomeFilterChip.LOCKED -> maxOf(tabHiddenLockedCount, nonCommunityChats.count { it.isLocked })
                        HomeFilterChip.DELETED -> nonCommunityChats.count { it.hasDeletedMessages || it.lastMessageIsDeleted }
                        HomeFilterChip.EDITED -> nonCommunityChats.count { it.hasEditedMessages }
                    }
                    val active = chip == selectedChip
                    val isClassicUi = com.wa.beastclient.ui.theme.LocalWuppClassicUi.current
                    val chipRadius by animateDpAsState(
                        targetValue = if (isClassicUi) 20.dp else if (active) 12.dp else 20.dp,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "filter_chip_radius_${chip.name}"
                    )
                    val chipBg by animateColorAsState(
                        targetValue = if (active) colors.pillActiveBg else Color.Transparent,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "filter_chip_bg_${chip.name}"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (active) {
                            colors.pillActiveText.copy(alpha = 0.42f)
                        } else {
                            colors.textSecondary.copy(alpha = 0.28f)
                        },
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "filter_chip_border_${chip.name}"
                    )
                    val chipShape = RoundedCornerShape(chipRadius)
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(chipBg)
                            .border(1.dp, borderColor, chipShape)
                            .clickable { onSelectChip(chip) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        val baseLabel = when (chip) {
                            HomeFilterChip.ALL -> stringResource(R.string.home_filter_all)
                            HomeFilterChip.UNREAD -> stringResource(R.string.home_filter_unread)
                            HomeFilterChip.FAVORITES -> stringResource(R.string.home_filter_pinned)
                            HomeFilterChip.GROUPS -> stringResource(R.string.home_filter_groups)
                            HomeFilterChip.LOCKED -> stringResource(R.string.home_filter_locked)
                            HomeFilterChip.DELETED -> chip.label
                            HomeFilterChip.EDITED -> chip.label
                        }
                        val chipLabel = when (chip) {
                            HomeFilterChip.UNREAD -> if (count > 0) "$baseLabel $count" else baseLabel
                            HomeFilterChip.GROUPS -> if (count > 0) "$baseLabel $count" else baseLabel
                            HomeFilterChip.LOCKED -> if (count > 0) "$baseLabel $count" else baseLabel
                            else -> baseLabel
                        }
                        Text(
                            text = chipLabel,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) colors.pillActiveText else colors.textSecondary
                        )
                    }
                }
                item(key = "filter_plus_chip") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (showExtraFilters) colors.pillActiveBg else Color.Transparent)
                            .border(
                                1.dp,
                                if (showExtraFilters) colors.pillActiveText.copy(alpha = 0.35f) else colors.textSecondary.copy(alpha = 0.28f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { showExtraFilters = !showExtraFilters }
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showExtraFilters) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = "More filters",
                            tint = if (showExtraFilters) colors.pillActiveText else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (archivedCount > 0 || includeArchived) {
            item(key = "archived_banner_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleIncludeArchived(!includeArchived) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (includeArchived) colors.accentGreen.copy(alpha = 0.18f)
                                else colors.elevatedBar
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (includeArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = if (includeArchived) {
                            stringResource(R.string.home_back_to_active_chats)
                        } else {
                            stringResource(R.string.home_archived_chats)
                        },
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (archivedCount > 0) {
                        Text(
                            text = archivedCount.toString(),
                            color = colors.accentGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono
                        )
                    }
                }
            }
        }

        if (tabHiddenLockedCount > 0) {
            item(key = "locked_chats_hidden_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRequestRevealLockedChats)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accentGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_locked_chats_banner, tabHiddenLockedCount),
                            color = colors.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.home_locked_chats_unlock_action),
                            color = colors.accentGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (hideLockedBehindLock && lockedChatsRevealed && nonCommunityChats.any { it.isLocked }) {
            item(key = "locked_chats_revealed_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onHideRevealedLockedChats)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_locked_chats_unlocked_banner),
                        color = colors.accentGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FTS4 Search Matches when typing search query
        if (searchQuery.trim().length >= 2 && ftsHits.isNotEmpty()) {
            item(key = "fts_header") {
                Text(
                    text = "FTS4 Message Matches (${ftsHits.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(ftsHits.take(6), key = { "fts_${it.id}" }) { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatClick(ChatEntity(jid = hit.chatJid, name = hit.senderName)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hit.senderName.ifBlank { hit.chatJid.substringBefore("@") },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = hit.body.ifBlank { hit.audioTranscript.orEmpty() },
                            color = colors.textSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatMessageClock(hit.timestamp, includeSeconds = showSeconds),
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
            item { HorizontalDivider(color = colors.divider) }
        }

        // Empty state
        if (chats.isEmpty()) {
            item(key = "empty_state") {
                val syncingNow = bridgeStatus.connectionState == "SYNCING" || bridgeStatus.connectionState == "CONNECTING"
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.elevatedBar.copy(alpha = if (colors.isDark) 0.72f else 0.94f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (syncingNow) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            ContainedLoadingIndicator(
                                containerColor = colors.pillActiveBg,
                                indicatorColor = colors.brightGreen,
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            ExpressiveIconBadge(
                                icon = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                active = true,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                syncingNow -> "Syncing your conversations..."
                                bridgeStatus.connectionState == "CONNECTED" -> "No chats yet"
                                else -> "Start chatting on Wupp"
                            },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your synced conversations will appear here automatically.",
                            color = colors.textSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        } else {
            items(chats, key = { it.jid }) { chat ->
                ChatListRow(
                    chat = chat,
                    selected = chat.jid in selectedChatJids,
                    compactRows = compactRows,
                    showSeconds = showSeconds,
                    hideStealthBadges = hideStealthBadges,
                    onAvatarClick = { onAvatarClick(chat) },
                    onRowClick = { onChatClick(chat) },
                    onRowLongClick = { onChatLongClick(chat) }
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    chat: ChatEntity,
    selected: Boolean = false,
    compactRows: Boolean = false,
    showSeconds: Boolean = false,
    hideStealthBadges: Boolean = false,
    onAvatarClick: () -> Unit,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val rowInteraction = remember { MutableInteractionSource() }
    val rowBg by animateColorAsState(
        targetValue = if (selected) colors.brightGreen.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chat_row_selection_bg"
    )
    val rowRadius by animateDpAsState(
        targetValue = if (selected) 20.dp else 14.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chat_row_radius"
    )
    val avatarSize = if (compactRows) 46.dp else 52.dp
    val rowHeight = if (compactRows) 64.dp else 76.dp
    val rowVerticalPad = if (compactRows) 6.dp else 10.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressivePressScale(rowInteraction, pressedScale = 0.985f)
            .padding(horizontal = if (selected) 8.dp else 0.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(rowRadius))
            .height(rowHeight)
            .background(rowBg)
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        colors.brightGreen.copy(alpha = 0.35f),
                        RoundedCornerShape(rowRadius)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(horizontal = if (selected) 10.dp else 14.dp, vertical = rowVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .combinedClickable(
                    onClick = onAvatarClick,
                    onLongClick = onRowLongClick
                )
        ) {
            WhatsAppAvatar(
                jid = chat.jid,
                avatarPath = chat.avatarPath,
                isGroup = chat.isGroup,
                size = avatarSize
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF0B141A),
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (chat.isOnline && !hideStealthBadges) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compactRows) 1.5.dp else 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name.ifBlank { chat.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compactRows) 15.sp else 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMessageClock(chat.lastMessageTimestamp, includeSeconds = showSeconds),
                    fontFamily = mono,
                    fontSize = 11.5.sp,
                    color = if (chat.unreadCount > 0) colors.brightGreen else colors.textSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isViewOncePreview = chat.lastMessageType.startsWith("VIEW_ONCE") ||
                    chat.lastMessagePreview.startsWith("View once")

                if (chat.chatPresence.isBlank()) {
                    if (chat.lastMessageIsDeleted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_delete_sweep),
                            contentDescription = null,
                            tint = colors.antiDeleteRed,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    } else if (chat.lastMessageFromMe) {
                        DeliveryTickIcon(
                            status = chat.lastMessageStatus,
                            mutedColor = colors.textSecondary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    if (!chat.lastMessageIsDeleted && isViewOncePreview) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_view_once),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(15.dp)
                        )
                    }
                }

                val fallbackPreview = when (chat.lastMessageType) {
                    "PTT" -> "🎤 Voice message"
                    "AUDIO" -> "🎵 Audio"
                    "IMAGE" -> "📷 Photo"
                    "VIDEO" -> "🎥 Video"
                    "STICKER" -> "💟 Sticker"
                    "DOCUMENT" -> "📄 Document"
                    else -> ""
                }
                val subtitle = when {
                    chat.chatPresence.isNotBlank() -> chat.chatPresence
                    isViewOncePreview && !chat.lastMessageIsDeleted -> when {
                        chat.lastMessagePreview.startsWith("Opened") -> chat.lastMessagePreview
                        chat.lastMessageType.endsWith("VIDEO") -> "Video"
                        chat.lastMessageType.endsWith("AUDIO") || chat.lastMessageType.endsWith("PTT") -> "Voice message"
                        else -> "Photo"
                    }
                    chat.lastMessagePreview.isNotBlank() -> chat.lastMessagePreview
                    else -> fallbackPreview
                }
                Text(
                    text = subtitle,
                    color = when {
                        chat.chatPresence.isNotBlank() -> colors.brightGreen
                        chat.lastMessageIsDeleted -> colors.antiDeleteRed
                        else -> colors.textSecondary
                    },
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chat.isLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = colors.accentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.isArchived) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "Archived",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.isMuted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_mute_list),
                            contentDescription = "Muted",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .animateContentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.brightGreen)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                                color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesStatusTab(
    statuses: List<StatusEntity>,
    channels: List<ChatEntity>,
    allChats: List<ChatEntity>,
    ownJid: String = "",
    searchQuery: String,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    verticalStatusLayout: Boolean = false,
    hideChannels: Boolean = false,
    showSeconds: Boolean = false,
    onOpenStatus: (StatusEntity) -> Unit,
    onAddStatus: (StatusComposerMode) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val colors = LocalWaColors.current
    val (chatsByJid, chatsByUser) = remember(allChats) { buildStatusChatLookup(allChats) }
    val (ownStatuses, contactStatuses) = remember(statuses, ownJid) {
        partitionOwnAndContactStatuses(statuses, ownJid)
    }
    val latestOwnStatus = remember(ownStatuses) { ownStatuses.maxByOrNull { it.timestamp } }
    val ownAvatarPath = remember(ownJid, chatsByJid, chatsByUser) {
        if (ownJid.isBlank()) null else resolveStatusSenderChat(ownJid, chatsByJid, chatsByUser)?.avatarPath
    }
    val groupedStatuses = remember(contactStatuses, allChats, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val grouped = contactStatuses.groupBy { normalizeStatusSenderJid(it.senderJid) }.values.map { it.first() }
        if (q.isEmpty()) grouped
        else grouped.filter { st ->
            val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
            val name = resolveStatusDisplayName(st.senderJid, st.senderName, senderChat)
            name.lowercase().contains(q) || st.caption.lowercase().contains(q)
        }
    }
    val filteredChannels = remember(channels, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) channels
        else channels.filter {
            it.name.lowercase().contains(q) ||
                it.lastMessagePreview.lowercase().contains(q) ||
                it.aboutText.lowercase().contains(q)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item(key = "updates_status_header") {
            Text(
                text = "Status",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)
            )
        }

        if (verticalStatusLayout) {
            item(key = "updates_my_status_vertical") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (latestOwnStatus != null) {
                                onOpenStatus(latestOwnStatus)
                            } else {
                                onAddStatus(StatusComposerMode.MEDIA)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(50.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (latestOwnStatus != null) {
                                        Modifier
                                            .border(2.dp, colors.brightGreen, CircleShape)
                                            .padding(2.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(Color(0xFF4A2531)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ownJid.isNotBlank() && !ownAvatarPath.isNullOrBlank()) {
                                WhatsAppAvatar(
                                    jid = ownJid,
                                    avatarPath = ownAvatarPath,
                                    isGroup = false,
                                    size = 44.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFF48FB1),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .border(2.dp, colors.appSurface, CircleShape)
                                .clickable { onAddStatus(StatusComposerMode.MEDIA) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add status",
                                tint = Color(0xFF0B141A),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.status_my_status),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp
                        )
                        Text(
                            text = if (latestOwnStatus != null) {
                                buildString {
                                    append(formatMessageClock(latestOwnStatus.timestamp, includeSeconds = showSeconds))
                                    if (ownStatuses.size > 1) {
                                        append(" · ${ownStatuses.size}")
                                    }
                                }
                            } else {
                                stringResource(R.string.status_add_update)
                            },
                            color = colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
            if (groupedStatuses.isEmpty()) {
                item(key = "updates_status_empty_vertical") {
                    Text(
                        text = stringResource(R.string.home_empty_updates),
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                items(groupedStatuses, key = { "status_vert_${it.id}" }) { st ->
                    val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
                    val displayName = resolveStatusDisplayName(st.senderJid, st.senderName, senderChat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStatus(st) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, colors.brightGreen, CircleShape)
                                .padding(3.dp)
                        ) {
                            WhatsAppAvatar(
                                jid = senderChat?.jid ?: st.senderJid,
                                avatarPath = senderChat?.avatarPath,
                                isGroup = false,
                                size = 46.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(formatMessageClock(st.timestamp, includeSeconds = showSeconds))
                                    if (st.caption.isNotBlank()) {
                                        append(" · ")
                                        append(st.caption)
                                    }
                                },
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "updates_status_cards_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "add_status_card") {
                        AddStatusStoryCard(
                            latestOwnStatus = latestOwnStatus,
                            ownJid = ownJid,
                            ownAvatarPath = ownAvatarPath,
                            onOpenOwnStatus = { latestOwnStatus?.let(onOpenStatus) },
                            onAddStatus = { onAddStatus(StatusComposerMode.MEDIA) }
                        )
                    }
                    items(groupedStatuses, key = { "status_card_${it.id}" }) { st ->
                        val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
                        StatusStoryCard(
                            status = st,
                            senderChat = senderChat,
                            onClick = { onOpenStatus(st) }
                        )
                    }
                }
            }
        }

        if (!hideChannels) {
            item(key = "updates_channels_header") {
                Text(
                    text = "Channels",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 6.dp)
                )
            }

            if (filteredChannels.isEmpty()) {
                item(key = "updates_channels_empty") {
                    Text(
                        text = "Subscribed channels will appear here once synced.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            } else {
                items(filteredChannels, key = { "channel_${it.jid}" }) { ch ->
                    ChannelListRow(
                        channel = ch,
                        onClick = { onOpenChannel(ch.jid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStatusStoryCard(
    latestOwnStatus: StatusEntity?,
    ownJid: String,
    ownAvatarPath: String?,
    onOpenOwnStatus: () -> Unit,
    onAddStatus: () -> Unit
) {
    val colors = LocalWaColors.current
    val ownMediaBmp = remember(latestOwnStatus?.localMediaPath) {
        latestOwnStatus?.localMediaPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (latestOwnStatus != null) Color(0xFF1A3438) else colors.elevatedBar)
            .border(
                width = if (latestOwnStatus != null) 1.dp else 0.5.dp,
                color = if (latestOwnStatus != null) colors.brightGreen.copy(alpha = 0.65f) else colors.textSecondary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                if (latestOwnStatus != null) onOpenOwnStatus() else onAddStatus()
            }
    ) {
        if (ownMediaBmp != null) {
            Image(
                bitmap = ownMediaBmp,
                contentDescription = stringResource(R.string.status_my_status),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
        } else if (latestOwnStatus != null && latestOwnStatus.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = latestOwnStatus.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = if (latestOwnStatus != null) {
                Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .align(Alignment.TopStart)
            } else {
                Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            }
        ) {
            val ringSize = if (latestOwnStatus != null) 38.dp else 54.dp
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .clip(CircleShape)
                    .then(
                        if (latestOwnStatus != null) {
                            Modifier
                                .border(2.dp, colors.brightGreen, CircleShape)
                                .padding(2.dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(Color(0xFF4A2531)),
                contentAlignment = Alignment.Center
            ) {
                if (ownJid.isNotBlank() && !ownAvatarPath.isNullOrBlank()) {
                    WhatsAppAvatar(
                        jid = ownJid,
                        avatarPath = ownAvatarPath,
                        isGroup = false,
                        size = ringSize - 4.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color(0xFFF48FB1),
                        modifier = Modifier.size(if (latestOwnStatus != null) 22.dp else 32.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(if (latestOwnStatus != null) 18.dp else 22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(colors.brightGreen)
                    .border(2.dp, colors.elevatedBar, CircleShape)
                    .clickable(onClick = onAddStatus),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add status",
                    tint = Color(0xFF0B141A),
                    modifier = Modifier.size(if (latestOwnStatus != null) 11.dp else 14.dp)
                )
            }
        }
        Text(
            text = if (latestOwnStatus != null) stringResource(R.string.status_my_status) else "Add status",
            color = if (latestOwnStatus != null) Color.White else colors.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 8.dp, end = 8.dp)
        )
    }
}

private fun normalizeStatusSenderJid(rawJid: String): String {
    val trimmed = rawJid.trim()
    val atIdx = trimmed.indexOf('@')
    if (atIdx <= 0) return trimmed
    val user = trimmed.substring(0, atIdx).substringBefore(':')
    val server = trimmed.substring(atIdx + 1)
    return if (user.isNotBlank() && server.isNotBlank()) "$user@$server" else trimmed
}

private fun isPhoneLikeDisplayName(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    var hasDigit = false
    for (ch in trimmed) {
        when {
            ch.isDigit() -> hasDigit = true
            ch == '+' || ch == '-' || ch == ' ' || ch == '(' || ch == ')' -> Unit
            else -> return false
        }
    }
    return hasDigit
}

private fun buildStatusChatLookup(
    allChats: List<ChatEntity>
): Pair<Map<String, ChatEntity>, Map<String, ChatEntity>> {
    val byJid = LinkedHashMap<String, ChatEntity>(allChats.size * 2)
    val byUser = LinkedHashMap<String, ChatEntity>(allChats.size)
    for (chat in allChats) {
        byJid.putIfAbsent(chat.jid, chat)
        val norm = normalizeStatusSenderJid(chat.jid)
        if (norm.isNotBlank()) byJid.putIfAbsent(norm, chat)
        val user = norm.substringBefore("@").trim()
        if (user.isNotBlank() && !chat.isGroup && !chat.jid.endsWith("@newsletter")) {
            val prev = byUser[user]
            if (prev == null || (isPhoneLikeDisplayName(prev.name) && !isPhoneLikeDisplayName(chat.name))) {
                byUser[user] = chat
            }
        }
    }
    return byJid to byUser
}

private fun resolveStatusSenderChat(
    senderJid: String,
    chatsByJid: Map<String, ChatEntity>,
    chatsByUser: Map<String, ChatEntity>
): ChatEntity? {
    val norm = normalizeStatusSenderJid(senderJid)
    return chatsByJid[senderJid]
        ?: chatsByJid[norm]
        ?: chatsByUser[norm.substringBefore("@").trim()]
}

private fun resolveStatusDisplayName(
    senderJid: String,
    storedName: String,
    senderChat: ChatEntity?
): String {
    val chatName = senderChat?.name?.trim().orEmpty()
    val norm = normalizeStatusSenderJid(senderJid)
    val user = norm.substringBefore("@")
    val chatUseful = chatName.isNotBlank() && chatName != senderJid && chatName != norm && chatName != user
    val storedClean = storedName.trim()
    val storedUseful = storedClean.isNotBlank() &&
        storedClean != "Status" &&
        storedClean != senderJid &&
        storedClean != norm &&
        storedClean != user
    return when {
        chatUseful && !isPhoneLikeDisplayName(chatName) -> chatName
        storedUseful && !isPhoneLikeDisplayName(storedClean) -> storedClean
        chatUseful -> chatName
        storedUseful -> storedClean
        else -> user.ifBlank { "Status" }
    }
}

@Composable
private fun StatusStoryCard(
    status: StatusEntity,
    senderChat: ChatEntity?,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val mediaBmp = remember(status.localMediaPath) {
        status.localMediaPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    val displayName = resolveStatusDisplayName(status.senderJid, status.senderName, senderChat)

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A3438))
            .clickable(onClick = onClick)
    ) {
        if (mediaBmp != null) {
            Image(
                bitmap = mediaBmp,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (status.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .padding(8.dp)
                .size(40.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (status.isRevoked) colors.antiDeleteRed else colors.brightGreen,
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            WhatsAppAvatar(
                jid = status.senderJid,
                avatarPath = senderChat?.avatarPath,
                isGroup = false,
                size = 34.dp
            )
        }

        Text(
            text = displayName,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ChannelListRow(
    channel: ChatEntity,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhatsAppAvatar(
            jid = channel.jid,
            avatarPath = channel.avatarPath,
            isGroup = false,
            size = 52.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.name.ifBlank { channel.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (channel.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(channel.lastMessageTimestamp, includeSeconds = LocalWuppShowSeconds.current),
                        fontFamily = mono,
                        fontSize = 11.5.sp,
                        color = if (channel.unreadCount > 0) colors.brightGreen else colors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.lastMessagePreview.ifBlank {
                        channel.aboutText.ifBlank { "Channel updates" }
                    },
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (channel.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (channel.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (channel.unreadCount > 999) "999+" else channel.unreadCount.toString(),
                                color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(
    allChats: List<ChatEntity>,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    onOpenGroup: (String) -> Unit
) {
    val colors = LocalWaColors.current
    var expandedCommunities by remember { mutableStateOf(setOf<String>()) }

    val groups = remember(allChats) { allChats.filter { it.isGroup } }
    val parentByJid = remember(groups) {
        groups.filter { it.isCommunityParent }.associateBy { it.jid }
    }
    val childrenByParent = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isNotBlank() }
            .groupBy { it.parentGroupJid }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareByDescending<ChatEntity> { it.isDefaultSubgroup }
                        .thenByDescending { it.lastMessageTimestamp }
                        .thenBy { it.name.lowercase() }
                )
            }
    }
    val communityJids = remember(parentByJid, childrenByParent) {
        (parentByJid.keys + childrenByParent.keys).distinct().sortedByDescending { jid ->
            val parentTs = parentByJid[jid]?.lastMessageTimestamp ?: 0L
            val childTs = childrenByParent[jid]?.maxOfOrNull { it.lastMessageTimestamp } ?: 0L
            maxOf(parentTs, childTs)
        }
    }
    val standaloneGroups = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isBlank() }
            .sortedWith(
                compareByDescending<ChatEntity> { it.lastMessageTimestamp }
                    .thenBy { it.name.lowercase() }
            )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 8.dp)
    ) {
        item(key = "new_community_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.textSecondary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = "New community",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.accentGreen)
                            .border(1.5.dp, colors.appSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "New community",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(colors.elevatedBar.copy(alpha = 0.45f))
            )
        }

        if (communityJids.isEmpty()) {
            item(key = "communities_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accentGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Stay connected with a community",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Communities bring members together in topic-based groups, and make it easy to get admin announcements.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        communityJids.forEach { parentJid ->
            val parentChat = parentByJid[parentJid]
            val children = childrenByParent[parentJid].orEmpty()
            val isExpanded = parentJid in expandedCommunities
            val visibleChildren = if (isExpanded || children.size <= 3) children else children.take(3)
            val communityTitle = parentChat?.name?.takeIf {
                it.isNotBlank() && it != parentJid && it != parentJid.substringBefore("@")
            } ?: "Community"

            item(key = "comm_header_$parentJid") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val announcement = children.firstOrNull { it.isDefaultSubgroup } ?: children.firstOrNull()
                            if (announcement != null) {
                                onOpenGroup(announcement.jid)
                            } else if (parentChat != null) {
                                onOpenGroup(parentChat.jid)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommunityAvatarBox(
                        avatarPath = parentChat?.avatarPath,
                        isRoundedSquare = true,
                        isAnnouncement = false,
                        sizeDp = 46
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = communityTitle,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val topic = parentChat?.aboutText.orEmpty()
                        if (topic.isNotBlank()) {
                            Text(
                                text = topic,
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.textSecondary.copy(alpha = 0.14f)
                )
            }

            items(visibleChildren, key = { "comm_${parentJid}_sub_${it.jid}" }) { sub ->
                CommunityGroupRow(
                    chat = sub,
                    onClick = { onOpenGroup(sub.jid) }
                )
            }

            if (children.size > 3) {
                item(key = "comm_more_$parentJid") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCommunities = if (isExpanded) {
                                    expandedCommunities - parentJid
                                } else {
                                    expandedCommunities + parentJid
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = if (isExpanded) "Show less" else "View all (${children.size})",
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }

            item(key = "comm_sep_$parentJid") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(colors.elevatedBar.copy(alpha = 0.45f))
                )
            }
        }

        if (standaloneGroups.isNotEmpty()) {
            item(key = "standalone_groups_header") {
                Text(
                    text = "Groups",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            items(standaloneGroups, key = { "standalone_${it.jid}" }) { g ->
                CommunityGroupRow(
                    chat = g,
                    onClick = { onOpenGroup(g.jid) }
                )
            }
        }
    }
}

@Composable
private fun CommunityGroupRow(
    chat: ChatEntity,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val title = if (chat.isDefaultSubgroup && (chat.name.isBlank() || chat.name == chat.jid.substringBefore("@"))) {
        "Announcements"
    } else {
        chat.name.ifBlank { chat.jid.substringBefore("@") }
    }
    val subtitle = chat.lastMessagePreview.ifBlank {
        chat.aboutText.ifBlank { if (chat.isDefaultSubgroup) "Announcements" else "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityAvatarBox(
            avatarPath = chat.avatarPath,
            isRoundedSquare = chat.isDefaultSubgroup,
            isAnnouncement = chat.isDefaultSubgroup,
            sizeDp = 44
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(chat.lastMessageTimestamp, includeSeconds = LocalWuppShowSeconds.current),
                        color = if (chat.unreadCount > 0) colors.accentGreen else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (subtitle.isNotBlank() || chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.accentGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityAvatarBox(
    avatarPath: String?,
    isRoundedSquare: Boolean,
    isAnnouncement: Boolean,
    sizeDp: Int
) {
    val colors = LocalWaColors.current
    val shape = if (isRoundedSquare) RoundedCornerShape(12.dp) else CircleShape
    val avatarBmp = remember(avatarPath) {
        avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(colors.accentGreen.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null && !isAnnouncement) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isAnnouncement) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.54f).dp)
            )
        }
    }
}

@Composable
private fun ToolsVaultTab(
    viewModel: MainViewModel,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    onOpenFeedback: (GitHubFeedbackType) -> Unit
) {
    val context = LocalContext.current
    val tabScope = rememberCoroutineScope()
    val stealth by viewModel.stealthConfig.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    val includePreReleases by viewModel.includePreReleases.collectAsState()
    var ownProfile by remember { mutableStateOf(OwnProfileUi()) }
    var showProfileEditorDialog by remember { mutableStateOf(false) }
    var showAccountPrivacyDialog by remember { mutableStateOf(false) }
    var showBlockedContactsDialog by remember { mutableStateOf(false) }
    var showCompanionScopeDialog by remember { mutableStateOf(false) }
    var showStorageUsageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var editingAutoDlNetwork by remember { mutableStateOf<AutoDownloadNetwork?>(null) }
    var showGalleryContactsDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showReactionDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHideNotifGuide by remember { mutableStateOf(false) }
    var isSyncNotifHidden by remember {
        mutableStateOf(WaRealtimeService.isRealtimeStatusNotificationHidden(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(WaRealtimeService.isIgnoringBatteryOptimizations(context))
    }

    LaunchedEffect(bridgeStatus.isLoggedIn, bridgeStatus.connectionState) {
        viewModel.fetchOwnProfile().onSuccess { ownProfile = it }
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isSyncNotifHidden = WaRealtimeService.isRealtimeStatusNotificationHidden(context)
                isBatteryUnrestricted = WaRealtimeService.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 14.dp,
            end = 16.dp,
            bottom = bottomPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "patch_section_profile_card") {
            val ownAvatarPath = remember(bridgeStatus.ownJid, allChats) {
                val ownUser = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
                allChats.firstOrNull { ch ->
                    !ch.avatarPath.isNullOrBlank() &&
                        ownUser.isNotBlank() &&
                        ch.jid.substringBefore("@").substringBefore(":") == ownUser
                }?.avatarPath
            }
            SettingsProfileHeaderCard(
                ownJid = bridgeStatus.ownJid,
                profile = ownProfile,
                avatarPath = ownAvatarPath,
                onClick = { showProfileEditorDialog = true }
            )
        }

        item(key = "patch_section_account_privacy") {
            ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_account_privacy)) {
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    title = stringResource(R.string.settings_wa_privacy_title),
                    subtitle = stringResource(R.string.settings_wa_privacy_subtitle),
                    position = ExpressiveSegmentPosition.TOP,
                    onClick = { showAccountPrivacyDialog = true }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Block,
                    title = stringResource(R.string.settings_blocked_contacts_title),
                    subtitle = stringResource(R.string.settings_blocked_contacts_subtitle),
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { showBlockedContactsDialog = true }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_account_limits_title),
                    subtitle = stringResource(R.string.settings_account_limits_subtitle),
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onClick = { showCompanionScopeDialog = true }
                )
            }
        }

        item(key = "patch_section_privacy") {
            ExpressiveSegmentedGroup(title = "Privacy & Ghost Mode") {
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Ghost Mode (Freeze Last Seen)",
                    subtitle = "Hide online status and freeze your last seen timestamp",
                    checked = stealth.ghostPresence,
                    position = ExpressiveSegmentPosition.TOP,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(ghostPresence = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Hide Read Receipts",
                    subtitle = "Read messages privately without sending blue ticks",
                    checked = stealth.hideReadReceipts,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideReadReceipts = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Campaign,
                    title = "Hide Played Receipts",
                    subtitle = "Play voice notes and audio without marking them played",
                    checked = stealth.hidePlayedReceipts,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hidePlayedReceipts = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Shield,
                    title = "Hide Typing & Recording",
                    subtitle = "Suppress typing and recording audio indicators in chats",
                    checked = stealth.hideTypingRecording,
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideTypingRecording = it) }
                    }
                )
            }
        }

        item(key = "patch_section_media") {
            ExpressiveSegmentedGroup(title = "Message & Media Patches") {
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Anti-Delete Messages & Statuses",
                    subtitle = "Keep messages, media, and statuses deleted by everyone",
                    checked = stealth.antiDeleteEnabled,
                    position = ExpressiveSegmentPosition.TOP,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(antiDeleteEnabled = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Lock,
                    title = "Disappearing Message Immunity",
                    subtitle = "Prevent ephemeral timers from expiring messages in chats",
                    checked = stealth.disappearingImmunity,
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(disappearingImmunity = it) }
                    }
                )
            }
        }

        item(key = "patch_section_storage_media") {
            ExpressiveSegmentedGroup(title = "Storage & Media Auto-Download") {
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.CleaningServices,
                    title = stringResource(R.string.settings_manage_storage_title),
                    subtitle = stringResource(R.string.settings_manage_storage_subtitle),
                    position = ExpressiveSegmentPosition.TOP,
                    onClick = { showStorageUsageDialog = true }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Media auto-download",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        "Automatically download incoming photos, audio, videos, and files"
                    } else {
                        "Off — tap media items in chats to download manually"
                    },
                    checked = stealth.autoDownloadEnabled,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = { viewModel.setAutoDownloadEnabled(it) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = "When using mobile data",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadMobileMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.MOBILE }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Wifi,
                    title = "When connected on Wi-Fi",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadWifiMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.WIFI }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Flight,
                    title = "When roaming",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadRoamingMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.ROAMING }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Collections,
                    title = "Media visibility",
                    subtitle = "Show newly downloaded media in your device's gallery",
                    checked = stealth.mediaVisibilityInGallery,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = { viewModel.setMediaVisibilityInGallery(it) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hide contact media from gallery",
                    subtitle = if (stealth.hiddenGalleryJids.isEmpty()) {
                        "Choose specific contacts or groups to keep out of gallery"
                    } else {
                        "${stealth.hiddenGalleryJids.size} contact(s)/group(s) hidden from gallery"
                    },
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onClick = { showGalleryContactsDialog = true }
                )
            }
        }

        item(key = "patch_section_security") {
            val lockSubtitle = when (appLockConfig.mode) {
                AppLockMode.OFF -> stringResource(R.string.settings_applock_row_subtitle_off)
                AppLockMode.DEVICE -> stringResource(R.string.settings_applock_row_subtitle_device)
                AppLockMode.CUSTOM_PIN -> stringResource(R.string.settings_applock_row_subtitle_pin)
            }
            ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_security)) {
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.settings_applock_row_title),
                    subtitle = lockSubtitle,
                    badgeText = if (appLockConfig.mode != AppLockMode.OFF) {
                        stringResource(R.string.common_enabled)
                    } else null,
                    position = if (appLockConfig.mode != AppLockMode.OFF) {
                        ExpressiveSegmentPosition.TOP
                    } else {
                        ExpressiveSegmentPosition.SINGLE
                    },
                    onClick = { showAppLockDialog = true }
                )
                if (appLockConfig.mode != AppLockMode.OFF) {
                    ExpressiveSegmentedActionRow(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.settings_lock_now_title),
                        subtitle = stringResource(R.string.settings_lock_now_subtitle),
                        position = ExpressiveSegmentPosition.BOTTOM,
                        onClick = { AppLockManager.lockNow() }
                    )
                }
            }
        }

        item(key = "patch_section_appearance") {
            ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_appearance)) {
                val colors = LocalWaColors.current
                val langSubtitle = when (stealth.appLanguage) {
                    LocaleHelper.LANG_TR -> stringResource(R.string.language_turkish)
                    LocaleHelper.LANG_EN -> stringResource(R.string.language_english)
                    else -> stringResource(R.string.language_system)
                }
                val reactionPreview = stealth.quickReactionEmojis
                    .ifEmpty { StealthMatrixManager.DEFAULT_QUICK_REACTIONS }
                    .joinToString("  ")

                ExpressiveSegmentedContainer(position = ExpressiveSegmentPosition.TOP) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExpressiveIconBadge(
                                icon = Icons.Rounded.Palette,
                                contentDescription = stringResource(R.string.settings_theme_title),
                                active = true
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_theme_title),
                                    color = colors.textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${stealth.themeMode.label} — ${stealth.themeMode.tagline}",
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        ThemeSelectorGrid(
                            selectedTheme = stealth.themeMode,
                            onSelectTheme = { viewModel.setThemeMode(it) }
                        )
                    }
                }
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Image,
                    title = stringResource(R.string.settings_wallpaper_title),
                    subtitle = "${describeChatWallpaper(stealth.chatWallpaperUri)} · ${stringResource(R.string.settings_wallpaper_subtitle)}",
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { showWallpaperDialog = true }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.TextFields,
                    title = stringResource(R.string.settings_system_font_title),
                    subtitle = stringResource(R.string.settings_system_font_subtitle),
                    checked = stealth.useSystemFont,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = { viewModel.setUseSystemFont(it) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.FormatSize,
                    title = stringResource(R.string.settings_font_size_title),
                    subtitle = formatFontScaleLabel(stealth.chatFontScale),
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { showFontSizeDialog = true }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.KeyboardReturn,
                    title = stringResource(R.string.settings_enter_sends_title),
                    subtitle = stringResource(R.string.settings_enter_sends_subtitle),
                    checked = stealth.enterIsSend,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(enterIsSend = it) }
                    }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.EmojiEmotions,
                    title = stringResource(R.string.settings_reactions_title),
                    subtitle = reactionPreview,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { showReactionDialog = true }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Translate,
                    title = stringResource(R.string.settings_language_title),
                    subtitle = langSubtitle,
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        item(key = "patch_section_looks") {
            ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_looks)) {
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(R.string.settings_look_vertical_status_title),
                    subtitle = stringResource(R.string.settings_look_vertical_status_subtitle),
                    checked = stealth.statusVerticalLayout,
                    position = ExpressiveSegmentPosition.TOP,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(statusVerticalLayout = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.settings_look_always_bio_title),
                    subtitle = stringResource(R.string.settings_look_always_bio_subtitle),
                    checked = stealth.alwaysShowContactBio,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(alwaysShowContactBio = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(R.string.settings_look_classic_ui_title),
                    subtitle = stringResource(R.string.settings_look_classic_ui_subtitle),
                    checked = stealth.classicWhatsAppLook,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(classicWhatsAppLook = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(R.string.settings_look_compact_rows_title),
                    subtitle = stringResource(R.string.settings_look_compact_rows_subtitle),
                    checked = stealth.compactChatRows,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(compactChatRows = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.settings_look_seconds_title),
                    subtitle = stringResource(R.string.settings_look_seconds_subtitle),
                    checked = stealth.showSecondsInTimestamps,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(showSecondsInTimestamps = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.settings_look_hide_camera_title),
                    subtitle = stringResource(R.string.settings_look_hide_camera_subtitle),
                    checked = stealth.hideHomeCameraShortcut,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideHomeCameraShortcut = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.settings_look_hide_badges_title),
                    subtitle = stringResource(R.string.settings_look_hide_badges_subtitle),
                    checked = stealth.hideStealthStatusBadges,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideStealthStatusBadges = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.settings_look_separate_groups_title),
                    subtitle = stringResource(R.string.settings_look_separate_groups_subtitle),
                    checked = stealth.separateGroupsAndChats,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(separateGroupsAndChats = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.settings_look_hide_channels_title),
                    subtitle = stringResource(R.string.settings_look_hide_channels_subtitle),
                    checked = stealth.hideChannels,
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideChannels = it) }
                    }
                )
            }
        }

        item(key = "patch_section_notifications_sync") {
            ExpressiveSegmentedGroup(title = "Notifications & Background Sync") {
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notify_dm_title),
                    subtitle = stringResource(R.string.settings_notify_dm_subtitle),
                    checked = stealth.notifyDmEnabled,
                    position = ExpressiveSegmentPosition.TOP,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(notifyDmEnabled = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Groups,
                    title = stringResource(R.string.settings_notify_group_title),
                    subtitle = stringResource(R.string.settings_notify_group_subtitle),
                    checked = stealth.notifyGroupEnabled,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(notifyGroupEnabled = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.settings_notify_preview_title),
                    subtitle = stringResource(R.string.settings_notify_preview_subtitle),
                    checked = stealth.notifyShowPreview,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(notifyShowPreview = it) }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.Vibration,
                    title = stringResource(R.string.settings_notify_vibrate_title),
                    subtitle = stringResource(R.string.settings_notify_vibrate_subtitle),
                    checked = stealth.notifyVibrateEnabled,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(notifyVibrateEnabled = it) }
                    }
                )
                ExpressiveSegmentedActionRow(
                    icon = if (isSyncNotifHidden) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                    title = if (isSyncNotifHidden) "Persistent Sync Banner" else "Hide Persistent Sync Banner",
                    subtitle = if (isSyncNotifHidden) {
                        "Hidden — 24/7 background sync & message alerts are still active"
                    } else {
                        "Remove the constant notification from your shade without breaking real-time sync or message alerts"
                    },
                    badgeText = if (isSyncNotifHidden) "HIDDEN" else "1-TAP GUIDE",
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = {
                        if (isSyncNotifHidden) {
                            WaRealtimeService.openNotificationChannelSettings(
                                context = context,
                                channelId = WaRealtimeService.CHANNEL_ID
                            )
                        } else {
                            showHideNotifGuide = true
                        }
                    }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Unrestricted Battery (24/7 Sync)",
                    subtitle = if (isBatteryUnrestricted) {
                        "Allowed — Android won't kill Wupp sync while your phone is locked"
                    } else {
                        "Allow unrestricted background use so real-time sync and alerts never stop"
                    },
                    badgeText = if (isBatteryUnrestricted) "ACTIVE" else "ALLOW",
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onClick = {
                        WaRealtimeService.openBatteryOptimizationSettings(context)
                    }
                )
            }
        }

        item(key = "patch_section_updates_support") {
            val updateSubtitle = when (val st = updateState) {
                is UpdateUiState.Checking -> "Checking GitHub Releases..."
                is UpdateUiState.UpToDate -> "Up to date (v${st.currentVersion})"
                is UpdateUiState.UpdateAvailable -> "New update available: ${st.release.tagName}"
                is UpdateUiState.Downloading -> {
                    if (st.progressPercent >= 0) "Downloading ${st.release.tagName} (${st.progressPercent}%)"
                    else "Downloading ${st.release.tagName}..."
                }
                is UpdateUiState.ReadyToInstall -> "${st.release.tagName} ready — tap to install"
                is UpdateUiState.Error -> if (!st.silent) st.message else "Installed v${BuildConfig.VERSION_NAME}"
                UpdateUiState.Idle -> "Installed v${BuildConfig.VERSION_NAME}"
            }
            val updateBadge = when (updateState) {
                is UpdateUiState.UpdateAvailable -> "NEW"
                is UpdateUiState.ReadyToInstall -> "INSTALL"
                else -> null
            }

            ExpressiveSegmentedGroup(title = "Updates & GitHub Support") {
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = updateSubtitle,
                    badgeText = updateBadge,
                    position = ExpressiveSegmentPosition.TOP,
                    onClick = {
                        when (updateState) {
                            is UpdateUiState.UpdateAvailable,
                            is UpdateUiState.Downloading,
                            is UpdateUiState.ReadyToInstall -> viewModel.openUpdatePrompt()
                            else -> viewModel.checkForUpdates(silent = false)
                        }
                    }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Auto-Check for Updates",
                    subtitle = "Notify and prompt to download when a new GitHub release ships",
                    checked = autoUpdateEnabled,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = { viewModel.setAutoUpdateEnabled(it) }
                )
                ExpressiveSegmentedSwitchRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Receive Pre-release Updates",
                    subtitle = "Tester channel — fetch GitHub pre-releases before public rollout",
                    checked = includePreReleases,
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onCheckedChange = { viewModel.setIncludePreReleases(it) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Report a Bug / Issue",
                    subtitle = "Open a pre-filled bug report on GitHub Issues",
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { onOpenFeedback(GitHubFeedbackType.BUG) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Lightbulb,
                    title = "Request a Feature",
                    subtitle = "Suggest improvements or new capabilities",
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { onOpenFeedback(GitHubFeedbackType.FEATURE) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "Give Feedback",
                    subtitle = "Share general thoughts directly to GitHub",
                    position = ExpressiveSegmentPosition.MIDDLE,
                    onClick = { onOpenFeedback(GitHubFeedbackType.FEEDBACK) }
                )
                ExpressiveSegmentedActionRow(
                    icon = Icons.Rounded.Code,
                    title = "GitHub Repository",
                    subtitle = "${BuildConfig.GITHUB_REPO} • v${BuildConfig.VERSION_NAME}",
                    position = ExpressiveSegmentPosition.BOTTOM,
                    onClick = {
                        val repo = BuildConfig.GITHUB_REPO.trim().trim('/')
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repo"))
                            )
                        }
                    }
                )
            }
        }

        item(key = "patch_bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingAutoDlNetwork?.let { net ->
        val mask = when (net) {
            AutoDownloadNetwork.MOBILE -> stealth.autoDownloadMobileMask
            AutoDownloadNetwork.WIFI -> stealth.autoDownloadWifiMask
            AutoDownloadNetwork.ROAMING -> stealth.autoDownloadRoamingMask
        }
        AutoDownloadNetworkDialog(
            network = net,
            initialMask = mask,
            onDismiss = { editingAutoDlNetwork = null },
            onConfirmMask = { updatedMask ->
                viewModel.setAutoDownloadMask(net, updatedMask)
            }
        )
    }

    if (showGalleryContactsDialog) {
        GalleryVisibilityContactsDialog(
            chats = allChats,
            hiddenJids = stealth.hiddenGalleryJids,
            onToggleHideContact = { jid, hide ->
                viewModel.setChatMediaVisibility(jid, if (hide) false else null)
            },
            onDismiss = { showGalleryContactsDialog = false }
        )
    }

    if (showProfileEditorDialog) {
        ProfileEditorDialog(
            initialProfile = ownProfile,
            onSaveName = { viewModel.saveOwnPushName(it) },
            onSaveAbout = { viewModel.saveOwnAbout(it) },
            onDismiss = {
                showProfileEditorDialog = false
                tabScope.launch { viewModel.fetchOwnProfile().onSuccess { ownProfile = it } }
            }
        )
    }

    if (showAccountPrivacyDialog) {
        WhatsAppAccountPrivacyDialog(
            initialTimerSec = stealth.defaultDisappearingSec,
            onLoadPrivacy = { viewModel.fetchAccountPrivacy() },
            onUpdateCategory = { cat, valStr -> viewModel.setAccountPrivacy(cat, valStr) },
            onUpdateDefaultTimer = { sec -> viewModel.setDefaultDisappearingTimer(sec) },
            onOpenBlockedContacts = { showBlockedContactsDialog = true },
            onDismiss = { showAccountPrivacyDialog = false }
        )
    }

    if (showBlockedContactsDialog) {
        BlockedContactsDialog(
            dmCandidates = allChats,
            onLoadBlocked = { viewModel.fetchBlockedContacts() },
            onSetBlocked = { jid, blk -> viewModel.setContactBlocked(jid, blk) },
            onDismiss = { showBlockedContactsDialog = false }
        )
    }

    if (showStorageUsageDialog) {
        StorageUsageDialog(
            onLoadStats = { viewModel.loadStorageStats() },
            onClearTempCache = { viewModel.clearTempCache() },
            onDismiss = { showStorageUsageDialog = false }
        )
    }

    if (showFontSizeDialog) {
        ChatFontSizeDialog(
            currentScale = stealth.chatFontScale,
            onSelectScale = { scale -> viewModel.updateGlobalStealth { c -> c.copy(chatFontScale = scale) } },
            onDismiss = { showFontSizeDialog = false }
        )
    }

    if (showCompanionScopeDialog) {
        CompanionScopeInfoDialog(onDismiss = { showCompanionScopeDialog = false })
    }

    if (showWallpaperDialog) {
        WallpaperPickerDialog(
            currentUri = stealth.chatWallpaperUri,
            currentDim = stealth.chatWallpaperDim,
            showDoodleOnPresets = stealth.chatWallpaperShowDoodle,
            currentScale = stealth.chatWallpaperScale,
            currentOffsetX = stealth.chatWallpaperOffsetX,
            currentOffsetY = stealth.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false }
        )
    }

    if (showReactionDialog) {
        ReactionEmojiCustomizerDialog(
            currentEmojis = stealth.quickReactionEmojis,
            onSave = { viewModel.setQuickReactionEmojis(it) },
            onReset = { viewModel.resetQuickReactionEmojis() },
            onDismiss = { showReactionDialog = false }
        )
    }

    if (showAppLockDialog) {
        AppLockConfigDialog(
            config = appLockConfig,
            onDismiss = { showAppLockDialog = false }
        )
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            currentLanguage = stealth.appLanguage,
            onSelectLanguage = { chosen ->
                val changed = chosen != stealth.appLanguage
                viewModel.setAppLanguage(chosen)
                if (changed) {
                    context.findFragmentActivity()?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showHideNotifGuide) {
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showHideNotifGuide = false },
            containerColor = colors.elevatedBar,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hide Constant Banner",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android requires a background service for 24/7 WhatsApp sync and doesn't let apps mute that status notification automatically.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.appSurface)
                            .border(0.8.dp, colors.accentGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "On the next screen (1 tap):",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Switch \"Show notifications\" OFF at the top of \"Wupp Realtime Sync\".",
                            color = colors.accentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        text = "• Real-time sync stays 100% active\n• Chat message alerts & sounds keep working",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideNotifGuide = false
                        WaRealtimeService.openNotificationChannelSettings(
                            context = context,
                            channelId = WaRealtimeService.CHANNEL_ID
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentGreen,
                        contentColor = Color(0xFF0B141A)
                    )
                ) {
                    Text("Open Setting", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideNotifGuide = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun NewChatPickerDialog(
    chats: List<ChatEntity>,
    contacts: List<ContactEntity>,
    onDismiss: () -> Unit,
    onSelectChat: (String) -> Unit,
    onOpenLinkedDevices: () -> Unit
) {
    val colors = LocalWaColors.current
    var query by remember { mutableStateOf("") }
    val mergedContacts = remember(chats, contacts) {
        val byJid = LinkedHashMap<String, ChatEntity>(contacts.size + chats.size)
        for (c in contacts) {
            if (c.jid.endsWith("@g.us") || c.jid.endsWith("@newsletter") || c.jid == "status@broadcast" || c.jid.endsWith("@broadcast")) continue
            byJid[c.jid] = ChatEntity(
                jid = c.jid,
                name = c.name,
                avatarPath = c.avatarPath,
                aboutText = c.aboutText,
                isFavorite = c.isSaved
            )
        }
        for (ch in chats) {
            if (ch.isGroup || ch.isCommunityParent || ch.jid.endsWith("@newsletter") || ch.jid == "status@broadcast" || ch.jid.endsWith("@broadcast")) continue
            val existing = byJid[ch.jid]
            if (existing == null) {
                byJid[ch.jid] = ch
            } else {
                val bare = ch.jid.substringBefore("@")
                val existingReal = existing.name.isNotBlank() && existing.name != bare && existing.name != ch.jid && !existing.name.startsWith("+")
                val chReal = ch.name.isNotBlank() && ch.name != bare && ch.name != ch.jid && !ch.name.startsWith("+")
                byJid[ch.jid] = existing.copy(
                    name = when {
                        existingReal && !chReal -> existing.name
                        chReal -> ch.name
                        else -> ch.name.ifBlank { existing.name }
                    },
                    avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: existing.avatarPath,
                    aboutText = ch.aboutText.ifBlank { existing.aboutText },
                    lastMessageTimestamp = maxOf(existing.lastMessageTimestamp, ch.lastMessageTimestamp)
                )
            }
        }
        byJid.values.sortedWith(
            compareByDescending<ChatEntity> { it.isFavorite }
                .thenByDescending { it.lastMessageTimestamp > 0L }
                .thenBy { it.name.lowercase() }
        )
    }
    val digitsQuery = remember(query) { query.filter { it.isDigit() } }
    val filtered = remember(mergedContacts, query) {
        val q = query.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        if (q.isEmpty()) mergedContacts.take(60)
        else mergedContacts.filter {
            it.name.lowercase().contains(q) ||
                it.jid.lowercase().contains(q) ||
                (qDigits.length >= 3 && it.jid.substringBefore("@").contains(qDigits))
        }.take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Select contact", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or number...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    if (digitsQuery.length >= 6 && filtered.none { it.jid.substringBefore("@") == digitsQuery }) {
                        val directJid = "$digitsQuery@s.whatsapp.net"
                        item(key = "direct_$directJid") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChat(directJid) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentGreen.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = colors.accentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Start chat with +$digitsQuery",
                                    color = colors.accentGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    items(filtered, key = { it.jid }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChat(c.jid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhatsAppAvatar(
                                jid = c.jid,
                                avatarPath = c.avatarPath,
                                isGroup = false,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name.ifBlank { c.jid.substringBefore("@") },
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (c.aboutText.isNotBlank()) {
                                    Text(
                                        text = c.aboutText,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenLinkedDevices) {
                Text("Linked devices", color = colors.accentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun LinkCompanionDialog(
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onDismiss: () -> Unit,
    onPairPhone: (String, (String) -> Unit) -> Unit,
    onReconnect: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    var phoneInput by remember { mutableStateOf("") }
    var generatedCode by remember(bridgeStatus.latestPairCode) { mutableStateOf(bridgeStatus.latestPairCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.wupp_logo),
                    contentDescription = "Wupp",
                    modifier = Modifier.size(28.dp)
                )
                Text("Link Companion Device", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Engine: ${bridgeStatus.connectionState}" +
                        if (bridgeStatus.ownJid.isNotBlank()) " (${bridgeStatus.ownJid})" else "",
                    color = colors.accentGreen,
                    fontFamily = mono,
                    fontSize = 12.sp
                )
                Text(
                    text = "Enter your full phone number with country code (digits only, e.g. 15551234567) to request an 8-character Wupp companion link code:",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!generatedCode.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.pillActiveBg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pair Code: $generatedCode",
                            fontFamily = mono,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.pillActiveText
                        )
                    }
                }
                if (!bridgeStatus.latestQrCode.isNullOrBlank()) {
                    Text(
                        text = "QR Token ready (${bridgeStatus.latestQrCode.take(28)}...)",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPairPhone(phoneInput) { generatedCode = it } },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Get Pair Code", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReconnect) { Text("Reconnect") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun VaultMediaPreviewDialog(
    title: String,
    subtitle: String,
    localPath: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val colors = LocalWaColors.current
    val bmp = remember(localPath) {
        localPath?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Save to Gallery", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatFullDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(epochMs))
}
